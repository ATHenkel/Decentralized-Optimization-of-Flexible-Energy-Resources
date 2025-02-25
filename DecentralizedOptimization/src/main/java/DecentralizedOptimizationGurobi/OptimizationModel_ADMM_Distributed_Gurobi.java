package DecentralizedOptimizationGurobi;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBEnv;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBModel;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class OptimizationModel_ADMM_Distributed_Gurobi {
	/**
	 * Global Variables
	 */
	final static String ANSI_RESET = "\u001B[0m";
	final static String ANSI_GREEN = "\u001B[32m";

	// ADMM penalty parameter
	private static double rho = 1;
	
	// Methode zur Periodenfilterung für Agenten
	public static Set<Period> filterPeriodsForAgent(Agent agent, Set<Period> allPeriods) {
	    Set<Period> filteredPeriods = new HashSet<>();

	    // Filter Periods for Agent
	    if (agent.getId() == 1) {
	        // Agent 1 bekommt ungerade Perioden (1, 3, 5, ..., 19)
	        for (Period period : allPeriods) {
	            if (period.getT() % 2 != 0) { // Ungerade Perioden
	                filteredPeriods.add(period);
	            }
	        }
	    } else if (agent.getId() == 2) {
	        // Agent 2 bekommt gerade Perioden (2, 4, 6, ..., 20)
	        for (Period period : allPeriods) {
	            if (period.getT() % 2 == 0) { // Gerade Perioden
	                filteredPeriods.add(period);
	            }
	        }
	    }

	    return filteredPeriods;
	}

	public static void main(String[] args) {
		
		FileInputStream fileIn = null;
		Workbook workbook = null;
		Workbook resultWorkbook = null;

		int maxADMMIterations = 31;
		double improvementThreshold = 0.001; 
		double previousObjectiveValue = Double.MAX_VALUE;
		boolean globalFeasible = false;
		
		try {
			fileIn = new FileInputStream("in/InputData.xlsx");
			workbook = new XSSFWorkbook(fileIn);
			Parameters params = loadParameters(workbook);
			Set<Agent> agents = params.startupCost.keySet();
			resultWorkbook = new XSSFWorkbook();

			Set<Period> currentPeriods = new HashSet<>();
			for (int i = 1; i <= 10; i++) {
				currentPeriods.add(new Period(i));
			}

			// Data Exchange
			DataExchange dataExchange = new DataExchange();

			// Initialisierung aller Iterationen
			for (int iteration = 0; iteration < maxADMMIterations; iteration++) {
				dataExchange.initializeIteration(iteration, currentPeriods.size(), agents.size());
			}

			// Alle Y-Werte auf "Production" für die erste Iteration
			dataExchange.setYToIdleForFirstIteration();

			System.out.println("Start Optimization");
			
			 // ADMM Iterations
            for (int admmIter = 0; admmIter < maxADMMIterations-1; admmIter++) {

            	// X-Update
            	for (Agent agent : agents) {
            	    GRBEnv env = null;
            	    GRBModel model = null;
            	    try {
            	        env = new GRBEnv();
            	        env.start();
            	        model = new GRBModel(env);
            	        configureGurobi(model);
            	        Set<Period> filteredPeriods = filterPeriodsForAgent(agent, currentPeriods);
            	        agent.optimizeX(model, params, filteredPeriods, agents, admmIter, dataExchange, rho);
            	    } catch (GRBException e) {
            	        e.printStackTrace();
            	    } finally {
            	        // Modell und Umgebung freigeben
            	        if (model != null) {
            	            model.dispose();
            	        }
            	        if (env != null) {
            	            try {
            	                env.dispose();
            	            } catch (GRBException e) {
            	                e.printStackTrace();
            	            }
            	        }
            	    }
            	}

            	// Y-Update
            	for (Agent agent : agents) {
            	    GRBEnv env = null;
            	    GRBModel model = null;
            	    try {
            	        env = new GRBEnv();
            	        env.start();
            	        model = new GRBModel(env);
            	        configureGurobi(model);
            	        agent.optimizeY(model, params, currentPeriods, admmIter, dataExchange, rho);
            	    } catch (GRBException e) {
            	        e.printStackTrace();
            	    } finally {
            	        if (model != null) {
            	            model.dispose();
            	        }
            	        if (env != null) {
            	            try {
            	                env.dispose();
            	            } catch (GRBException e) {
            	                e.printStackTrace();
            	            }
            	        }
            	    }
            	}

            	// S-Update
            	for (Agent agent : agents) {
            	    GRBEnv env = null;
            	    GRBModel model = null;
            	    try {
            	        env = new GRBEnv();
            	        env.start();
            	        model = new GRBModel(env);
            	        configureGurobi(model);
            	        agent.optimizeS(model, params, currentPeriods, admmIter, dataExchange, rho);
            	    } catch (GRBException e) {
            	        e.printStackTrace();
            	    } finally {
            	        if (model != null) {
            	            model.dispose();
            	        }
            	        if (env != null) {
            	            try {
            	                env.dispose();
            	            } catch (GRBException e) {
            	                e.printStackTrace();
            	            }
            	        }
            	    }
            	}

            	// Dual-Update
            	for (Agent agent : agents) {
            	    GRBEnv env = null;
            	    GRBModel model = null;
            	    try {
            	        env = new GRBEnv();
            	        env.start();
            	        model = new GRBModel(env);
            	        configureGurobi(model);
            	        agent.updateU(model, params, currentPeriods, admmIter, dataExchange, rho);
            	    } catch (GRBException e) {
            	        e.printStackTrace();
            	    } finally {
            	        if (model != null) {
            	            model.dispose();
            	        }
            	        if (env != null) {
            	            try {
            	                env.dispose();
            	            } catch (GRBException e) {
            	                e.printStackTrace();
            	            }
            	        }
            	    }
            	}


                // Überprüfung der Zulässigkeit und Berechnung des Zielfunktionswertes
                double currentObjectiveValue = checkFeasibilityAndCalculateObjective(agents, currentPeriods, params,
                        dataExchange, admmIter, globalFeasible);

                // Überprüfung der Verbesserung der Zielfunktion
                if (admmIter > 0) {
                	double improvement = Math.abs((previousObjectiveValue - currentObjectiveValue) / previousObjectiveValue);
                    System.out.println("Verbesserung von Iteration " + admmIter + ": " + (improvement * 100) + "%" + " previousObjective: " + previousObjectiveValue + " currentObjective: " + currentObjectiveValue);
                    boolean feasible = dataExchange.getFeasibilityForIteration(admmIter);

                    if (improvement < improvementThreshold && feasible) {
                        System.out.println("Die Verbesserung ist weniger als " + (improvementThreshold * 100)
                                + "%. Abbruch der Iteration bei Iteration " + admmIter + " Verbesserung: " + improvement);
                        String filePath = "out/FinalIterationResults.xlsx";
                        exportFinalIterationResultsToExcel(dataExchange, admmIter, agents, currentPeriods, params , filePath);
                        break;
                    }
                }
                
                previousObjectiveValue = currentObjectiveValue;
            }

            //Save Results
            dataExchange.writeValuesToExcel_sorted("ADMM_Results_sorted.xlsx");

            System.out.println("Optimization completed successfully.");

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fileIn != null) {
				try {
					fileIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (workbook != null) {
				try {
					workbook.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (resultWorkbook != null) {
				try {
					resultWorkbook.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static double checkFeasibilityAndCalculateObjective(Set<Agent> agents, Set<Period> currentPeriods,
	        Parameters params, DataExchange dataExchange, int admmIter, boolean globalFeasible) {

	    System.out.println("Überprüfe Zulässigkeit nach Iteration " + admmIter);

	    double tolerancePercentage = 0.0005; // 0,5% Toleranz erlaubt
	    double zeroTolerance = 0.001; // Feste Toleranz, wenn Grenzwert Null ist
	    int currentIteration = admmIter + 1; // Da Iterationen ab 0 beginnen

	    double[][] xValues = dataExchange.getXValuesForIteration(currentIteration);
	    boolean[][][] yValues = dataExchange.getYValuesForIteration(currentIteration);
	    boolean feasible = true;

	    double objectiveValue = 0.0; // Zielfunktionswert sammeln

	    // Überprüfung der Gültigkeit und Berechnung des Zielfunktionswertes
	    for (Agent agent : agents) {
	        int agentIndex = agent.getId() - 1;
	        double powerElectrolyzer = params.powerElectrolyzer.get(agent);
	        double startupCost = params.startupCost.get(agent);
	        double standbyCost = params.standbyCost.get(agent);

	        for (Period period : currentPeriods) {
	            int periodIndex = period.getT() - 1;
	            double electricityPrice = params.electricityCost.get(period);
	            double intervalLength = params.intervalLength;

	            double x = xValues[agentIndex][periodIndex];
	            boolean[] y = yValues[agentIndex][periodIndex];

	            // Berechnung des Zielfunktionswertes
	            objectiveValue += x * powerElectrolyzer * electricityPrice * intervalLength;
	            objectiveValue += y[State.STARTING.ordinal()] ? startupCost * intervalLength : 0.0;
	            objectiveValue += y[State.STANDBY.ordinal()] ? standbyCost * intervalLength : 0.0;

	            // Nebenbedingung 1: Summe der y-Variablen gleich 1
	            int ySum = 0;
	            for (boolean yVal : y) {
	                ySum += yVal ? 1 : 0;
	            }
	            double ySumTolerance = tolerancePercentage; // Toleranz für Summenbedingung
	            if (Math.abs(ySum - 1) > ySumTolerance) {
	                System.out.println("Nebenbedingungsverletzung: Summe der y-Variablen (" + ySum + ") ungleich 1 für Agent " + agent.getId() + " in Periode " + period.getT());
	                feasible = false;
	            }

	            // Nebenbedingung 2: x ≥ 0
	            double lowerBound = 0.0;
	            double lowerTolerance = (lowerBound == 0.0) ? zeroTolerance : tolerancePercentage * Math.abs(lowerBound);
	            if (x < lowerBound - lowerTolerance) {
	                System.out.println("Nebenbedingungsverletzung: x (" + x + ") < 0 (mit Toleranz " + lowerTolerance + ") für Agent " + agent.getId() + " in Periode " + period.getT());
	                feasible = false;
	            }

	            // Nebenbedingung 3: x ≤ maxOperation * y_PRODUCTION + standbyLoad * y_STANDBY
	            double opMax = params.maxOperation.get(agent);
	            double standbyLoad = 0;

	            double xUpperBound = opMax * (y[State.PRODUCTION.ordinal()] ? 1 : 0)
	                               + standbyLoad * (y[State.STANDBY.ordinal()] ? 1 : 0);

	            double upperTolerance = (xUpperBound == 0.0) ? zeroTolerance : tolerancePercentage * Math.abs(xUpperBound);

	            if (x > xUpperBound + upperTolerance) {
	                System.out.println("Nebenbedingungsverletzung: x (" + x + ") überschreitet obere Grenze (" + xUpperBound + ") plus Toleranz (" + upperTolerance + ") für Agent " + agent.getId() + " in Periode " + period.getT());
	                feasible = false;
	            }

	            // Nebenbedingung 4: x ≥ minOperation * y_PRODUCTION + standbyLoad * y_STANDBY
	            double minOperation = params.minOperation.get(agent);
	            double xLowerBound = minOperation * (y[State.PRODUCTION.ordinal()] ? 1 : 0)
	                               + standbyLoad * (y[State.STANDBY.ordinal()] ? 1 : 0);

	            double lowerToleranceBound = (xLowerBound == 0.0) ? zeroTolerance : tolerancePercentage * Math.abs(xLowerBound);

	            if (x < xLowerBound - lowerToleranceBound) {
	                System.out.println("Nebenbedingungsverletzung: x (" + x + ") unterschreitet untere Grenze (" + xLowerBound + ") minus Toleranz (" + lowerToleranceBound + ") für Agent " + agent.getId() + " in Periode " + period.getT());
	                feasible = false;
	            }

	            // Zustandsübergangsbedingungen
	            if (period.getT() > 1) {
	                int prevPeriodIndex = periodIndex - 1;

	                // Hole y-Werte der vorherigen Periode
	                boolean[] yPrev = yValues[agentIndex][prevPeriodIndex];

	                // Nebenbedingung: y_t,STARTING ≤ y_{t-1,IDLE} + y_{t-1,STARTING}
	                boolean lhs_STARTING = y[State.STARTING.ordinal()];
	                boolean rhs_STARTING = yPrev[State.IDLE.ordinal()] || yPrev[State.STARTING.ordinal()];
	                if (lhs_STARTING && !rhs_STARTING) {
	                    System.out.println("Nebenbedingungsverletzung: Ungültiger Übergang zu STARTING für Agent " + agent.getId() + " in Periode " + period.getT());
	                    feasible = false;
	                }

	                // Nebenbedingung: y_t,PRODUCTION ≤ y_{t-1,PRODUCTION} + y_{t-1,STANDBY} + y_{t-startupDuration,STARTING}
	                boolean lhs_PRODUCTION = y[State.PRODUCTION.ordinal()];
	                boolean rhs_PRODUCTION = yPrev[State.PRODUCTION.ordinal()] || yPrev[State.STANDBY.ordinal()];
	                int startingHoldingDuration = params.holdingDurations.get(agent).get(State.STARTING);
	                
	                if (period.getT() > startingHoldingDuration) {
	                    int startupPeriodIndex = periodIndex - startingHoldingDuration;
	                    boolean yStartPrev = yValues[agentIndex][startupPeriodIndex][State.STARTING.ordinal()];
	                    rhs_PRODUCTION = rhs_PRODUCTION || yStartPrev;
	                }
	                if (lhs_PRODUCTION && !rhs_PRODUCTION) {
	                    System.out.println("Nebenbedingungsverletzung: Ungültiger Übergang zu PRODUCTION für Agent " + agent.getId() + " in Periode " + period.getT());
	                    feasible = false;
	                }

	                // Nebenbedingung: y_t,STANDBY ≤ y_{t-1,PRODUCTION} + y_{t-1,STANDBY}
	                boolean lhs_STANDBY = y[State.STANDBY.ordinal()];
	                boolean rhs_STANDBY = yPrev[State.PRODUCTION.ordinal()] || yPrev[State.STANDBY.ordinal()];
	                if (lhs_STANDBY && !rhs_STANDBY) {
	                    System.out.println("Nebenbedingungsverletzung: Ungültiger Übergang zu STANDBY für Agent " + agent.getId() + " in Periode " + period.getT());
	                    feasible = false;
	                }

	                // Nebenbedingung: y_t,IDLE ≤ y_{t-1,IDLE} + y_{t-1,PRODUCTION} + y_{t-1,STANDBY}
	                boolean lhs_IDLE = y[State.IDLE.ordinal()];
	                boolean rhs_IDLE = yPrev[State.IDLE.ordinal()] || yPrev[State.PRODUCTION.ordinal()] || yPrev[State.STANDBY.ordinal()];
	                if (lhs_IDLE && !rhs_IDLE) {
	                    System.out.println("Nebenbedingungsverletzung: Ungültiger Übergang zu IDLE für Agent " + agent.getId() + " in Periode " + period.getT());
	                    feasible = false;
	                }
	                
					// Rampen-Raten:
					double rampTolerance = 0.001; // Beispielwert für eine feste Toleranz
					// Rampenrate-Bedingung: Nur prüfen, wenn der Agent in der aktuellen Periode in Produktion ist
					if (y[State.PRODUCTION.ordinal()]) {
					    double currentXValue = xValues[agentIndex][periodIndex];
					    double previousXValue = xValues[agentIndex][prevPeriodIndex];

					    // Berechne die Differenz (Rampenresidual) zwischen aktuellem und vorherigem x-Wert
					    double diff1 = Math.abs(currentXValue - previousXValue);
					    double rampRate = 0.6; // Beispielwert für die maximale Rampenrate

					    // Berechnung der zulässigen Rampenrate mit Toleranz
					    double upperRampTolerance = (rampRate == 0.0) ? rampTolerance : rampTolerance * Math.abs(rampRate);
					    double rampUpConstraint1 = rampRate;

					    // Überprüfung auf Verletzung der Rampenrate
					    if (diff1 > rampUpConstraint1 + upperRampTolerance) {
					        System.out.println("Rampenratenverletzung (Bedingung 1): x_{a," + period.getT() + "} (" + currentXValue
					                + ") - x_{a," + (period.getT() - 1) + "} (" + previousXValue + ") überschreitet RampRateMax ("
					                + rampUpConstraint1 + ") plus Toleranz (" + upperRampTolerance + ") für Agent "
					                + agent.getId() + " in Periode " + period.getT());
					        feasible = false;
					    }
					}

	            }
	        }
	    }

	 // Berechnung der Abweichungskosten
	    for (Period period : currentPeriods) {
	        int periodIndex = period.getT() - 1;
	        double demandPeriod = params.demand.get(period);
	        double demandDeviationCost = params.demandDeviationCost;

	        double productionSum = 0.0;
	        for (Agent agent : agents) {
	            int agentIndex = agent.getId() - 1;
	            boolean[] y = yValues[agentIndex][periodIndex];
	            int agentID = agent.getId() - 1;
	            double powerElectrolyzer = params.powerElectrolyzer.get(agent);
	            double slope = params.slope.get(agent);
	            double intercept = params.intercept.get(agent);
	            double x = xValues[agentID][periodIndex];
	            double intervalLength = params.intervalLength;

	            // Umwandlung von boolean in 0 (false) oder 1 (true)
	            int isProductionActive = y[State.PRODUCTION.ordinal()] ? 1 : 0;

	            // Berechnung der Produktion: intercept wird nur verwendet, wenn isProductionActive == 1
	            productionSum += intervalLength * (x * slope * powerElectrolyzer + intercept * isProductionActive);
	        }

	        double deviation = Math.abs(demandPeriod - productionSum);
	        objectiveValue += deviation * demandDeviationCost;
	    }

	    // Ausgabe der Ergebnisse und Speicherung des Zielfunktionswertes
	    if (feasible) {
	        System.out.println("Lösung ist zulässig nach Iteration " + admmIter);
	        System.out.println("Zielfunktionswert: " + objectiveValue);
	        dataExchange.saveObjectiveValueForIteration(currentIteration, objectiveValue);
	        dataExchange.saveFeasibilityForIteration(currentIteration, true);  
	        
	    } else {
	        System.out.println("Lösung ist unzulässig nach Iteration " + admmIter);
	        System.out.println("Zielfunktionswert (nur zur Info): " + objectiveValue);
	        dataExchange.saveObjectiveValueForIteration(currentIteration, objectiveValue*100);
	        dataExchange.saveFeasibilityForIteration(currentIteration, false);  
	    }

	    return objectiveValue;
	}


	/**
	 * Load parameters from the Excel file.
	 */
	private static Parameters loadParameters(Workbook workbook) {
	    Sheet agentsSheet = workbook.getSheet("Agent");
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
	    Map<Agent, Double> rampRate = new HashMap<>();
	    Set<Agent> agents = new HashSet<>();

	    // Mindestverweildauer für Zustände (Idle, Starting, Production, Standby)
	    Map<Agent, Map<State, Integer>> holdingDurations = new HashMap<>();

	    // Lesen der Agenten-Daten aus der Agenten-Tabelle
	    for (Row row : agentsSheet) {
	        if (row.getRowNum() == 0)
	            continue;
	        
	        Agent agent = new Agent((int) row.getCell(0).getNumericCellValue());  // Column A
	        powerElectrolyzer.put(agent, row.getCell(1).getNumericCellValue());   // Column B
	        minOperation.put(agent, row.getCell(2).getNumericCellValue());        // Column C
	        maxOperation.put(agent, row.getCell(3).getNumericCellValue());        // Column D
	        startupCost.put(agent, row.getCell(4).getNumericCellValue());         // Column E
	        standbyCost.put(agent, row.getCell(5).getNumericCellValue());         // Column F
	        slope.put(agent, row.getCell(6).getNumericCellValue());               // Column G
	        intercept.put(agent, row.getCell(7).getNumericCellValue());           // Column H
	        
	        // Laden der Haltedauern (Idle, Starting, Production, Standby)
	        Map<State, Integer> agentHoldingDurations = new HashMap<>();
	        agentHoldingDurations.put(State.IDLE, (int) row.getCell(8).getNumericCellValue());       // Column I
	        agentHoldingDurations.put(State.STARTING, (int) row.getCell(9).getNumericCellValue());   // Column J
	        agentHoldingDurations.put(State.PRODUCTION, (int) row.getCell(10).getNumericCellValue()); // Column K
	        agentHoldingDurations.put(State.STANDBY, (int) row.getCell(11).getNumericCellValue());   // Column L
	        rampRate.put(agent, row.getCell(12).getNumericCellValue());           // Column M

	        // Haltedauern in die Map einfügen
	        holdingDurations.put(agent, agentHoldingDurations);
	        agents.add(agent);
	    }

	    // Laden der periodenspezifischen Parameter, einschließlich Demand
	    Map<Period, Double> electricityPrice = new HashMap<>();
	    Map<Period, Double> availablePower = new HashMap<>();
	    Map<Period, Double> periodDemand = new HashMap<>(); // Neu: Demand pro Periode
	    Set<Period> periods = new HashSet<>();

	    // Lesen der Perioden-Daten aus der Perioden-Tabelle
	    for (Row row : periodsSheet) {
	        if (row.getRowNum() == 0)
	            continue;
	        Period period = new Period((int) row.getCell(0).getNumericCellValue()); // Column A
	        electricityPrice.put(period, row.getCell(1).getNumericCellValue());		// Column B
	        availablePower.put(period, row.getCell(2).getNumericCellValue());		// Column C
	        periodDemand.put(period, row.getCell(3).getNumericCellValue()); 		// Column D
	        periods.add(period);										
	    }

	    // Global Parameter
	    double intervalLength = parametersSheet.getRow(1).getCell(1).getNumericCellValue();
	    double demandDeviationCost = parametersSheet.getRow(2).getCell(1).getNumericCellValue();

	    // Rückgabe der Parameter einschließlich des periodenspezifischen Demand und der Haltedauern
	    return new Parameters(startupCost, standbyCost, powerElectrolyzer, electricityPrice, minOperation, maxOperation,
	            periodDemand, slope, intercept, availablePower, intervalLength, startupDuration, demandDeviationCost,
	            holdingDurations, rampRate); 

	}
	
	public static void exportFinalIterationResultsToExcel(DataExchange dataExchange, int finalIteration, Set<Agent> agents, Set<Period> periods, Parameters params, String filePath) throws IOException {
	    Workbook workbook = new XSSFWorkbook(); // Erstellen einer neuen Excel-Arbeitsmappe
	    Sheet resultSheet = workbook.createSheet("Final Iteration Results");

	    // Erstellen der Headerzeile
	    Row headerRow = resultSheet.createRow(0);
	    headerRow.createCell(0).setCellValue("Period");
	    headerRow.createCell(1).setCellValue("Agent");
	    headerRow.createCell(2).setCellValue("X Value");
	    headerRow.createCell(3).setCellValue("Y State");
	    headerRow.createCell(4).setCellValue("Hydrogen Production");
	    headerRow.createCell(5).setCellValue("Period Demand");  // Hinzufügen des Periodenbedarfs

	    int rowIndex = 1;

	    // Daten für die finale Iteration
	    double[][] xValues = dataExchange.getXValuesForIteration(finalIteration);
	    boolean[][][] yValues = dataExchange.getYValuesForIteration(finalIteration);
	    double[][] hydrogenProductionValues = dataExchange.getHydrogenProductionForIteration(finalIteration);

	    // Iteriere zuerst über die Perioden und dann über die Agenten
	    for (Period period : periods) {
	        int periodIndex = period.getT() - 1;
	        double periodDemand = params.demand.get(period);  // Periodenbedarf für diese Periode

	        for (Agent agent : agents) {
	            int agentIndex = agent.getId() - 1;  // Angenommen, Agenten-IDs beginnen bei 1

	            // Erstelle eine neue Zeile in der Tabelle
	            Row row = resultSheet.createRow(rowIndex++);

	            // Perioden- und Agenteninformationen
	            row.createCell(0).setCellValue(period.getT()); // Period
	            row.createCell(1).setCellValue(agent.getId()); // Agent

	            // X-Werte
	            row.createCell(2).setCellValue(xValues[agentIndex][periodIndex]);

	            // Y-Zustand
	            String activeState = "None";
	            for (State state : State.values()) {
	                if (yValues[agentIndex][periodIndex][state.ordinal()]) {
	                    activeState = state.name();
	                    break;
	                }
	            }
	            row.createCell(3).setCellValue(activeState); // Y-State

	            // Wasserstoffproduktion
	            row.createCell(4).setCellValue(hydrogenProductionValues[agentIndex][periodIndex]); // Hydrogen Production

	            // Periodenbedarf
	            row.createCell(5).setCellValue(periodDemand);  // Period Demand
	        }
	    }

	    // Schreibe die Arbeitsmappe in die Datei
	    try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
	        workbook.write(fileOut);
	    } catch (IOException e) {
	        e.printStackTrace();
	    } finally {
	        workbook.close();
	    }

	    System.out.println("Results successfully written to Excel file: " + filePath);
	}


  /**
  * Configure the Gurobi solver.
  */
 private static void configureGurobi(GRBModel model) throws GRBException {
     model.set(GRB.DoubleParam.MIPGap, 0.000000001);
     model.set(GRB.DoubleParam.TimeLimit, 600); // 10-minute time limit
 }
}
