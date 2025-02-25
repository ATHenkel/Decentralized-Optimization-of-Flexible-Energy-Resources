////Auskommentieren für Gurobi 
// 
//
//package CentralizedOptimization;
//
//import ilog.concert.*;
//import ilog.cplex.*;
//import org.apache.poi.ss.usermodel.*;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//
//import java.awt.Desktop;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.util.*;
//
//class Point {
//    double x;
//    double y;
//
//    public Point(double x, double y) {
//        this.x = x;
//        this.y = y;
//    }
//
//    public double getX() {
//        return x;
//    }
//
//    public double getY() {
//        return y;
//    }
//
//    @Override
//    public String toString() {
//        return "Point{" +
//                "x=" + x +
//                ", y=" + y +
//                '}';
//    }
//}
//
//class Period {
//    int t;
//
//    public Period(int t) {
//        this.t = t;
//    }
//    
//    // Getter method for 't'
//    public int getT() {
//        return t;
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        if (this == obj) return true;
//        if (obj == null || getClass() != obj.getClass()) return false;
//        Period period = (Period) obj;
//        return t == period.t;
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(t);
//    }
//}
//
//class Agent {
//    int a;
//
//    public Agent(int a) {
//        this.a = a;
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        if (this == obj) return true;
//        if (obj == null || getClass() != obj.getClass()) return false;
//        Agent agent = (Agent) obj;
//        return a == agent.a;
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(a);
//    }
//}
//
//enum State {
//    IDLE, STARTING, PRODUCTION, STANDBY
//}
//
//class Parameters {
//	
//    Map<Agent, Double> startupCost;
//    Map<Agent, Double> standbyCost;
//    Map<Agent, Double> powerElectrolyzer;
//    Map<Period, Double> electricityCost;
//    Map<Agent, Double> minOperation;
//    Map<Agent, Double> maxOperation;
//    Map<Period, Double> demand;
//    Map<Agent, Double> slope;
//    Map<Agent, Double> intercept;
//    Map<Agent, Double> rampUpRate;
//    Map<Agent, Double> rampDownRate;
//    Map<Period, Double> availablePower;
//    double intervalLength;
//    Map<Agent, Integer> startupDuration;
//    double demandDeviationCost;
//    Map<Agent, Map<State, Integer>> holdingDurations;
//
//    public Parameters(Map<Agent, Double> startupCost, Map<Agent, Double> standbyCost, Map<Agent, Double> powerElectrolyzer, Map<Period, Double> electricityCost,
//                      Map<Agent, Double> minOperation, Map<Agent, Double> maxOperation, Map<Period, Double> demand,
//                      Map<Agent, Double> slope,  Map<Agent, Double> intercept, Map<Period, Double> availablePower, double intervalLength,
//                      Map<Agent, Integer> startupDuration, double demandDeviationCost,  Map<Agent, Map<State, Integer>> holdingDurations) {
//    	
//        this.startupCost = startupCost;
//        this.standbyCost = standbyCost;
//        this.powerElectrolyzer = powerElectrolyzer;
//        this.electricityCost = electricityCost;
//        this.minOperation = minOperation;
//        this.maxOperation = maxOperation;
//        this.demand = demand;
//        this.slope = slope;
//        this.intercept = intercept;
//        this.availablePower = availablePower;
//        this.intervalLength = intervalLength;
//        this.startupDuration = startupDuration;
//        this.demandDeviationCost = demandDeviationCost;
//        this.holdingDurations = holdingDurations;
//    }
//    
//}
//
//public class OptimizationModel {
//
//    public static void main(String[] args) {
//        try {
//        	
//            /**
//             * Get the InputData
//             */
//            FileInputStream fileIn = new FileInputStream("in/InputData.xlsx");
//            Workbook workbook = new XSSFWorkbook(fileIn);
//
//            Parameters params = loadParameters(workbook);
//            Set<Agent> agents = params.startupCost.keySet();
//			Set<Period> periods = new HashSet<>();
//			for (int i = 1; i <= 10; i++) {
//				periods.add(new Period(i));
//			}
//
//            workbook.close();
//
//            /**
//             * Define the optimization model
//             */
//            IloCplex cplex = new IloCplex();
//            configureCplex(cplex);
//            
//            Map<Agent, Map<Period, IloNumVar>> xVar = new HashMap<>();
//            Map<Agent, Map<Period, Map<State, IloIntVar>>> yVar = new HashMap<>();
//            Map<Agent, Map<Period, IloNumVar>> mH2 = new HashMap<>();
//            double M = 1000.0;
//
//            defineDecisionVariables(cplex, agents, periods, xVar, yVar, mH2, M);
//
//            IloLinearNumExpr objective = cplex.linearNumExpr();
//            defineObjectiveFunction(cplex, params, agents, periods, xVar, yVar, mH2, objective);
//            cplex.addMinimize(objective);
//
//            defineConstraints(cplex, params, agents, periods, xVar, yVar, mH2, M);
//            
//            /**
//             * Solve the optimization model
//             */
//            if (cplex.solve()) {
//                displaySolution(cplex, agents, periods, xVar, yVar);
//                saveResults(cplex, agents, periods, xVar, yVar, mH2, params);
//                cplex.exportModel("optimizationmodel.lp");
//            } else {
//                System.out.println("Solution status = " + cplex.getStatus());
//                System.out.println("Solver status = " + cplex.getCplexStatus());
//                System.out.println("No optimal solution found");
//                cplex.exportModel("optimizationmodel.lp");
//            }
//
//            cplex.end();
//        } catch (IloException | IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * Define decision variables.
//     */
//    private static void defineDecisionVariables(IloCplex cplex, Set<Agent> agents, Set<Period> periods,
//                                                Map<Agent, Map<Period, IloNumVar>> xVar,
//                                                Map<Agent, Map<Period, Map<State, IloIntVar>>> yVar,
//                                                Map<Agent, Map<Period, IloNumVar>> mH2, double M) throws IloException {
//        for (Agent a : agents) {
//            xVar.put(a, new HashMap<>());
//            yVar.put(a, new HashMap<>());
//            mH2.put(a, new HashMap<>());
//            for (Period t : periods) {
//                xVar.get(a).put(t, cplex.numVar(0, 1, IloNumVarType.Float, "x_" + a.a + "_" + t.t));
//                mH2.get(a).put(t, cplex.numVar(0, Double.MAX_VALUE, IloNumVarType.Float, "mH2_" + a.a + "_" + t.t));
//                yVar.get(a).put(t, new HashMap<>());
//                for (State s : State.values()) {
//                    yVar.get(a).get(t).put(s, cplex.boolVar("y_" + a.a + "_" + t.t + "_" + s));
//                }
//            }
//        }
//    }
//
//    /**
//     * Define the objective function.
//     */
//	private static void defineObjectiveFunction(IloCplex cplex, Parameters params, Set<Agent> agents,
//			Set<Period> periods, Map<Agent, Map<Period, IloNumVar>> x, Map<Agent, Map<Period, Map<State, IloIntVar>>> y,
//			Map<Agent, Map<Period, IloNumVar>> mH2, IloLinearNumExpr objective) throws IloException {
//		
//		// Production cost
//		IloLinearNumExpr productionCostExpr = cplex.linearNumExpr();
//		for (Agent a : agents) {
//			for (Period t : periods) {
//				Double Pel = params.powerElectrolyzer.get(a);
//				Double CtE = params.electricityCost.get(t);
//				if (Pel != null && CtE != null) {
//					productionCostExpr.addTerm(Pel * CtE * params.intervalLength, x.get(a).get(t));
//				}
//			}
//		}
//		objective.add(productionCostExpr);
//
//		// Startup cost
//		IloLinearNumExpr startupCostExpr = cplex.linearNumExpr();
//		
//		for (Agent a : agents) {
//			double standbyCost = params.standbyCost.get(a);
//		    for (Period t : periods) {
//		        // Hinzufügen der Startup-Kosten
//		        startupCostExpr.addTerm(params.intervalLength *params.startupCost.get(a), y.get(a).get(t).get(State.STARTING));
//		        
//		        // Standby Kosten
//		        startupCostExpr.addTerm(params.intervalLength * standbyCost,  y.get(a).get(t).get(State.STANDBY));
//		    }
//		}
//		objective.add(startupCostExpr);
//
//        // Demand Deviation Costs 
//        Map<Period, Double> demandForPeriods = params.demand; 
//        double demandDeviationCost = params.demandDeviationCost;
//        
//        // Create variables for positive and negative deviations for each period
//        Map<Period, IloNumVar> positiveDeviations = new HashMap<>();
//        Map<Period, IloNumVar> negativeDeviations = new HashMap<>();
//        
//        for (Period t : periods) {
//        	 int periodIndex = t.getT() - 1;
//        	
//            // Positive and negative deviations for each period
//            positiveDeviations.put(t, cplex.numVar(0, Double.MAX_VALUE, "positiveDeviation_period_" + t.getT()));
//            negativeDeviations.put(t, cplex.numVar(0, Double.MAX_VALUE, "negativeDeviation_period_" + t.getT()));
//
//            // Add constraints to capture the deviations per period
//            double periodDemand = demandForPeriods.get(t); 
//            
//            IloNumExpr productionInPeriod = cplex.numExpr(); 
//            for (Agent a : agents) {
//
//                double slope = params.slope.get(a);
//                double intercept = params.intercept.get(a);
//                double powerElectrolyzer = params.powerElectrolyzer.get(a);
//                double intervalLength = params.intervalLength;
//                IloIntVar productionStateVar = y.get(a).get(t).get(State.PRODUCTION);
//
//                // Berechnung: intervalLength * (powerElectrolyzer * slope * xVars + intercept)
//                IloNumExpr agentProduction = cplex.prod(intervalLength, 
//                    cplex.sum(
//                        cplex.prod(powerElectrolyzer * slope, x.get(a).get(t)), // powerElectrolyzer * slope * xVars
//                        cplex.prod(intercept, productionStateVar) // + intercept
//                    )
//                );
//
//                // Summiere die Produktionsmenge für den Agenten zur Gesamtproduktion hinzu
//                productionInPeriod = cplex.sum(productionInPeriod, agentProduction);
//            }
//
//            // Add deviations constraints per period
//            cplex.addGe(positiveDeviations.get(t), cplex.diff(productionInPeriod, periodDemand));
//            cplex.addGe(negativeDeviations.get(t), cplex.diff(periodDemand, productionInPeriod));
//
//            // Add the absolute deviation per period to the objective function
//            objective.addTerm(demandDeviationCost, positiveDeviations.get(t));
//            objective.addTerm(demandDeviationCost, negativeDeviations.get(t));
//        }
//	}
//
//    /**
//     * Define the constraints.
//     */
//    private static void defineConstraints(IloCplex cplex, Parameters params, Set<Agent> agents, Set<Period> periods,
//                                          Map<Agent, Map<Period, IloNumVar>> x, Map<Agent, Map<Period, Map<State, IloIntVar>>> y,
//                                          Map<Agent, Map<Period, IloNumVar>> mH2, double M) throws IloException {
//        // Initial state
//        for (Agent a : agents) {
//            Period firstPeriod = new Period(1);
//            cplex.addEq(y.get(a).get(firstPeriod).get(State.IDLE), 1.0);
//        }
//
//     // Define mH2 Production Rate (Constraint 0)
//        for (Agent a : agents) {
//            for (Period t : periods) {
//                // Produktionsmenge über den Zusammenhang: intervalLength * (powerElectrolyzer * slope * xVars + intercept)
//                IloNumExpr productionExpr = cplex.prod(params.intervalLength, 
//                    cplex.sum(
//                        cplex.prod(params.powerElectrolyzer.get(a) * params.slope.get(a), x.get(a).get(t)), // powerElectrolyzer * slope * xVars
//                        cplex.prod(params.intercept.get(a), y.get(a).get(t).get(State.PRODUCTION)) // + intercept
//                    )
//                );
//
//                // Bedingung: mH2 ist kleiner oder gleich M * y(PRODUCTION)
//                cplex.addLe(mH2.get(a).get(t), cplex.prod(y.get(a).get(t).get(State.PRODUCTION), M));
//
//                // Bedingung: mH2 ist kleiner oder gleich der berechneten Produktionsmenge
//                cplex.addLe(mH2.get(a).get(t), productionExpr);
//
//                // Bedingung: mH2 ist größer oder gleich der Produktionsmenge unter der Bedingung, dass y(PRODUCTION) aktiv ist
//                IloNumExpr lhs = cplex.diff(
//                    productionExpr, 
//                    cplex.prod(cplex.diff(1, y.get(a).get(t).get(State.PRODUCTION)), M) // Modifikation für den Zustand PRODUCTION
//                );
//
//                cplex.addGe(mH2.get(a).get(t), lhs);
//            }
//        }
//
//
//     // Operational limits (Constraint 1)
//        for (Agent a : agents) {
//            for (Period t : periods) {
//                // Min Operation Constraint
//                cplex.addGe(x.get(a).get(t), cplex.prod(params.minOperation.get(a), y.get(a).get(t).get(State.PRODUCTION)));
//                
//                // Max Operation Constraint
//                cplex.addLe(x.get(a).get(t), cplex.prod(params.maxOperation.get(a), y.get(a).get(t).get(State.PRODUCTION)));
//            }
//        }
//
//        // State exclusivity (Constraint 2)
//        for (Agent a : agents) {
//            for (Period t : periods) {
//                IloLinearNumExpr statusSum = cplex.linearNumExpr();
//                for (State s : State.values()) {
//                    statusSum.addTerm(1.0, y.get(a).get(t).get(s));
//                }
//                cplex.addEq(statusSum, 1.0);
//            }
//        }
//
//        // State transitions (Constraint 4)
//        for (Agent a : agents) {
//            for (Period t : periods) {
//                if (t.t > 1) {
//                    Period prevPeriod = new Period(t.t - 1);
//                    int startingHoldingDuration = params.holdingDurations.get(a).get(State.STARTING);
//                    Period startupPeriod = new Period(t.getT() - startingHoldingDuration);
//
//                    cplex.addLe(y.get(a).get(t).get(State.STARTING),
//                            cplex.sum(y.get(a).get(prevPeriod).get(State.IDLE), y.get(a).get(prevPeriod).get(State.STARTING)));
//
//                    if (t.t > startingHoldingDuration) {
//                        cplex.addLe(y.get(a).get(t).get(State.PRODUCTION),
//                                cplex.sum(y.get(a).get(startupPeriod).get(State.STARTING), y.get(a).get(prevPeriod).get(State.PRODUCTION),
//                                        y.get(a).get(prevPeriod).get(State.STANDBY)));
//                    } else {
//                        cplex.addLe(y.get(a).get(t).get(State.PRODUCTION),
//                                cplex.sum(y.get(a).get(prevPeriod).get(State.STARTING), y.get(a).get(prevPeriod).get(State.PRODUCTION),
//                                        y.get(a).get(prevPeriod).get(State.STANDBY)));
//                    }
//
//                    cplex.addLe(y.get(a).get(t).get(State.STANDBY),
//                            cplex.sum(y.get(a).get(prevPeriod).get(State.PRODUCTION), y.get(a).get(prevPeriod).get(State.STANDBY)));
//
//                    cplex.addLe(y.get(a).get(t).get(State.IDLE),
//                            cplex.sum(y.get(a).get(prevPeriod).get(State.PRODUCTION), y.get(a).get(prevPeriod).get(State.STANDBY),
//                                    y.get(a).get(prevPeriod).get(State.IDLE)));
//                }
//            }
//        }
//        
//        // Constraint to ensure minimum dwell time (Constraint 7)
//      for (Agent a : agents) {
//          for (State s : State.values()) {
//              Integer minDwellTime = params.holdingDurations.get(a).get(s);
//              if (minDwellTime != null) {
//                  for (Period t : periods) {
//                      if (t.t >= minDwellTime && t.t >= 2) { // Ensure period is greater than or equal to the minimum dwell time and at least 2
//                          for (int tau = 1; tau <= minDwellTime - 1; tau++) {
//                              if (t.t - tau >= 1) { // Ensure that the period exists
//                                  Period prevPeriod = new Period(t.t - 1);
//                                  Period futurePeriod = new Period(t.t - tau);
//                                  cplex.addLe(
//                                      cplex.diff(y.get(a).get(t).get(s), y.get(a).get(prevPeriod).get(s)),
//                                      y.get(a).get(futurePeriod).get(s)
//                                  );
//                              }
//                          }
//                      }
//                  }
//              }
//          }
//      }
//
//    }
//
//    /**
//     * Display the solution on the console.
//     */
//    private static void displaySolution(IloCplex cplex, Set<Agent> agents, Set<Period> periods,
//                                        Map<Agent, Map<Period, IloNumVar>> x,
//                                        Map<Agent, Map<Period, Map<State, IloIntVar>>> y) throws IloException {
//        System.out.println("Solution status = " + cplex.getStatus());
//        System.out.println("Solver status = " + cplex.getCplexStatus());
//        System.out.println("Optimal solution found");
//        System.out.println("Total cost: " + cplex.getObjValue());
//
//        for (Agent a : agents) {
//            for (Period t : periods) {
//                System.out.println(String.format("x[%d][%d] = %.2f", a.a, t.t, cplex.getValue(x.get(a).get(t))));
//                for (State s : State.values()) {
//                    System.out.println(String.format("y[%d][%d][%s] = %.0f", a.a, t.t, s, cplex.getValue(y.get(a).get(t).get(s))));
//                }
//            }
//        }
//    }
//
//    /**
//     * Save the results to an Excel file.
//     */
//    private static void saveResults(IloCplex cplex, Set<Agent> agents, Set<Period> periods,
//                                    Map<Agent, Map<Period, IloNumVar>> x,
//                                    Map<Agent, Map<Period, Map<State, IloIntVar>>> y,
//                                    Map<Agent, Map<Period, IloNumVar>> mH2, Parameters params) throws IloException, IOException {
//        Workbook resultWorkbook = new XSSFWorkbook();
//        Sheet resultSheet = resultWorkbook.createSheet("Results");
//
//        Row headerRow = resultSheet.createRow(0);
//        headerRow.createCell(0).setCellValue("Period");
//        headerRow.createCell(1).setCellValue("Agent");
//        headerRow.createCell(2).setCellValue("Utilization");
//        headerRow.createCell(3).setCellValue("State");
//        headerRow.createCell(4).setCellValue("Demand");
//        headerRow.createCell(5).setCellValue("Total Production");
//        headerRow.createCell(6).setCellValue("Demand Deviation");
//        headerRow.createCell(7).setCellValue("Production Cost");
//        headerRow.createCell(8).setCellValue("Standby Cost");
//        headerRow.createCell(9).setCellValue("Startup Cost");
//        headerRow.createCell(10).setCellValue("Demand Deviation Cost");
//        headerRow.createCell(11).setCellValue("Agent Production");
//
//        int rowIndex = 1;
//        double totalProductionValue;
//
//        // Iterate over each period to calculate total production and other metrics
//        for (Period t : periods) {
//            totalProductionValue = 0.0;
//
//            for (Agent a : agents) {
//                totalProductionValue += cplex.getValue(mH2.get(a).get(t));
//            }
//
//            double currentDemand = params.demand.get(t); // Holen Sie sich den spezifischen Demand für die Periode t
//            double demandDeviationValue = (currentDemand > 0) ? Math.abs(currentDemand - totalProductionValue) : 0.0;
//
//            double productionCostValue = 0.0;
//            double standbyCostValue = 0.0;
//            double startupCostValue = 0.0;
//
//            for (Agent a : agents) {
//                Double Pel = params.powerElectrolyzer.get(a);
//                Double CtE = params.electricityCost.get(t);
//                if (Pel != null && CtE != null) {
//                    productionCostValue += Pel * CtE * params.intervalLength * cplex.getValue(x.get(a).get(t));
//                }
//                standbyCostValue += params.standbyCost.get(a) * cplex.getValue(y.get(a).get(t).get(State.STANDBY));
//                startupCostValue += params.startupCost.get(a) * cplex.getValue(y.get(a).get(t).get(State.STARTING));
//            }
//
//            for (Agent a : agents) {
//                double agentProduction = cplex.getValue(mH2.get(a).get(t));
//
//                Row row = resultSheet.createRow(rowIndex++);
//                row.createCell(0).setCellValue(t.t);  // t.t könnte die spezifische Zeit für die Periode darstellen
//                row.createCell(1).setCellValue(a.a);  // a.a könnte den Namen oder die ID des Agenten darstellen
//                row.createCell(2).setCellValue(cplex.getValue(x.get(a).get(t)));
//                row.createCell(4).setCellValue(currentDemand);  // Verwenden Sie den spezifischen Demand für die Periode
//                row.createCell(5).setCellValue(totalProductionValue);
//                row.createCell(6).setCellValue(demandDeviationValue);
//                row.createCell(7).setCellValue(productionCostValue);
//                row.createCell(8).setCellValue(standbyCostValue);
//                row.createCell(9).setCellValue(startupCostValue);
//                row.createCell(10).setCellValue(demandDeviationValue * params.demandDeviationCost);
//                row.createCell(11).setCellValue(agentProduction);
//
//                for (State s : State.values()) {
//                    double stateValue = Math.round(cplex.getValue(y.get(a).get(t).get(s)));
//                    if (stateValue == 1.0) {
//                        row.createCell(3).setCellValue(s.name());
//                        break;
//                    }
//                }
//            }
//        }
//
//        Row objectiveRow = resultSheet.createRow(rowIndex++);
//        objectiveRow.createCell(0).setCellValue("Objective Value");
//        objectiveRow.createCell(1).setCellValue(cplex.getObjValue());
//
//        File outputDir = new File("out");
//        if (!outputDir.exists()) {
//            outputDir.mkdirs();
//        }
//
//        String filePath = "out/OptimizationResults.xlsx";
//        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
//            resultWorkbook.write(fileOut);
//        }
//        resultWorkbook.close();
//
//        File file = new File(filePath);
//        if (Desktop.isDesktopSupported()) {
//            Desktop desktop = Desktop.getDesktop();
//            try {
//                desktop.open(file);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        } else {
//            System.out.println("Desktop is not supported. Please open the file manually: " + filePath);
//        }
//    }
//    
//    /**
//     * Load parameters from the Excel file.
//     */
//	private static Parameters loadParameters(Workbook workbook) {
//	    Sheet agentsSheet = workbook.getSheet("Agent");
//	    Sheet periodsSheet = workbook.getSheet("Periods");
//	    Sheet parametersSheet = workbook.getSheet("GlobalParameters");
//
//	    // Laden der Agenten-Parameter
//	    Map<Agent, Double> powerElectrolyzer = new HashMap<>();
//	    Map<Agent, Double> minOperation = new HashMap<>();
//	    Map<Agent, Double> maxOperation = new HashMap<>();
//	    Map<Agent, Double> slope = new HashMap<>();
//	    Map<Agent, Double> intercept = new HashMap<>();
//	    Map<Agent, Integer> startupDuration = new HashMap<>();
//	    Map<Agent, Double> startupCost = new HashMap<>();
//	    Map<Agent, Double> standbyCost = new HashMap<>();
//	    Set<Agent> agents = new HashSet<>();
//
//	    // Mindestverweildauer für Zustände (Idle, Starting, Production, Standby)
//	    Map<Agent, Map<State, Integer>> holdingDurations = new HashMap<>();
//
//	    // Lesen der Agenten-Daten aus der Agenten-Tabelle
//	    for (Row row : agentsSheet) {
//	        if (row.getRowNum() == 0)
//	            continue;
//	        
//	        Agent agent = new Agent((int) row.getCell(0).getNumericCellValue());  // Column A
//	        powerElectrolyzer.put(agent, row.getCell(1).getNumericCellValue());   // Column B
//	        minOperation.put(agent, row.getCell(2).getNumericCellValue());        // Column C
//	        maxOperation.put(agent, row.getCell(3).getNumericCellValue());        // Column D
//	        startupCost.put(agent, row.getCell(4).getNumericCellValue());         // Column E
//	        standbyCost.put(agent, row.getCell(5).getNumericCellValue());         // Column F
//	        slope.put(agent, row.getCell(6).getNumericCellValue());               // Column G
//	        intercept.put(agent, row.getCell(7).getNumericCellValue());           // Column H
//	        
//	        // Laden der Haltedauern (Idle, Starting, Production, Standby)
//	        Map<State, Integer> agentHoldingDurations = new HashMap<>();
//	        agentHoldingDurations.put(State.IDLE, (int) row.getCell(8).getNumericCellValue());       // Column I
//	        agentHoldingDurations.put(State.STARTING, (int) row.getCell(9).getNumericCellValue());   // Column J
//	        agentHoldingDurations.put(State.PRODUCTION, (int) row.getCell(10).getNumericCellValue()); // Column K
//	        agentHoldingDurations.put(State.STANDBY, (int) row.getCell(11).getNumericCellValue());   // Column L
//
//	        // Haltedauern in die Map einfügen
//	        holdingDurations.put(agent, agentHoldingDurations);
//	        agents.add(agent);
//	    }
//
//	    // Laden der periodenspezifischen Parameter, einschließlich Demand
//	    Map<Period, Double> electricityPrice = new HashMap<>();
//	    Map<Period, Double> availablePower = new HashMap<>();
//	    Map<Period, Double> periodDemand = new HashMap<>(); // Neu: Demand pro Periode
//	    Set<Period> periods = new HashSet<>();
//
//	    // Lesen der Perioden-Daten aus der Perioden-Tabelle
//	    for (Row row : periodsSheet) {
//	        if (row.getRowNum() == 0)
//	            continue;
//	        Period period = new Period((int) row.getCell(0).getNumericCellValue()); // Column A
//	        electricityPrice.put(period, row.getCell(1).getNumericCellValue());		// Column B
//	        availablePower.put(period, row.getCell(2).getNumericCellValue());		// Column C
//	        periodDemand.put(period, row.getCell(3).getNumericCellValue()); 		// Column D
//	        periods.add(period);										
//	    }
//
//	    // Global Parameter
//	    double intervalLength = parametersSheet.getRow(1).getCell(1).getNumericCellValue();
//	    double demandDeviationCost = parametersSheet.getRow(2).getCell(1).getNumericCellValue();
//
//	    // Rückgabe der Parameter einschließlich des periodenspezifischen Demand und der Haltedauern
//	    return new Parameters(startupCost, standbyCost, powerElectrolyzer, electricityPrice, minOperation, maxOperation, periodDemand, 
//	            slope, intercept, availablePower, intervalLength, startupDuration, demandDeviationCost, 
//	      holdingDurations);
//	}
//
//
//    /**
//     * Configure the CPLEX solver.
//     */
//    private static void configureCplex(IloCplex cplex) throws IloException {
//        cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0.000000001);
//        cplex.setParam(IloCplex.Param.TimeLimit, 600); // 10-minute time limit
//    }
//
//}
//
