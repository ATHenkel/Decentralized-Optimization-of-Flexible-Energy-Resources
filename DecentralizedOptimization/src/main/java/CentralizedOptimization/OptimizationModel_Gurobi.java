package CentralizedOptimization;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import com.gurobi.gurobi.*;

class Point {
	double x;
	double y;

	public Point(double x, double y) {
		this.x = x;
		this.y = y;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	@Override
	public String toString() {
		return "Point{" + "x=" + x + ", y=" + y + '}';
	}
}

class Period {
	int t;

	public Period(int t) {
		this.t = t;
	}

	// Getter method for 't'
	public int getT() {
		return t;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		Period period = (Period) obj;
		return t == period.t;
	}

	@Override
	public int hashCode() {
		return Objects.hash(t);
	}
}

class Agent {
	int a;

	public Agent(int a) {
		this.a = a;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		Agent agent = (Agent) obj;
		return a == agent.a;
	}

	@Override
	public int hashCode() {
		return Objects.hash(a);
	}
}

enum State {
	IDLE, STARTING, PRODUCTION, STANDBY
}

class Parameters {

    Map<Agent, Double> startupCost;
    Map<Agent, Double> standbyCost;
    Map<Agent, Double> powerElectrolyzer;
    Map<Period, Double> electricityCost;
    Map<Agent, Double> minOperation;
    Map<Agent, Double> maxOperation;
    Map<Period, Double> demand;
    Map<Agent, Double> slope;
    Map<Agent, Double> intercept;
    Map<Agent, Double> rampUpRate;
    Map<Agent, Double> rampDownRate;
    Map<Period, Double> availablePower;
    double intervalLengthSWO;
    Map<Agent, Integer> startupDuration;
    double demandDeviationCost;
    Map<Agent, Map<State, Integer>> holdingDurations;
    Set<Period> periods;

    public Map<Period, Double> renewableEnergy; // Erneuerbare Energie
    public Map<Period, Map<Agent, Double>> swoXVarResults; // Ergebnisse der SWO
    public Map<Period, Map<Agent, Map<State, Double>>> swoYVarResults; // Zustandsbezogene SWO-Ergebnisse
    public Map<Period, Double> swoGridEnergyResults; // Netzenergie-Ergebnisse aus der SWO
    public Map<Period, Map<Integer, Double>> fluctuatingRenewableEnergy; // Fluktuierende erneuerbare Energien

    public int rtoStepsPerSWOStep = 10; // Anzahl der RTO-Schritte pro SWO-Schritt

    // Vollständiger Konstruktor
    public Parameters(Map<Agent, Double> startupCost, Map<Agent, Double> standbyCost,
                      Map<Agent, Double> powerElectrolyzer, Map<Period, Double> electricityCost,
                      Map<Agent, Double> minOperation, Map<Agent, Double> maxOperation,
                      Map<Period, Double> demand, Map<Agent, Double> slope,
                      Map<Agent, Double> intercept, Map<Period, Double> availablePower,
                      double intervalLength, Map<Agent, Integer> startupDuration,
                      double demandDeviationCost, Map<Agent, Map<State, Integer>> holdingDurations,
                      Set<Period> periods, Map<Period, Double> renewableEnergy,
                      Map<Period, Map<Agent, Double>> swoResults,
                      Map<Period, Double> netEnergyResults,
                      Map<Period, Map<Integer, Double>> fluctuatingRenewableEnergy,
                      int rtoStepsPerSWOStep) {

        this.startupCost = startupCost;
        this.standbyCost = standbyCost;
        this.powerElectrolyzer = powerElectrolyzer;
        this.electricityCost = electricityCost;
        this.minOperation = minOperation;
        this.maxOperation = maxOperation;
        this.demand = demand;
        this.slope = slope;
        this.intercept = intercept;
        this.availablePower = availablePower;
        this.intervalLengthSWO = intervalLength;
        this.startupDuration = startupDuration;
        this.demandDeviationCost = demandDeviationCost;
        this.holdingDurations = holdingDurations;
        this.periods = periods;
        this.renewableEnergy = renewableEnergy;
        this.swoXVarResults = swoResults;
        this.swoGridEnergyResults = netEnergyResults;
        this.fluctuatingRenewableEnergy = fluctuatingRenewableEnergy;
        this.rtoStepsPerSWOStep = rtoStepsPerSWOStep;
    }

