package plugfest;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import com.gurobi.gurobi.*;

public class OptimizationModel_Plugfest {

    // Hilfsklasse zur Speicherung der Iterationsergebnisse
	public static class IterationResult {
	    public int iteration;  // Kennzahl (z. B. Startperiode dieser Iteration)
	    public Map<Period, Map<Agent, Double>> xValues = new HashMap<>();
	    public Map<Period, Map<Agent, String>> yStates = new HashMap<>();
	    // Aggregierte Produktion (über alle Agenten) pro Periode (optional)
	    public Map<Period, Double> aggregatedCalculated = new HashMap<>();
	    public Map<Period, Double> aggregatedSimulated = new HashMap<>();
	    // Agentenspezifische Produktionswerte pro Periode:
	    public Map<Period, Map<Agent, Double>> agentCalculated = new HashMap<>();
	    public Map<Period, Map<Agent, Double>> agentSimulated = new HashMap<>();
	}


    public static void main(String[] args) {
        try {
            // --- Input-Daten laden ---
            String excelFilePath = System.getenv("EXCEL_FILE_PATH");
            if (excelFilePath == null || excelFilePath.isEmpty()) {
                excelFilePath = "in/InputData_Plugfest.xlsx";
            }
            FileInputStream excelFile = null;
            Workbook workbook = null;
            try {
                excelFile = new FileInputStream(excelFilePath);
                System.out.println(excelFilePath + " found and loaded.");
                workbook = new XSSFWorkbook(excelFile);
                System.out.println("Workbook erfolgreich erstellt.");
            } catch (FileNotFoundException e) {
                System.out.println(excelFilePath + " not found.");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Fehler beim Laden des Workbooks.");
                e.printStackTrace();
            } finally {
                if (excelFile != null) {
                    try {
                        excelFile.close();
                    } catch (Exception e) {
                        System.out.println("Fehler beim Schließen der Excel-Datei.");
                        e.printStackTrace();
                    }
                }
            }

            // --- Plugfest-spezifische Parameter ---
            // Hier speichern wir die aggregierten, kalkulierten Produktionswerte pro Periode (aus der Optimierung)
            HashMap<Period, Double> calculatedHydrogenProductionPerPeriod = new HashMap<>();
            // Hier speichern wir die simulierten "realen" Produktionswerte (fiktiv, ±20% Variation)
            HashMap<Period, Double> simulatedRealProductionMap = new HashMap<>();

            // --- Define Periods ---
            Parameters params = loadParameters(workbook);
            Set<Agent> agents = params.startupCost.keySet();
            Set<Period> periods = params.periods;
            workbook.close();

            // Sortiere Perioden nach t (aufsteigend)
            List<Period> sortedPeriods = new ArrayList<>(periods);
            Collections.sort(sortedPeriods, Comparator.comparingInt(Period::getT));

            // Diese Maps dienen dazu, in späteren Iterationen die bereits optimierten Werte zu fixieren.
            Map<Agent, Map<Period, Double>> fixedXValues = new HashMap<>();
            Map<Agent, Map<Period, Map<State, Double>>> fixedYValues = new HashMap<>();
            Map<Period, Map<Agent, Double>> globalAgentSimulatedMap = new HashMap<>();

            // Liste, in der wir pro Iteration die gesammelten Ergebnisse speichern.
            List<IterationResult> iterationResults = new ArrayList<>();

            // Rollierende Optimierung: Für jeden Durchlauf wird der Horizont ab der aktuellen Startperiode bis zum Ende optimiert.
            for (int i = 0; i < sortedPeriods.size(); i++) {
                Period currentStart = sortedPeriods.get(i);
                System.out.println("Starte rollierende Optimierung ab Periode: " + currentStart.getT());

                // Neues Gurobi-Environment und Modell für diese Iteration
                GRBEnv env = new GRBEnv(true);
                env.start();
                GRBModel model = new GRBModel(env);
                configureGurobi(model);

                // Erstelle Entscheidungsvariablen für alle Perioden
                Map<Agent, Map<Period, GRBVar>> xVar = new HashMap<>();
                Map<Agent, Map<Period, Map<State, GRBVar>>> yVar = new HashMap<>();
                defineDecisionVariables(model, agents, periods, xVar, yVar);

                // Setze Zielfunktion und alle Constraints außer der Nachfrage-Constraint
                GRBLinExpr objective = new GRBLinExpr();
                defineObjectiveFunction(model, params, agents, periods, xVar, yVar, objective);
                model.setObjective(objective, GRB.MINIMIZE);
                defineConstraintsWithoutDemand(model, params, agents, periods, xVar, yVar);

                // Berechne die bisher simulierte Produktion vergangener Perioden (aus früheren Iterationen)
                double measuredProductionSoFar = 0.0;
                for (Period t : sortedPeriods) {
                    if (t.getT() < currentStart.getT() && simulatedRealProductionMap.containsKey(t)) {
                        measuredProductionSoFar += simulatedRealProductionMap.get(t);
                    }
                }
                // Berechne die verbleibende globale Nachfrage (params.demand ist global)
                double remainingDemand = params.demand - measuredProductionSoFar;
                System.out.println("Verbleibende Nachfrage ab Periode " + currentStart.getT() + ": " + remainingDemand);

                // Füge die angepasste Nachfrage-Constraint für Perioden ab currentStart hinzu
                GRBLinExpr demandConstraintExpr = new GRBLinExpr();
                for (Period t : sortedPeriods) {
                    if (t.getT() >= currentStart.getT()) {
                        for (Agent a : agents) {
                            double alpha = params.slope.get(a);
                            double powerElectrolyzer = params.powerElectrolyzer.get(a);
                            demandConstraintExpr.addTerm(alpha * powerElectrolyzer * params.intervalLengthSWO,
                                    xVar.get(a).get(t));
                        }
                    }
                }
                model.addConstr(demandConstraintExpr, GRB.GREATER_EQUAL, remainingDemand, "demandConstraint");

                // Fixiere in dieser Iteration alle Entscheidungsvariablen für vergangene Perioden
                for (Period t : sortedPeriods) {
                    if (t.getT() < currentStart.getT()) {
                        for (Agent a : agents) {
                            if (fixedXValues.containsKey(a) && fixedXValues.get(a).containsKey(t)) {
                                double fixedX = fixedXValues.get(a).get(t);
                                GRBVar varX = xVar.get(a).get(t);
                                varX.set(GRB.DoubleAttr.LB, fixedX);
                                varX.set(GRB.DoubleAttr.UB, fixedX);
                            }
                            for (State s : State.values()) {
                                if (fixedYValues.containsKey(a) && fixedYValues.get(a).containsKey(t)) {
                                    double fixedY = fixedYValues.get(a).get(t).get(s);
                                    GRBVar varY = yVar.get(a).get(t).get(s);
                                    varY.set(GRB.DoubleAttr.LB, fixedY);
                                    varY.set(GRB.DoubleAttr.UB, fixedY);
                                }
                            }
                        }
                    }
                }

                // Optimiere das Modell
                model.optimize();

                // Erstelle ein neues IterationResult-Objekt
                IterationResult iterResult = new IterationResult();
                iterResult.iteration = currentStart.getT(); // Verwende den Startwert als Iterationskennzahl

                if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
                    // Speichere Ergebnisse für alle Perioden ab currentStart zur Fixierung in späteren Iterationen
                    for (Period t : sortedPeriods) {
                        if (t.getT() >= currentStart.getT()) {
                            for (Agent a : agents) {
                                double xVal = xVar.get(a).get(t).get(GRB.DoubleAttr.X);
                                fixedXValues.computeIfAbsent(a, k -> new HashMap<>()).put(t, xVal);
                                for (State s : State.values()) {
                                    double yVal = yVar.get(a).get(t).get(s).get(GRB.DoubleAttr.X);
                                    fixedYValues.computeIfAbsent(a, k -> new HashMap<>())
                                            .computeIfAbsent(t, k -> new HashMap<>())
                                            .put(s, yVal);
                                }
                            }
                        }
                    }
                    // Für jede Periode erfassen wir:
                    for (Period t : sortedPeriods) {
                        // Erfasse x- und y-Werte pro Agent:
                        Map<Agent, Double> xPerAgent = new HashMap<>();
                        Map<Agent, String> yPerAgent = new HashMap<>();
                        // Für agentenspezifische kalkulierte Produktion:
                        Map<Agent, Double> agentCalcMap = new HashMap<>();
                        
                        for (Agent a : agents) {
                            double xVal = fixedXValues.containsKey(a) && fixedXValues.get(a).containsKey(t) ?
                                          fixedXValues.get(a).get(t) : xVar.get(a).get(t).get(GRB.DoubleAttr.X);
                            xPerAgent.put(a, xVal);
                            String activeState = "None";
                            for (State s : State.values()) {
                                double yVal = fixedYValues.containsKey(a) && fixedYValues.get(a).containsKey(t) ?
                                              fixedYValues.get(a).get(t).get(s) : yVar.get(a).get(t).get(s).get(GRB.DoubleAttr.X);
                                if (yVal == 1.0) {
                                    activeState = s.name();
                                    break;
                                }
                            }
                            yPerAgent.put(a, activeState);
                            
                            // Kalkulierte Produktion für diesen Agenten:
                            double prodState = yVar.get(a).get(t).get(State.PRODUCTION).get(GRB.DoubleAttr.X);
                            double agentCalc = params.intervalLengthSWO *
                                               (params.powerElectrolyzer.get(a) * params.slope.get(a) * xVal +
                                                params.intercept.get(a) * prodState);
                            agentCalcMap.put(a, agentCalc);
                        }
                        // Speichere die agentenspezifischen Werte:
                        iterResult.xValues.put(t, xPerAgent);
                        iterResult.yStates.put(t, yPerAgent);
                        iterResult.agentCalculated.put(t, new HashMap<>(agentCalcMap)); // Hier speichern wir die kalkulierten Werte
                        
                        // Aggregierte kalkulierte Produktion (über alle Agenten)
                        double aggCalc = 0.0;
                        for (double calc : agentCalcMap.values()) {
                            aggCalc += calc;
                        }
                        iterResult.aggregatedCalculated.put(t, aggCalc);
                        
                        // Nun agentenspezifische und aggregierte simulierte Produktion:
                        if (t.getT() < currentStart.getT()) {
                            // Bereits fixierte, historische Werte verwenden:
                            Double aggSim = simulatedRealProductionMap.get(t);
                            if (aggSim == null) { 
                                aggSim = 0.0; 
                            }
                            iterResult.aggregatedSimulated.put(t, aggSim);
                            
                            // Agentenspezifisch:
                            Map<Agent, Double> agentSimFixed = globalAgentSimulatedMap.get(t);
                            if (agentSimFixed == null) {
                                agentSimFixed = new HashMap<>();
                                for (Agent a : agents) {
                                    agentSimFixed.put(a, 0.0);
                                }
                            }
                            iterResult.agentSimulated.put(t, agentSimFixed);
                        } else if (t.getT() == currentStart.getT()) {
                            // Für die aktuelle Startperiode: neuen Zufallswert erzeugen
                            double aggSim = aggCalc * (0.8 + Math.random() * 0.4);
                            simulatedRealProductionMap.put(t, aggSim);
                            iterResult.aggregatedSimulated.put(t, aggSim);
                            
                            Map<Agent, Double> agentSimNew = new HashMap<>();
                            for (Agent a : agents) {
                                double agentCalc = agentCalcMap.get(a);
                                double agentSim = agentCalc * (0.8 + Math.random() * 0.4);
                                agentSimNew.put(a, agentSim);
                            }
                            iterResult.agentSimulated.put(t, agentSimNew);
                            // Speichere den agentenspezifischen Wert global, damit spätere Iterationen ihn übernehmen:
                            globalAgentSimulatedMap.put(t, agentSimNew);
                        } else { // t > currentStart
                            // Für zukünftige Perioden noch keinen Wert – vorerst 0.0
                            iterResult.aggregatedSimulated.put(t, 0.0);
                            Map<Agent, Double> emptyMap = new HashMap<>();
                            for (Agent a : agents) {
                                emptyMap.put(a, 0.0);
                            }
                            iterResult.agentSimulated.put(t, emptyMap);
                        }
                    }
                    calculatedHydrogenProductionPerPeriod.put(currentStart, iterResult.aggregatedCalculated.get(currentStart));
                    System.out.println("Iteration ab Periode " + currentStart.getT() + " erfolgreich.");

                } else {
                    System.out.println("Keine optimale Lösung gefunden ab Periode " + currentStart.getT());
                    model.write("optimizationmodel_" + currentStart.getT() + ".lp");
                }
                iterationResults.add(iterResult);
                model.dispose();
                env.dispose();
            } // Ende der rollierenden Schleife

            // --- Export der Iterationsergebnisse in ein Excel-Sheet ---
            String desktopPath = System.getenv("DESKTOP_PATH");
            if (desktopPath == null || desktopPath.isEmpty()) {
                desktopPath = Paths.get(System.getProperty("user.home"), "Desktop").toString();
            }
            String excelFilePathFinalResults = desktopPath + "/" + "IterationsResults.xlsx";
            exportIterationOverviewToExcelAlternate(iterationResults, sortedPeriods, agents, excelFilePathFinalResults);
            System.out.println("Export der Iterationsergebnisse erfolgreich: " + excelFilePathFinalResults);

        } catch (GRBException | IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void exportIterationOverviewToExcelAlternate(
            List<IterationResult> iterationResults,
            List<Period> sortedPeriods,
            Set<Agent> agents,
            String excelFilePath) throws IOException {

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Iteration Overview");

        // Erzeuge Header-Zeile
        Row headerRow = sheet.createRow(0);
        int col = 0;
        headerRow.createCell(col++).setCellValue("Iteration");
        headerRow.createCell(col++).setCellValue("Periode");

        // Sortiere Agenten für eine konsistente Reihenfolge
        List<Agent> sortedAgents = new ArrayList<>(agents);
        sortedAgents.sort(Comparator.comparingInt(a -> a.a));
        for (Agent a : sortedAgents) {
            headerRow.createCell(col++).setCellValue("Agent " + a.a + " xValue");
            headerRow.createCell(col++).setCellValue("Agent " + a.a + " yValue");
            headerRow.createCell(col++).setCellValue("Agent " + a.a + " Calculated Prod");
            headerRow.createCell(col++).setCellValue("Agent " + a.a + " Simulated Prod");
        }

        // Fülle die Datenzeilen: Jede Zeile entspricht einer Kombination aus Iteration und Periode
        int rowIndex = 1;
        for (IterationResult ir : iterationResults) {
            for (Period p : sortedPeriods) {
                Row row = sheet.createRow(rowIndex++);
                int c = 0;
                row.createCell(c++).setCellValue(ir.iteration);
                row.createCell(c++).setCellValue(p.getT());
                // Für jeden Agenten: hole xValue, yState, agentCalculated und agentSimulated
                for (Agent a : sortedAgents) {
                    double xVal = 0.0;
                    String yVal = "N/A";
                    if (ir.xValues.containsKey(p) && ir.xValues.get(p).containsKey(a)) {
                        xVal = ir.xValues.get(p).get(a);
                    }
                    if (ir.yStates.containsKey(p) && ir.yStates.get(p).containsKey(a)) {
                        yVal = ir.yStates.get(p).get(a);
                    }
                    double calcProd = 0.0;
                    double simProd = 0.0;
                    if (ir.agentCalculated.containsKey(p) && ir.agentCalculated.get(p).containsKey(a)) {
                        calcProd = ir.agentCalculated.get(p).get(a);
                    }
                    if (ir.agentSimulated.containsKey(p) && ir.agentSimulated.get(p).containsKey(a)) {
                        simProd = ir.agentSimulated.get(p).get(a);
                    }
                    row.createCell(c++).setCellValue(xVal);
                    row.createCell(c++).setCellValue(yVal);
                    row.createCell(c++).setCellValue(calcProd);
                    row.createCell(c++).setCellValue(simProd);
                }
            }
        }

        // Optional: AutoSize der Spalten
        for (int i = 0; i < col; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream fos = new FileOutputStream(excelFilePath)) {
            workbook.write(fos);
        } finally {
            workbook.close();
        }
        System.out.println("Iteration Overview exportiert: " + excelFilePath);
    }


    /**
     * Define decision variables.
     */
    private static void defineDecisionVariables(GRBModel model, Set<Agent> agents, Set<Period> periods,
                                                  Map<Agent, Map<Period, GRBVar>> xVar,
                                                  Map<Agent, Map<Period, Map<State, GRBVar>>> yVar)
            throws GRBException {
        for (Agent a : agents) {
            xVar.put(a, new HashMap<>());
            yVar.put(a, new HashMap<>());
            for (Period t : periods) {
                xVar.get(a).put(t, model.addVar(0, 1, 0, GRB.CONTINUOUS, "x_" + a.a + "_" + t.t));
                yVar.get(a).put(t, new HashMap<>());
                for (State s : State.values()) {
                    yVar.get(a).get(t).put(s, model.addVar(0, 1, 0, GRB.BINARY, "y_" + a.a + "_" + t.t + "_" + s));
                }
            }
        }
    }

    /**
     * Define the objective function.
     */
    private static void defineObjectiveFunction(GRBModel model, Parameters params, Set<Agent> agents,
                                                  Set<Period> periods,
                                                  Map<Agent, Map<Period, GRBVar>> x,
                                                  Map<Agent, Map<Period, Map<State, GRBVar>>> y,
                                                  GRBLinExpr objective) throws GRBException {

        // Produktionskosten
        GRBLinExpr productionCostExpr = new GRBLinExpr();
        for (Agent a : agents) {
            for (Period t : periods) {
                Double Pel = params.powerElectrolyzer.get(a);
                Double CtE = params.electricityCost.get(t);
                if (Pel != null && CtE != null) {
                    productionCostExpr.addTerm(Pel * CtE * params.intervalLengthSWO, x.get(a).get(t));
                }
            }
        }
        objective.add(productionCostExpr);

        // Startup- und Standby-Kosten
        GRBLinExpr startupCostExpr = new GRBLinExpr();
        for (Agent a : agents) {
            double standbyCost = params.standbyCost.get(a);
            for (Period t : periods) {
                startupCostExpr.addTerm(params.intervalLengthSWO * params.startupCost.get(a),
                        y.get(a).get(t).get(State.STARTING));
                startupCostExpr.addTerm(params.intervalLengthSWO * standbyCost, y.get(a).get(t).get(State.STANDBY));
            }
        }
        objective.add(startupCostExpr);
    }

    /**
     * Define constraints without the demand constraint.
     */
    private static void defineConstraintsWithoutDemand(GRBModel model, Parameters params, Set<Agent> agents,
                                                         Set<Period> periods,
                                                         Map<Agent, Map<Period, GRBVar>> x,
                                                         Map<Agent, Map<Period, Map<State, GRBVar>>> y)
            throws GRBException {
        // 1. Initialzustand
        for (Agent a : agents) {
            Period firstPeriod = new Period(1);
            model.addConstr(y.get(a).get(firstPeriod).get(State.IDLE), GRB.EQUAL, 1.0,
                    "initialState_" + a.a + "_" + firstPeriod.t);
        }

        // 2. Betriebsgrenzen
        for (Agent a : agents) {
            for (Period t : periods) {
                GRBLinExpr minOperationExpr = new GRBLinExpr();
                minOperationExpr.addTerm(params.minOperation.get(a), y.get(a).get(t).get(State.PRODUCTION));
                model.addConstr(x.get(a).get(t), GRB.GREATER_EQUAL, minOperationExpr,
                        "minOperation_" + a.a + "_" + t.t);

                GRBLinExpr maxOperationExpr = new GRBLinExpr();
                maxOperationExpr.addTerm(params.maxOperation.get(a), y.get(a).get(t).get(State.PRODUCTION));
                model.addConstr(x.get(a).get(t), GRB.LESS_EQUAL, maxOperationExpr, "maxOperation_" + a.a + "_" + t.t);
            }
        }

        // 3. Zustandsexklusivität
        for (Agent a : agents) {
            for (Period t : periods) {
                GRBLinExpr statusSum = new GRBLinExpr();
                for (State s : State.values()) {
                    statusSum.addTerm(1.0, y.get(a).get(t).get(s));
                }
                model.addConstr(statusSum, GRB.EQUAL, 1.0, "stateExclusivity_" + a.a + "_" + t.t);
            }
        }

        // 4. Zustandsübergänge
        for (Agent a : agents) {
            for (Period t : periods) {
                if (t.t > 1) {
                    Period prevPeriod = new Period(t.t - 1);
                    int startingHoldingDuration = params.holdingDurations.get(a).get(State.STARTING);
                    Period startupPeriod = new Period(t.getT() - startingHoldingDuration);

                    GRBLinExpr startingExpr = new GRBLinExpr();
                    startingExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.IDLE));
                    startingExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.STARTING));
                    model.addConstr(y.get(a).get(t).get(State.STARTING), GRB.LESS_EQUAL, startingExpr,
                            "stateTransitionStarting_" + a.a + "_" + t.t);

                    GRBLinExpr productionExpr = new GRBLinExpr();
                    productionExpr.addTerm(1.0, y.get(a).get(startupPeriod).get(State.STARTING));
                    productionExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.PRODUCTION));
                    productionExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.STANDBY));
                    model.addConstr(y.get(a).get(t).get(State.PRODUCTION), GRB.LESS_EQUAL, productionExpr,
                            "stateTransitionProduction_" + a.a + "_" + t.t);
                }
            }
        }

        // 5. Mindesthaltedauer
        for (Agent a : agents) {
            for (State s : State.values()) {
                Integer minDwellTime = params.holdingDurations.get(a).get(s);
                if (minDwellTime != null) {
                    for (Period t : periods) {
                        if (t.t >= minDwellTime && t.t >= 2) {
                            for (int tau = 1; tau <= minDwellTime - 1; tau++) {
                                if (t.t - tau >= 1) {
                                    Period prevPeriod = new Period(t.t - 1);
                                    Period futurePeriod = new Period(t.t - tau);
                                    GRBLinExpr holdingExpr = new GRBLinExpr();
                                    holdingExpr.addTerm(1.0, y.get(a).get(t).get(s));
                                    holdingExpr.addTerm(-1.0, y.get(a).get(prevPeriod).get(s));
                                    model.addConstr(holdingExpr, GRB.LESS_EQUAL, y.get(a).get(futurePeriod).get(s),
                                            "minHoldingDuration_" + a.a + "_" + t.t + "_" + s);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

	/**
	 * Configure the Gurobi solver.
	 */
	private static void configureGurobi(GRBModel model) throws GRBException {
		model.set(GRB.DoubleParam.MIPGap, 0.0001);
		model.set(GRB.DoubleParam.TimeLimit, 600); 
	}

	/**
	 * Load parameters from the Excel file.
	 */
	private static Parameters loadParameters(Workbook workbook) {
	    Sheet agentsSheet = workbook.getSheet("Electrolyzer");
	    Sheet periodsSheet = workbook.getSheet("Periods");
	    Sheet parametersSheet = workbook.getSheet("GlobalParameters");

	    // Laden der Agenten-Parameter
	    Map<Agent, Double> powerElectrolyzer = new HashMap<>();
	    Map<Agent, Double> minOperation = new HashMap<>();
	    Map<Agent, Double> maxOperation = new HashMap<>();
	    Map<Agent, Double> slope = new HashMap<>();
	    Map<Agent, Double> intercept = new HashMap<>();
	    Map<Agent, Integer> startupDuration = new HashMap<>();
	    Map<Agent, Double> startupCost = new HashMap<>();
	    Map<Agent, Double> standbyCost = new HashMap<>();
	    Set<Agent> agents = new HashSet<>();

	    // Mindestverweildauer für Zustände (Idle, Starting, Production, Standby)
	    Map<Agent, Map<State, Integer>> holdingDurations = new HashMap<>();

	    // Lesen der Agenten-Daten aus der Agenten-Tabelle
	    for (Row row : agentsSheet) {
	        if (row.getRowNum() == 0) continue;

	        Agent agent = new Agent((int) row.getCell(0).getNumericCellValue()); // Column A
	        powerElectrolyzer.put(agent, row.getCell(1).getNumericCellValue()); // Column B
	        minOperation.put(agent, row.getCell(2).getNumericCellValue()); // Column C
	        maxOperation.put(agent, row.getCell(3).getNumericCellValue()); // Column D
	        startupCost.put(agent, row.getCell(4).getNumericCellValue()); // Column E
	        standbyCost.put(agent, row.getCell(5).getNumericCellValue()); // Column F
	        slope.put(agent, row.getCell(6).getNumericCellValue()); // Column G
	        intercept.put(agent, row.getCell(7).getNumericCellValue()); // Column H

	        // Laden der Haltedauern (Idle, Starting, Production, Standby)
	        Map<State, Integer> agentHoldingDurations = new HashMap<>();
	        agentHoldingDurations.put(State.IDLE, (int) row.getCell(8).getNumericCellValue()); // Column I
	        agentHoldingDurations.put(State.STARTING, (int) row.getCell(9).getNumericCellValue()); // Column J
	        agentHoldingDurations.put(State.PRODUCTION, (int) row.getCell(10).getNumericCellValue()); // Column K
	        agentHoldingDurations.put(State.STANDBY, (int) row.getCell(11).getNumericCellValue()); // Column L

	        // Haltedauern in die Map einfügen
	        holdingDurations.put(agent, agentHoldingDurations);
	        agents.add(agent);
	    }

	    // Laden der periodenspezifischen Parameter, einschließlich Demand und erneuerbare Energien
	    Map<Period, Double> electricityPrice = new HashMap<>();
	    Map<Period, Double> availablePower = new HashMap<>();
	    Map<Period, Double> renewableEnergy = new HashMap<>(); // Neu: Erneuerbare Energien
	    Set<Period> periods = new HashSet<>();

	    // Lesen der Perioden-Daten aus der Perioden-Tabelle
	    for (Row row : periodsSheet) {
	        if (row.getRowNum() == 0) continue; // Kopfzeile überspringen

	        Period period = new Period((int) row.getCell(0).getNumericCellValue()); // Column A
	        electricityPrice.put(period, row.getCell(1).getNumericCellValue()); // Column B
	        availablePower.put(period, row.getCell(2).getNumericCellValue()); // Column C
	        renewableEnergy.put(period, row.getCell(2).getNumericCellValue()); // Column E (erneuerbare Energie)
	        periods.add(period);
	    }

	    // Global Parameter
	    double intervalLength = parametersSheet.getRow(1).getCell(1).getNumericCellValue();
	    double demandDeviationCost = parametersSheet.getRow(2).getCell(1).getNumericCellValue();
	    double demand = parametersSheet.getRow(3).getCell(1).getNumericCellValue();

	    // Rückgabe des Parameters-Objekts mit erneuerbarer Energie
	    return new Parameters(
	            startupCost,
	            standbyCost,
	            powerElectrolyzer,
	            electricityPrice,
	            minOperation,
	            maxOperation,
	            demand,
	            slope,
	            intercept,
	            availablePower,
	            intervalLength,
	            startupDuration,
	            demandDeviationCost,
	            holdingDurations,
	            periods,
	            renewableEnergy 
	    );
	}

}