    // Standardkonstruktor mit Standardwerten für optionale Parameter
    public Parameters(Map<Agent, Double> startupCost, Map<Agent, Double> standbyCost,
                      Map<Agent, Double> powerElectrolyzer, Map<Period, Double> electricityCost,
                      Map<Agent, Double> minOperation, Map<Agent, Double> maxOperation,
                      Map<Period, Double> demand, Map<Agent, Double> slope,
                      Map<Agent, Double> intercept, Map<Period, Double> availablePower,
                      double intervalLength, Map<Agent, Integer> startupDuration,
                      double demandDeviationCost, Map<Agent, Map<State, Integer>> holdingDurations,
                      Set<Period> periods, Map<Period, Double> renewableEnergy) {

        this(startupCost, standbyCost, powerElectrolyzer, electricityCost,
             minOperation, maxOperation, demand, slope, intercept, availablePower,
             intervalLength, startupDuration, demandDeviationCost, holdingDurations,
             periods, renewableEnergy, new HashMap<>(), new HashMap<>(), new HashMap<>(), 10);
    }
}

public class OptimizationModel_Gurobi {

	public static void main(String[] args) {
		try {

			/**
			 * Get the InputData
			 */
			String excelFilePath = System.getenv("EXCEL_FILE_PATH");
			if (excelFilePath == null || excelFilePath.isEmpty()) {
				excelFilePath = "in/InputData_Plugfest.xlsx"; 
			}

			FileInputStream excelFile = null;
			Workbook workbook = null;

			try {
				// Versuche, die Excel-Datei zu öffnen
				excelFile = new FileInputStream(excelFilePath);
				System.out.println(excelFilePath + " found and loaded.");

				// Versuche, das Workbook zu erstellen
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

			/**
			 * Define Periods
			 */
			Parameters params = loadParameters(workbook);
			Set<Agent> agents = params.startupCost.keySet();
			Set<Period> periods = params.periods;
			workbook.close();

			/**
			 * Define the optimization model
			 */
			GRBEnv env = new GRBEnv(true);
			env.start();

			GRBModel model = new GRBModel(env);
			configureGurobi(model);

			Map<Agent, Map<Period, GRBVar>> xVar = new HashMap<>();
			Map<Agent, Map<Period, Map<State, GRBVar>>> yVar = new HashMap<>();

			defineDecisionVariables(model, agents, periods, xVar, yVar);

			GRBLinExpr objective = new GRBLinExpr();
			defineObjectiveFunction(model, params, agents, periods, xVar, yVar, objective);
			model.setObjective(objective, GRB.MINIMIZE);

			defineConstraints(model, params, agents, periods, xVar, yVar);
			
			/**
			 * Solve the optimization model
			 */
			model.optimize();

			if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {

				// Zeit nach der Optimierung
				long endTime = System.nanoTime();

				// Berechnung der Dauer in Sekunden
				SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
				String timestamp = formatter.format(new Date());
				String desktopPath = System.getenv("DESKTOP_PATH");
				String runNumber = System.getenv("OPTIMIZATION_RUN");
				String saveDetails;

				if (runNumber == null || runNumber.isEmpty()) {
					saveDetails = timestamp;
				} else {
					saveDetails = runNumber;
				}

				if (desktopPath == null || desktopPath.isEmpty()) {
					desktopPath = Paths.get(System.getProperty("user.home"), "Desktop").toString(); // Standardpfad
				}

				String excelFilePathFinalResults = desktopPath + "/" + saveDetails + "_GurobiResults" + ".xlsx";

				exportFinalIterationResultsToExcel(agents, periods, params, excelFilePathFinalResults, xVar, yVar, model);

			} else {
				System.out.println("No optimal solution found");
				model.write("optimizationmodel.lp");
			}

			model.dispose();
			env.dispose();

		} catch (GRBException | IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Define decision variables.
	 */
	private static void defineDecisionVariables(GRBModel model, Set<Agent> agents, Set<Period> periods,
			Map<Agent, Map<Period, GRBVar>> xVar, Map<Agent, Map<Period, Map<State, GRBVar>>> yVar)
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
			Set<Period> periods, Map<Agent, Map<Period, GRBVar>> x, Map<Agent, Map<Period, Map<State, GRBVar>>> y,
			GRBLinExpr objective) throws GRBException {

		// Production cost
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

		// Startup cost
		GRBLinExpr startupCostExpr = new GRBLinExpr();

		for (Agent a : agents) {
			double standbyCost = params.standbyCost.get(a);
			for (Period t : periods) {
				// Hinzufügen der Startup-Kosten
				startupCostExpr.addTerm(params.intervalLengthSWO * params.startupCost.get(a),
						y.get(a).get(t).get(State.STARTING));

				// Standby Kosten
				startupCostExpr.addTerm(params.intervalLengthSWO * standbyCost, y.get(a).get(t).get(State.STANDBY));
			}
		}
		objective.add(startupCostExpr);

		// Demand Deviation Costs
		Map<Period, Double> demandForPeriods = params.demand;
		double demandDeviationCost = params.demandDeviationCost;

		// Create variables for positive and negative deviations for each period
		Map<Period, GRBVar> positiveDeviations = new HashMap<>();
		Map<Period, GRBVar> negativeDeviations = new HashMap<>();

		for (Period t : periods) {
			// Positive and negative deviations for each period
			positiveDeviations.put(t,
					model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "positiveDeviation_period_" + t.getT()));
			negativeDeviations.put(t,
					model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "negativeDeviation_period_" + t.getT()));

			// Get the demand for the current period
			double periodDemand = demandForPeriods.get(t);

			// Calculate total production for the current period
			GRBLinExpr productionInPeriod = new GRBLinExpr();
			for (Agent a : agents) {
				double slope = params.slope.get(a);
				double intercept = params.intercept.get(a);
				double powerElectrolyzer = params.powerElectrolyzer.get(a);
				double intervalLength = params.intervalLengthSWO;
				GRBVar productionStateVar = y.get(a).get(t).get(State.PRODUCTION);

				// Berechnung: intervalLength * (powerElectrolyzer * slope * xVars + intercept)
				productionInPeriod.addTerm(powerElectrolyzer * slope * intervalLength, x.get(a).get(t));
				productionInPeriod.addTerm(intercept * intervalLength, productionStateVar);
			}

			// Add constraints for positive and negative deviations
			GRBLinExpr positiveDeviationExpr = new GRBLinExpr();
			positiveDeviationExpr.addTerm(1.0, positiveDeviations.get(t));
			positiveDeviationExpr.addConstant(-periodDemand);
			positiveDeviationExpr.add(productionInPeriod);
			model.addConstr(positiveDeviationExpr, GRB.GREATER_EQUAL, 0.0, "positiveDeviation_" + t.getT());

			GRBLinExpr negativeDeviationExpr = new GRBLinExpr();
			negativeDeviationExpr.addTerm(1.0, negativeDeviations.get(t));
			negativeDeviationExpr.addConstant(periodDemand);
			// Subtract each term in productionInPeriod from negativeDeviationExpr
			for (int i = 0; i < productionInPeriod.size(); i++) {
				negativeDeviationExpr.addTerm(-productionInPeriod.getCoeff(i), productionInPeriod.getVar(i));
			}
			model.addConstr(negativeDeviationExpr, GRB.GREATER_EQUAL, 0.0, "negativeDeviation_" + t.getT());

			// Add the absolute deviation per period to the objective function
			objective.addTerm(demandDeviationCost, positiveDeviations.get(t));
			objective.addTerm(demandDeviationCost, negativeDeviations.get(t));
		}
	}

	/**
	 * Define the constraints.
	 */
	private static void defineConstraints(GRBModel model, Parameters params, Set<Agent> agents, Set<Period> periods,
			Map<Agent, Map<Period, GRBVar>> x, Map<Agent, Map<Period, Map<State, GRBVar>>> y) throws GRBException {
		// Initial state
		for (Agent a : agents) {
			Period firstPeriod = new Period(1);
			model.addConstr(y.get(a).get(firstPeriod).get(State.IDLE), GRB.EQUAL, 1.0,
					"initialState_" + a.a + "_" + firstPeriod.t);
		}

		// Operational limits (Constraint 1)
		for (Agent a : agents) {
			for (Period t : periods) {
				// Min Operation Constraint
				GRBLinExpr minOperationExpr = new GRBLinExpr();
				minOperationExpr.addTerm(params.minOperation.get(a), y.get(a).get(t).get(State.PRODUCTION));
				model.addConstr(x.get(a).get(t), GRB.GREATER_EQUAL, minOperationExpr,
						"minOperation_" + a.a + "_" + t.t);

				// Max Operation Constraint
				GRBLinExpr maxOperationExpr = new GRBLinExpr();
				maxOperationExpr.addTerm(params.maxOperation.get(a), y.get(a).get(t).get(State.PRODUCTION));
				model.addConstr(x.get(a).get(t), GRB.LESS_EQUAL, maxOperationExpr, "maxOperation_" + a.a + "_" + t.t);
			}
		}

		// State exclusivity (Constraint 2)
		for (Agent a : agents) {
			for (Period t : periods) {
				GRBLinExpr statusSum = new GRBLinExpr();
				for (State s : State.values()) {
					statusSum.addTerm(1.0, y.get(a).get(t).get(s));
				}
				model.addConstr(statusSum, GRB.EQUAL, 1.0, "stateExclusivity_" + a.a + "_" + t.t);
			}
		}

		// State transitions (Constraint 4)
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

					GRBLinExpr standbyExpr = new GRBLinExpr();
					standbyExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.PRODUCTION));
					standbyExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.STANDBY));
					model.addConstr(y.get(a).get(t).get(State.STANDBY), GRB.LESS_EQUAL, standbyExpr,
							"stateTransitionStandby_" + a.a + "_" + t.t);

					GRBLinExpr idleExpr = new GRBLinExpr();
					idleExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.PRODUCTION));
					idleExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.STANDBY));
					idleExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.IDLE));
					model.addConstr(y.get(a).get(t).get(State.IDLE), GRB.LESS_EQUAL, idleExpr,
							"stateTransitionIdle_" + a.a + "_" + t.t);
				}
			}
		}

		// Constraint to ensure minimum dwell time (Constraint 7)
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
									GRBLinExpr dwellExpr = new GRBLinExpr();
									dwellExpr.addTerm(1.0, y.get(a).get(t).get(s));
									dwellExpr.addTerm(-1.0, y.get(a).get(prevPeriod).get(s));
									model.addConstr(dwellExpr, GRB.LESS_EQUAL, y.get(a).get(futurePeriod).get(s),
											"minDwellTime_" + a.a + "_" + t.t + "_" + s);
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
	    Map<Period, Double> periodDemand = new HashMap<>();
	    Map<Period, Double> renewableEnergy = new HashMap<>(); // Neu: Erneuerbare Energien
	    Set<Period> periods = new HashSet<>();

	    // Lesen der Perioden-Daten aus der Perioden-Tabelle
	    for (Row row : periodsSheet) {
	        if (row.getRowNum() == 0) continue; // Kopfzeile überspringen

	        Period period = new Period((int) row.getCell(0).getNumericCellValue()); // Column A
	        electricityPrice.put(period, row.getCell(1).getNumericCellValue()); // Column B
	        availablePower.put(period, row.getCell(2).getNumericCellValue()); // Column C
	        periodDemand.put(period, row.getCell(3).getNumericCellValue()); // Column D
	        renewableEnergy.put(period, row.getCell(2).getNumericCellValue()); // Column E (erneuerbare Energie)
	        periods.add(period);
	    }

	    // Global Parameter
	    double intervalLength = parametersSheet.getRow(1).getCell(1).getNumericCellValue();
	    double demandDeviationCost = parametersSheet.getRow(2).getCell(1).getNumericCellValue();

	    // Rückgabe des Parameters-Objekts mit erneuerbarer Energie
	    return new Parameters(
	            startupCost,
	            standbyCost,
	            powerElectrolyzer,
	            electricityPrice,
	            minOperation,
	            maxOperation,
	            periodDemand,
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

	/**
	 * Save the results to an Excel file.
	 */

	private static void exportFinalIterationResultsToExcel(Set<Agent> agents, Set<Period> periods, Parameters params,
			String filePath, Map<Agent, Map<Period, GRBVar>> xVar, Map<Agent, Map<Period, Map<State, GRBVar>>> yVar, GRBModel model)
			throws IOException, GRBException {
		Workbook workbook = new XSSFWorkbook();

		// Sheet 1: Final Iteration Results
		Sheet resultSheet = workbook.createSheet("Final Iteration Results");
		Row headerRow = resultSheet.createRow(0);
		headerRow.createCell(0).setCellValue("Period");
		headerRow.createCell(1).setCellValue("Agent");
		headerRow.createCell(2).setCellValue("X Value");
		headerRow.createCell(3).setCellValue("Y State");
		headerRow.createCell(4).setCellValue("Hydrogen Production");
		headerRow.createCell(5).setCellValue("Period Demand");

		int rowIndex = 1;
		for (Period period : periods) {
			double periodDemand = params.demand.get(period);

			for (Agent agent : agents) {
				Row row = resultSheet.createRow(rowIndex++);
				row.createCell(0).setCellValue(period.getT());
				row.createCell(1).setCellValue(agent.a);
				row.createCell(2).setCellValue(xVar.get(agent).get(period).get(GRB.DoubleAttr.X));

				// Active state in Y variables
				String activeState = "None";
				for (State state : State.values()) {
					if (yVar.get(agent).get(period).get(state).get(GRB.DoubleAttr.X) == 1.0) {
						activeState = state.name();
						break;
					}
				}
				row.createCell(3).setCellValue(activeState);
				
				double xValue = xVar.get(agent).get(period).get(GRB.DoubleAttr.X);
				double productionState = yVar.get(agent).get(period).get(State.PRODUCTION).get(GRB.DoubleAttr.X);
				double slope = params.slope.get(agent);
				double intercept = params.intercept.get(agent);
				double powerElectrolyzer = params.powerElectrolyzer.get(agent);
				double intervalLength = params.intervalLengthSWO;

				// Berechnung von productionInPeriod
				double mH2inPeriod = intervalLength
						* (powerElectrolyzer * slope * xValue + intercept * productionState);
				row.createCell(4).setCellValue(mH2inPeriod);
				row.createCell(5).setCellValue(periodDemand);
			}
		}

		// Objective value
		Row objectiveRow = resultSheet.createRow(rowIndex++);
		objectiveRow.createCell(0).setCellValue("Objective Value");
		objectiveRow.createCell(1).setCellValue(model.get(GRB.DoubleAttr.ObjVal));
		

		// Save to file
		try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
			workbook.write(fileOut);
		} finally {
			workbook.close();
		}

		System.out.println("Results successfully written to Excel file: " + filePath);
	}

}
