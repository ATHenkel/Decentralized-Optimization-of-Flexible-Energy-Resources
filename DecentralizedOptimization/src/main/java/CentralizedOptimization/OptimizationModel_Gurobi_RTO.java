	package CentralizedOptimization;
	
	import org.apache.poi.ss.usermodel.*;
	
	import org.apache.poi.xssf.usermodel.XSSFWorkbook;
	
	import java.io.FileInputStream;
	import java.io.FileOutputStream;
	import java.io.IOException;
	import java.util.*;
	import com.gurobi.gurobi.*;
	
	public class OptimizationModel_Gurobi_RTO {
	
		public static void main(String[] args) {
		    try {
		        // Initialisierung
		        String excelFilePath = System.getenv("EXCEL_FILE_PATH");
		        if (excelFilePath == null || excelFilePath.isEmpty()) {
		            excelFilePath = "in/InputData_Short.xlsx"; 
		        }

		        FileInputStream excelFile = new FileInputStream(excelFilePath);
		        Workbook workbook = new XSSFWorkbook(excelFile);
		        Parameters params = loadParameters(workbook);
		        workbook.close();

		        Set<Agent> agents = params.startupCost.keySet();
		        Set<Period> periods = params.periods;

		        // Ergebnisse speichern
		        params.swoGridEnergyResults = new HashMap<>();
		        params.fluctuatingRenewableEnergy = initializeFluctuatingEnergy(params);

		        // Initialisiere Maps für SWO Ergebnisse
		        params.swoXVarResults = new HashMap<>();
		        params.swoYVarResults = new HashMap<>();

		        // 1. SWO-Optimierung
		        for (Period currentPeriod : periods) {
		            System.out.println("Starte SWO-Optimierung für Periode ab: " + currentPeriod.t);

		            GRBModel swoModel = new GRBModel(new GRBEnv("swo_" + currentPeriod.t + ".log"));
		            Map<Agent, Map<Period, GRBVar>> swoXVar = new HashMap<>();
		            Map<Agent, Map<Period, Map<State, GRBVar>>> swoYVar = new HashMap<>();

		            // Definiere SWO-Modell
		            defineSWOModel(swoModel, agents, periods, params, swoXVar, swoYVar, currentPeriod);
		            swoModel.optimize();
		            
		            swoModel.write("swo_model.lp");
		            
			        if (swoModel.get(GRB.IntAttr.Status) == GRB.INFEASIBLE) {
			            System.out.println("Model is infeasible. Computing IIS...");
			            swoModel.computeIIS();

			            for (GRBConstr constr : swoModel.getConstrs()) {
			                if (constr.get(GRB.IntAttr.IISConstr) == 1) {
			                    System.out.println("Infeasible constraint: " + constr.get(GRB.StringAttr.ConstrName));
			                }
			            }
			        }

		            // Exportiere SWO-Ergebnisse mit der Methode exportFinalIterationResultsToExcelSWO
		            String swoFilename = "SWO_Period_" + currentPeriod.t + ".xlsx";
		            exportFinalIterationResultsToExcelSWO(
		                agents, 
		                periods,
		                params, 
		                swoFilename, 
		                swoXVar, 
		                swoYVar, 
		                swoModel, 
		                0 // Berechnungszeit (falls erforderlich, hier als Platzhalter)
		            );
		            
		            // Ergebnisse der SWO in Map speichern (x-Variable)
		            for (Agent agent : agents) {
		                for (Period period : periods) {
		                    if (swoXVar.containsKey(agent) && swoXVar.get(agent).containsKey(period)) {
		                        GRBVar xSWO = swoXVar.get(agent).get(period);
		                        if (xSWO != null) {
		                            double xSWOValue = xSWO.get(GRB.DoubleAttr.X); // Optimiertes Ergebnis
		                            params.swoXVarResults
		                                .computeIfAbsent(period, p -> new HashMap<>()) // Initialisiere Map<Period, Map<Agent, Double>>
		                                .put(agent, xSWOValue); // Speichern
		                        }
		                    }
		                }
		            }

		            // Ergebnisse der SWO in Map speichern (y-Variable)
		            for (Agent agent : agents) {
		                for (Period period : periods) {
		                    // Initialisierung der Map für swoYVarResults
		                    params.swoYVarResults
		                        .computeIfAbsent(period, p -> new HashMap<>())
		                        .computeIfAbsent(agent, a -> new HashMap<>()); // Map<Agent, Map<State, Double>>

		                    for (State state : State.values()) {
		                        if (swoYVar.containsKey(agent) && swoYVar.get(agent).containsKey(period)) {
		                            GRBVar currentYVarSWO = swoYVar.get(agent).get(period).get(state);
		                            if (currentYVarSWO != null) {
		                                double value = currentYVarSWO.get(GRB.DoubleAttr.X); // Optimiertes Ergebnis
		                                params.swoYVarResults
		                                    .computeIfAbsent(period, p -> new HashMap<>()) // Initialisiere Map<Period, Map<Agent, Map<State, Double>>>
		                                    .computeIfAbsent(agent, a -> new HashMap<>())
		                                    .put(state, value); // Speichern
		                            }
		                        }
		                    }
		                }
		            }
		            
		         // Berechne die Netzenergie für die SWO-Periode
		            double gridEnergy = 0.0;  // Variable zur Speicherung der Netzenergie für die Periode

		            double renewableEnergyForCurrentPeriod = params.renewableEnergy.get(currentPeriod);

		            // Berechne die Energie der Elektrolyseure für diese Periode
		            double totalElectrolyzerEnergy = 0.0;
		            for (Agent a : agents) {
		                // Hole den Arbeitspunkt (x-Wert) für den Elektrolyseur des Agenten in der aktuellen Periode
		                double xSWOValue = 0.0;
		                if (params.swoXVarResults.containsKey(currentPeriod) && params.swoXVarResults.get(currentPeriod).containsKey(a)) {
		                    xSWOValue = params.swoXVarResults.get(currentPeriod).get(a); // Der Arbeitspunkt für diesen Agenten
		                }

		                // Berechne die Energie des Elektrolyseurs für diesen Agenten in der aktuellen Periode
		                double electrolyzerPower = params.powerElectrolyzer.get(a);  // Leistung des Elektrolyseurs
		                double electrolyzerEnergy = xSWOValue * electrolyzerPower * params.intervalLengthSWO;  // Energie der Elektrolyseure

		                // Summiere die Energie der Elektrolyseure für alle Agenten in der Periode
		                totalElectrolyzerEnergy += electrolyzerEnergy;
		            }
		            
		            // Netzenergie = Fluktuierende erneuerbare Energie - Energie der Elektrolyseure
		            gridEnergy =  totalElectrolyzerEnergy - renewableEnergyForCurrentPeriod;

		            // Speichern der berechneten Netzenergie für die Periode in swoGridEnergyResults
		            params.swoGridEnergyResults.put(currentPeriod, gridEnergy);

		            swoModel.dispose();
		        }

		     // 2. RTO-Optimierung
		        for (Period currentPeriod : periods) {
		            System.out.println("Starte RTO-Optimierung für Periode: " + currentPeriod.t);
		            GRBModel rtoModel = new GRBModel(new GRBEnv("rto_" + currentPeriod.t + ".log"));
		            Map<Agent, Map<Period, Map<Integer, GRBVar>>> rtoXVar = new HashMap<>();
		            Map<Agent, Map<Period, Map<State, GRBVar>>> rtoYVar = new HashMap<>();

		            // Definiere RTO-Modell
		            defineRTOModel(
		                rtoModel,
		                agents,
		                currentPeriod,
		                params,
		                params.swoGridEnergyResults, 
		                params.fluctuatingRenewableEnergy, 
		                rtoXVar,
		                rtoYVar
		            );
		            
		            rtoModel.optimize();
		            
			        if (rtoModel.get(GRB.IntAttr.Status) == GRB.INFEASIBLE) {
			            System.out.println("Model is infeasible. Computing IIS...");
			            rtoModel.computeIIS();

			            for (GRBConstr constr : rtoModel.getConstrs()) {
			                if (constr.get(GRB.IntAttr.IISConstr) == 1) {
			                    System.out.println("Infeasible constraint: " + constr.get(GRB.StringAttr.ConstrName));
			                }
			            }
			        }

		            // Schreibe die LP-Datei für den aktuellen RTO-Optimierungsdurchlauf
		            String lpFilePath = "RTO_Period_" + currentPeriod.t + ".lp";
		            rtoModel.write(lpFilePath);
		            
		            // Exportiere RTO-Ergebnisse mit der Methode exportFinalIterationResultsToExcelRTO
		            String rtoFilename = "RTO_Period_" + currentPeriod.t + ".xlsx";
		            exportFinalIterationResultsToExcelRTO(
		                agents, 
		                currentPeriod, 
		                params, 
		                rtoFilename, 
		                rtoXVar, 
		                rtoYVar, 
		                rtoModel, 
		                0, // Berechnungszeit 
		                params.fluctuatingRenewableEnergy 
		            );

		            rtoModel.dispose();
		        }

		        System.out.println("Alle Optimierungen abgeschlossen.");
		    } catch (GRBException | IOException e) {
		        e.printStackTrace();
		    }
		}
		
		private static Map<Period, Map<Integer, Double>> initializeFluctuatingEnergy(Parameters params) {
		    Map<Period, Map<Integer, Double>> fluctuatingEnergy = new HashMap<>();

		    for (Period period : params.periods) {
		        Map<Integer, Double> energyForPeriod = new HashMap<>();
		        for (int t = 1; t <= params.rtoStepsPerSWOStep; t++) {
		            double baseEnergy = params.renewableEnergy.getOrDefault(period, 0.0) / params.rtoStepsPerSWOStep; //Um die Energie auf die RTO-Perioden zu verteilen 
		            double fluctuation = baseEnergy * (0.2 * (Math.random() - 0.5)); // ±20%
		            energyForPeriod.put(t, baseEnergy + fluctuation);
		        }
		        System.out.println("EnergyForPeriod: " + energyForPeriod);
		        fluctuatingEnergy.put(period, energyForPeriod);
		    }
		    return fluctuatingEnergy;
		}
		
		static void exportResultsToExcel(GRBModel model, String filename) throws IOException, GRBException {
		    Workbook workbook = new XSSFWorkbook();
		    Sheet sheet = workbook.createSheet("Results");

		    int rowIndex = 0;
		    for (GRBVar var : model.getVars()) {
		        Row row = sheet.createRow(rowIndex++);
		        row.createCell(0).setCellValue(var.get(GRB.StringAttr.VarName));
		        row.createCell(1).setCellValue(var.get(GRB.DoubleAttr.X));
		    }

		    try (FileOutputStream fos = new FileOutputStream(filename)) {
		        workbook.write(fos);
		    }

		    workbook.close();
		    System.out.println("Results written to: " + filename);
		}
		
	    // Define SWO Model
		private static void defineSWOModel(
			    GRBModel swoModel,
			    Set<Agent> agents,
			    Set<Period> periods,
			    Parameters params,
			    Map<Agent, Map<Period, GRBVar>> xVar,
			    Map<Agent, Map<Period, Map<State, GRBVar>>> yVar,
			    Period currentPeriod  // Die aktuelle Periode, für die die Optimierung durchgeführt wird
			) throws GRBException {

			    // Entscheidungsvariablen definieren
			    for (Agent agent : agents) {
			        xVar.put(agent, new HashMap<>());
			        yVar.put(agent, new HashMap<>());

			        // Iteriere über alle Perioden
			        for (Period period : periods) {

			            // Kontinuierliche Variable für die Auslastung (x-Variable)
			            GRBVar x = swoModel.addVar(0, 1, 0, GRB.CONTINUOUS, "x_" + agent.a + "_" + period.t);
			            xVar.get(agent).put(period, x);

			            // Fixiere die x-Variablen aus den vergangenen Perioden (wenn notwendig)
			            if (period.t < currentPeriod.t) {  // Fixiere nur für vergangene Perioden
			            	Map<Agent, Double> agentMap = params.swoXVarResults.getOrDefault(period, Collections.emptyMap());
			            	double prevXValue = agentMap.getOrDefault(agent, 0.0);

			                // Setze die x-Variable als konstant für die vergangene Periode
			                GRBLinExpr fixXExpr = new GRBLinExpr();
			                fixXExpr.addTerm(1.0, x);
			                swoModel.addConstr(fixXExpr, GRB.EQUAL, prevXValue, 
			                    "fixX_" + agent.a + "_" + period.t);
			            }
			            
			            // Binäre Variablen für Zustände (y-Variablen)
			            yVar.get(agent).put(period, new HashMap<>());
			            for (State state : State.values()) {
			                GRBVar y = swoModel.addVar(0, 1, 0, GRB.BINARY, "y_" + agent.a + "_" + period.t + "_" + state);
			                yVar.get(agent).get(period).put(state, y);

			                // Fixiere die y-Variablen aus den vergangenen Perioden (wenn notwendig)
			                if (period.t < currentPeriod.t) {  // Fixiere nur für vergangene Perioden
			                	double prevYValue = params.swoYVarResults
			                		    .getOrDefault(period, Collections.emptyMap()) // Zugriff mit dem Period-Objekt
			                		    .getOrDefault(agent, Collections.emptyMap())  // Zugriff mit dem Agent-Objekt
			                		    .getOrDefault(state, 0.0);                    // Zugriff mit dem State-Objekt

			                    // Setze die y-Variable als konstant für die vergangene Periode
			                    GRBLinExpr fixYExpr = new GRBLinExpr();
			                    fixYExpr.addTerm(1.0, y);
			                    swoModel.addConstr(fixYExpr, GRB.EQUAL, prevYValue, 
			                        "fixY_" + agent.a + "_" + period.t + "_" + state);
			                }
			            }
			        }
			    }

			    // Zielfunktion definieren
			    GRBLinExpr objective = new GRBLinExpr();
			    defineSWOObjectiveFunction(swoModel, params, agents, periods, xVar, yVar, objective);
			    swoModel.setObjective(objective, GRB.MINIMIZE);

			    // Nebenbedingungen definieren
			    defineSWOConstraints(swoModel, params, agents, periods, xVar, yVar);
			}
	    
			private static void defineSWOObjectiveFunction(GRBModel model, Parameters params, Set<Agent> agents,
				Set<Period> periods, Map<Agent, Map<Period, GRBVar>> x, Map<Agent, Map<Period, Map<State, GRBVar>>> y,
				GRBLinExpr objective) throws GRBException {
	
			// Stromkosten berechnen
			GRBLinExpr electricityCostExpr = new GRBLinExpr();
			for (Period t : periods) {
			    double electricityCost = params.electricityCost.get(t);
			    double intervalLengthSWO = params.intervalLengthSWO;
			    double renewableEnergy = params.renewableEnergy.getOrDefault(t, 0.0);

			    GRBLinExpr productionExpr = new GRBLinExpr();
			    for (Agent a : agents) {
			        double powerElectrolyzer = params.powerElectrolyzer.get(a);
			        productionExpr.addTerm(powerElectrolyzer*intervalLengthSWO, x.get(a).get(t));
			    }

			    // Subtrahiere erneuerbare Energien
			    productionExpr.addConstant(-renewableEnergy);

			    // Skalar auf die Terme in productionExpr anwenden
			    for (int i = 0; i < productionExpr.size(); i++) {
			        electricityCostExpr.addTerm(
			            electricityCost * productionExpr.getCoeff(i),
			            productionExpr.getVar(i)
			        );
			    }

			    // Konstante hinzufügen (falls applicable)
			    electricityCostExpr.addConstant(productionExpr.getConstant()  * electricityCost);
			}
			
			
			
			
			objective.add(electricityCostExpr);
	
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
			    positiveDeviations.put(t, model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "positiveDeviation_period_" + t.getT()));
			    negativeDeviations.put(t, model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "negativeDeviation_period_" + t.getT()));
	
			    // Get the demand and renewable energy for the current period
			    double periodDemand = demandForPeriods.get(t);
	
			    // Calculate total production for the current period
			    GRBLinExpr productionInPeriod = new GRBLinExpr();
			    for (Agent a : agents) {
			        double intercept = params.intercept.get(a);
			        double powerElectrolyzer = params.powerElectrolyzer.get(a);
			        double intervalLengthSWO = params.intervalLengthSWO;
			        GRBVar productionStateVar = y.get(a).get(t).get(State.PRODUCTION);
	
			        // Berechnung: intervalLength * (powerElectrolyzer * slope * xVars + intercept)
			        productionInPeriod.addTerm(powerElectrolyzer * intervalLengthSWO, x.get(a).get(t));
			        productionInPeriod.addTerm(intercept * intervalLengthSWO, productionStateVar);
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
		
		private static void defineSWOConstraints(GRBModel model, Parameters params, Set<Agent> agents, Set<Period> periods,
				Map<Agent, Map<Period, GRBVar>> x, Map<Agent, Map<Period, Map<State, GRBVar>>> y) throws GRBException {
			
			// Initial state
			for (Agent a : agents) {
				Period firstPeriod = new Period(1);
				// Prüfen, ob y.get(a) und y.get(a).get(firstPeriod) null sind
				if (y.get(a) == null) {
					System.out.println("y.get(a) is null for Agent: " + a.a);
				} else if (y.get(a).get(firstPeriod) == null) {
					System.out.println("y.get(a).get(firstPeriod) is null for Agent: " + a.a + ", Period: " + firstPeriod.t);
				} else {
					model.addConstr(y.get(a).get(firstPeriod).get(State.IDLE), GRB.EQUAL, 1.0,
						"initialState_" + a.a + "_" + firstPeriod.t);
				}
			}
	
			// Operational limits (Constraint 1)
			for (Agent a : agents) {
				for (Period t : periods) {
					// Min Operation Constraint
					if (y.get(a) == null || y.get(a).get(t) == null || y.get(a).get(t).get(State.PRODUCTION) == null) {
						System.out.println("Null value found for minOperation: Agent: " + a.a + ", Period: " + t.t);
					} else {
						GRBLinExpr minOperationExpr = new GRBLinExpr();
						minOperationExpr.addTerm(params.minOperation.get(a), y.get(a).get(t).get(State.PRODUCTION));
						model.addConstr(x.get(a).get(t), GRB.GREATER_EQUAL, minOperationExpr,
							"minOperation_" + a.a + "_" + t.t);
					}
	
					// Max Operation Constraint
					if (y.get(a) == null || y.get(a).get(t) == null || y.get(a).get(t).get(State.PRODUCTION) == null) {
						System.out.println("Null value found for maxOperation: Agent: " + a.a + ", Period: " + t.t);
					} else {
						GRBLinExpr maxOperationExpr = new GRBLinExpr();
						maxOperationExpr.addTerm(params.maxOperation.get(a), y.get(a).get(t).get(State.PRODUCTION));
						model.addConstr(x.get(a).get(t), GRB.LESS_EQUAL, maxOperationExpr, "maxOperation_" + a.a + "_" + t.t);
					}
				}
			}
	
			// State exclusivity (Constraint 2)
			for (Agent a : agents) {
				for (Period t : periods) {
					if (y.get(a) == null || y.get(a).get(t) == null) {
						System.out.println("Null value found for stateExclusivity: Agent: " + a.a + ", Period: " + t.t);
					} else {
						GRBLinExpr statusSum = new GRBLinExpr();
						for (State s : State.values()) {
							if (y.get(a).get(t).get(s) == null) {
								System.out.println("Null value found for state " + s + ": Agent: " + a.a + ", Period: " + t.t);
							} else {
								statusSum.addTerm(1.0, y.get(a).get(t).get(s));
							}
						}
						model.addConstr(statusSum, GRB.EQUAL, 1.0, "stateExclusivity_" + a.a + "_" + t.t);
					}
				}
			}
	
			// State transitions (Constraint 4)
			for (Agent a : agents) {
				for (Period t : periods) {
					if (t.t > 1) {
						Period prevPeriod = new Period(t.t - 1);
						int startingHoldingDuration = params.holdingDurations.get(a).get(State.STARTING);
						Period startupPeriod = new Period(t.getT() - startingHoldingDuration);
	
						// Überprüfen, ob prevPeriod und startupPeriod null sind
						if (y.get(a) == null || y.get(a).get(prevPeriod) == null || y.get(a).get(startupPeriod) == null) {
							System.out.println("Null value found for state transition: Agent: " + a.a + ", prevPeriod: " + prevPeriod.t + ", startupPeriod: " + startupPeriod.t);
						} else {
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
										// Überprüfen, ob prevPeriod und futurePeriod null sind
										if (y.get(a) == null || y.get(a).get(prevPeriod) == null || y.get(a).get(futurePeriod) == null) {
											System.out.println("Null value found for minDwellTime: Agent: " + a.a + ", prevPeriod: " + prevPeriod.t + ", futurePeriod: " + futurePeriod.t);
										} else {
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
		}
		
		private static void defineRTOModel(
			    GRBModel rtoModel,
			    Set<Agent> agents,
			    Period currentSWOPeriod, // Die aktuelle SWO-Periode
			    Parameters params,
			    Map<Period, Double> netEnergyResults,
			    Map<Period, Map<Integer, Double>> fluctuatingRenewableEnergy,
			    Map<Agent, Map<Period, Map<Integer, GRBVar>>> xVar, // Variablen für RTO-Schritte
			    Map<Agent, Map<Period, Map<State, GRBVar>>> yVar // Variablen für RTO-Zustände
			) throws GRBException {

			    GRBLinExpr objective = new GRBLinExpr(); // Zielwertfunktion

			    // Variablen und Nebenbedingungen nur für die aktuelle SWO-Periode definieren
			    for (Agent agent : agents) {
			        xVar.putIfAbsent(agent, new HashMap<>());
			        yVar.putIfAbsent(agent, new HashMap<>());

			        xVar.get(agent).putIfAbsent(currentSWOPeriod, new HashMap<>());
			        yVar.get(agent).putIfAbsent(currentSWOPeriod, new HashMap<>());

			        // Variablen für RTO-Schritte definieren
			        for (int t = 1; t <= params.rtoStepsPerSWOStep; t++) {
			            if (!xVar.get(agent).get(currentSWOPeriod).containsKey(t)) {
			                GRBVar xRTO = rtoModel.addVar(0, 1, 0, GRB.CONTINUOUS,
			                    "x_" + agent.a + "_" + currentSWOPeriod.t + "_rto_t" + t);
			                xVar.get(agent).get(currentSWOPeriod).put(t, xRTO);

			                // Optional: Initiale Bedingungen für den ersten Zeitschritt setzen
			                if (t == 1) {
			                    double swoValue = params.swoXVarResults
			                        .getOrDefault(currentSWOPeriod, Collections.emptyMap())
			                        .getOrDefault(agent, 0.0);

//			                    GRBLinExpr initExpr = new GRBLinExpr();
//			                    initExpr.addTerm(1.0, xRTO);
//			                    rtoModel.addConstr(initExpr, GRB.EQUAL, swoValue,
//			                        "initRTO_" + agent.a + "_" + currentSWOPeriod.t + "_rto_t1");
			                }
			            }
			        }

			        // Binäre Variablen für Zustände definieren
			        for (State state : State.values()) {
			            if (!yVar.get(agent).get(currentSWOPeriod).containsKey(state)) {
			                GRBVar y = rtoModel.addVar(0, 1, 0, GRB.BINARY,
			                    "y_" + agent.a + "_" + currentSWOPeriod.t + "_" + state);
			                yVar.get(agent).get(currentSWOPeriod).put(state, y);

			                // Initialisierung der binären Variablen für den ersten Zeitschritt (t == 1)
			                if (params.rtoStepsPerSWOStep > 0) { // Sicherstellen, dass es t = 1 gibt
			                    double swoYValue = params.swoYVarResults
			                        .getOrDefault(currentSWOPeriod, Collections.emptyMap())
			                        .getOrDefault(agent, Collections.emptyMap())
			                        .getOrDefault(state, 0.0);

			                    GRBLinExpr initYExpr = new GRBLinExpr();
			                    initYExpr.addTerm(1.0, y);
			                    rtoModel.addConstr(initYExpr, GRB.EQUAL, swoYValue,
			                        "initY_" + agent.a + "_" + currentSWOPeriod.t + "_" + state);
			                }
			            } else {
			                System.out.println("WARNUNG: y-Variable für Agent " + agent.a + ", Periode " +
			                    currentSWOPeriod.t + ", Zustand " + state + " existiert bereits!");
			            }
			        }
			    }

			    // Zielwertfunktion definieren
			    defineRTOObjectiveFunction(
			        rtoModel,
			        params,
			        agents,
			        currentSWOPeriod,
			        xVar,
			        objective
			    );

			    // Nebenbedingungen definieren
			    defineRTOConstraints(
			        rtoModel,
			        params,
			        agents,
			        currentSWOPeriod,
			        netEnergyResults,
			        fluctuatingRenewableEnergy,
			        xVar,
			        yVar
			    );

			    // Zielwertfunktion setzen
			    rtoModel.setObjective(objective, GRB.MAXIMIZE);
			}

		private static void defineRTOObjectiveFunction(
		        GRBModel model,
		        Parameters params,
		        Set<Agent> agents,
		        Period currentSWOPeriod, 
		        Map<Agent, Map<Period, Map<Integer, GRBVar>>> xVar, 
		        GRBLinExpr objective
		) throws GRBException {

		    // Wasserstoffproduktion maximieren
		    GRBLinExpr hydrogenProductionExpr = new GRBLinExpr();

		    // Iteriere über alle Agenten und RTO-Schritte der aktuellen SWO-Periode
		    double intervalLengthRTO = params.intervalLengthSWO / params.rtoStepsPerSWOStep;

		    for (Agent a : agents) {
		        // Überprüfe, ob der Agent im Parameter enthalten ist
		        if (!params.powerElectrolyzer.containsKey(a)) {
		            System.out.println("WARNUNG: Agent " + a.a + " hat keine Stromverbrauchsdaten.");
		        }
		        double powerElectrolyzer = params.powerElectrolyzer.get(a);
		        double efficiency = params.slope.get(a);

		        // Überprüfe, ob für diesen Agenten und diese Periode die RTO-Variablen existieren
		        if (!xVar.containsKey(a)) {
		            System.out.println("WARNUNG: Keine RTO-Variablen für Agent " + a.a);
		        }

		        // Iteriere über die Zeitsteps
		        for (int t = 1; t <= params.rtoStepsPerSWOStep; t++) { 
		            // Debug-Ausgabe: Überprüfe, ob xVar für den Agenten, die Periode und den Schritt vorhanden ist
		            if (!xVar.get(a).containsKey(currentSWOPeriod)) {
		                System.out.println("WARNUNG: Keine Variablen für Agent " + a.a + " in der Periode " + currentSWOPeriod.t);
		            }
		            if (!xVar.get(a).get(currentSWOPeriod).containsKey(t)) {
		                System.out.println("WARNUNG: Keine RTO-Variable für Agent " + a.a + ", Periode " + currentSWOPeriod.t + ", Zeitschritt " + t);
		            }
		            
		            // Zugriff auf die RTO-Variable
		            GRBVar xRTO = xVar.get(a).get(currentSWOPeriod).get(t); 

		            // Falls der Wert null ist, eine Fehlerausgabe und Fortsetzung des Programms
		            if (xRTO == null) {
		                System.out.println("ERROR: Null-Referenz für RTO-Variable bei Agent " + a.a + ", Periode " + currentSWOPeriod.t + ", Zeitschritt " + t);
		                continue; // Überspringe diesen Schritt
		            }

		            // Füge den Term für die Wasserstoffproduktion hinzu
		            hydrogenProductionExpr.addTerm(powerElectrolyzer * efficiency * intervalLengthRTO, xRTO);
		        }
		    }

		    // Setze die Zielfunktion auf Maximierung der Wasserstoffproduktion
		    objective.add(hydrogenProductionExpr);
		    model.setObjective(objective, GRB.MAXIMIZE);
		}
	
	    private static void defineRTOConstraints(
	            GRBModel model,
	            Parameters params,
	            Set<Agent> agents,
	            Period currentSWOPeriod, // Nur die aktuelle SWO-Periode
	            Map<Period, Double> netEnergyResults,
	            Map<Period, Map<Integer, Double>> fluctuatingRenewableEnergy,
	            Map<Agent, Map<Period, Map<Integer, GRBVar>>> x,
	            Map<Agent, Map<Period, Map<State, GRBVar>>> y
	    ) throws GRBException {

	        double intervalLengthRTO = params.intervalLengthSWO / params.rtoStepsPerSWOStep;

	        // Operational limits (Constraint 1)
	        for (Agent a : agents) {
	            for (int t = 1; t <= params.rtoStepsPerSWOStep; t++) {
	                // Min Operation Constraint
	                GRBLinExpr minOperationExpr = new GRBLinExpr();
	                minOperationExpr.addTerm(params.minOperation.get(a), y.get(a).get(currentSWOPeriod).get(State.PRODUCTION));
	                model.addConstr(x.get(a).get(currentSWOPeriod).get(t), GRB.GREATER_EQUAL, minOperationExpr,
	                        "minOperation_" + a.a + "_" + currentSWOPeriod.t + "_t" + t);

	                // Max Operation Constraint
	                GRBLinExpr maxOperationExpr = new GRBLinExpr();
	                maxOperationExpr.addTerm(params.maxOperation.get(a), y.get(a).get(currentSWOPeriod).get(State.PRODUCTION));
	                model.addConstr(x.get(a).get(currentSWOPeriod).get(t), GRB.LESS_EQUAL, maxOperationExpr,
	                        "maxOperation_" + a.a + "_" + currentSWOPeriod.t + "_t" + t);
	            }
	        }

	     // State exclusivity (Constraint 2) für alle RTO-Zeitschritte
	        for (Agent a : agents) {
	            // Iteriere über alle RTO-Zeitschritte t (von 1 bis rtoStepsPerSWOStep)
	            for (int t = 1; t <= params.rtoStepsPerSWOStep; t++) {
	                GRBLinExpr statusSum = new GRBLinExpr();
	                
	                // Iteriere über alle Zustände und addiere die entsprechenden y-Variablen
	                for (State state : State.values()) {
	                    statusSum.addTerm(1.0, y.get(a).get(currentSWOPeriod).get(state)); 
	                }
	                
	                // Definiere die Constraint, dass die Summe der y-Variablen pro Periode gleich 1 ist
	                model.addConstr(statusSum, GRB.EQUAL, 1.0, "stateExclusivity_" + a.a + "_" + t);
	            }
	        }

	     // Energy balance (Constraint 3)
	        double netPowerFromGrid = netEnergyResults.getOrDefault(currentSWOPeriod, 0.0);

	        System.err.println("NetPowerfromGrid in SWO-Periode: " + currentSWOPeriod.t + " : " + netPowerFromGrid + " kWh");

	        double totalFluctuatingRenewable = 0.0;
	        GRBLinExpr totalEnergyExpr = new GRBLinExpr();

	        for (Agent a : agents) {
	            double powerElectrolyzer = params.powerElectrolyzer.get(a);
	            for (int t = 1; t <= params.rtoStepsPerSWOStep; t++) {
	                
	                GRBVar xRTO = x.get(a).get(currentSWOPeriod).get(t);
	                // Hinzufügen der Elektrolyseur-Leistung
	                totalEnergyExpr.addTerm(powerElectrolyzer * intervalLengthRTO, xRTO);
	            }
	        }
	        
	        for (int t = 1; t <= params.rtoStepsPerSWOStep; t++) {
	        	
	            // Fluktuierende erneuerbare Energie für diese Periode (wird jetzt nur einmal pro Periode berücksichtigt)
                double fluctuatingRenewable = params.fluctuatingRenewableEnergy
                        .get(currentSWOPeriod)
                        .get(t);
                
	            // Abziehen der fluktuierenden erneuerbaren Energie (diese wird nur einmal für jedes t abgezogen)
                totalEnergyExpr.addConstant(-fluctuatingRenewable);
                
                // Summe der fluktuierenden erneuerbaren Energie über alle Perioden
                totalFluctuatingRenewable += fluctuatingRenewable;
	        }

	        // Ausgabe der Summe der fluktuierenden erneuerbaren Energie (die nur einmal summiert wird)
	        System.out.println("Summe der fluktuierenden erneuerbaren Energie über alle RTO-Perioden: " + totalFluctuatingRenewable);

	        // Die Gleichung: Energie der Elektrolyseure - fluktuierende erneuerbare Energie = Netzenergiebedarf
	        model.addConstr(totalEnergyExpr, GRB.EQUAL, netPowerFromGrid, "energyBalance_period_" + currentSWOPeriod.t);
	    }

	    private static void exportFinalIterationResultsToExcelRTO(
	            Set<Agent> agents,
	            Period currentSWOPeriod, // Aktuelle SWO-Periode
	            Parameters params,
	            String filePath,
	            Map<Agent, Map<Period, Map<Integer, GRBVar>>> xVar,
	            Map<Agent, Map<Period, Map<State, GRBVar>>> yVar,
	            GRBModel model,
	            long computationTime,
	            Map<Period, Map<Integer, Double>> fluctuatingRenewableEnergyPerStep // Fluktuierende erneuerbare Energie
	    ) throws IOException, GRBException {

	        Workbook workbook = new XSSFWorkbook();
	        Sheet resultSheet = workbook.createSheet("Optimization Results");

	        // Header erstellen
	        Row headerRow = resultSheet.createRow(0);
	        headerRow.createCell(0).setCellValue("Period");
	        headerRow.createCell(1).setCellValue("Agent");
	        headerRow.createCell(2).setCellValue("RTO Step");
	        headerRow.createCell(3).setCellValue("X Value");
	        headerRow.createCell(4).setCellValue("Y State");
	        headerRow.createCell(5).setCellValue("Hydrogen Production");
	        headerRow.createCell(6).setCellValue("Period Demand");
	        headerRow.createCell(7).setCellValue("Renewable Energy");
	        headerRow.createCell(8).setCellValue("Fluctuating Renewable (per step)");
	        headerRow.createCell(9).setCellValue("Used Grid Energy RTO");
	        headerRow.createCell(10).setCellValue("Purchased Grid Energy SWO");

	        int rowIndex = 1;

	        // Period Demand und Renewable Energy für die aktuelle SWO-Periode abrufen
	        double periodDemand = params.demand.get(currentSWOPeriod);
	        double renewable = params.renewableEnergy.getOrDefault(currentSWOPeriod, 0.0);

	        // Map zur Speicherung der gesamten Netzenergie pro RTO-Periode
	        Map<Integer, Double> totalGridEnergyPerPeriod = new HashMap<>();

	        for (int t = 1; t <= params.rtoStepsPerSWOStep; t++) {
	            double totalElectrolyzerPower = 0.0;

	            // Über alle Agenten iterieren und benötigte Energie berechnen
	            for (Agent agent : agents) {
	                Row row = resultSheet.createRow(rowIndex++);

	                // Grundwerte eintragen
	                row.createCell(0).setCellValue(currentSWOPeriod.getT());
	                row.createCell(1).setCellValue(agent.a);
	                row.createCell(2).setCellValue(t); // RTO Step

	                // X Value abrufen
	                GRBVar xVariable = xVar.getOrDefault(agent, Collections.emptyMap())
	                                       .getOrDefault(currentSWOPeriod, Collections.emptyMap())
	                                       .get(t);
	                double xValue = (xVariable != null) ? xVariable.get(GRB.DoubleAttr.X) : 0.0;
	                row.createCell(3).setCellValue(xValue);

	                // Aktiven Zustand ermitteln
	                String activeState = "None";
	                for (State state : State.values()) {
	                    GRBVar yStateVar = yVar.getOrDefault(agent, Collections.emptyMap())
	                                           .getOrDefault(currentSWOPeriod, Collections.emptyMap())
	                                           .get(state);
	                    if (yStateVar != null && yStateVar.get(GRB.DoubleAttr.X) == 1.0) {
	                        activeState = state.name();
	                        break;
	                    }
	                }
	                row.createCell(4).setCellValue(activeState);

	                // Wasserstoffproduktion berechnen
	                GRBVar productionStateVar = yVar.getOrDefault(agent, Collections.emptyMap())
	                                                .getOrDefault(currentSWOPeriod, Collections.emptyMap())
	                                                .get(State.PRODUCTION);
	                double productionState = (productionStateVar != null) ? productionStateVar.get(GRB.DoubleAttr.X) : 0.0;
	                double slope = params.slope.get(agent);
	                double intercept = params.intercept.get(agent);
	                double powerElectrolyzer = params.powerElectrolyzer.get(agent);
	                double intervallLengthRTO = (params.intervalLengthSWO / params.rtoStepsPerSWOStep);

	                double hydrogenProduction = intervallLengthRTO * (powerElectrolyzer * slope * xValue + intercept * productionState);
	                row.createCell(5).setCellValue(hydrogenProduction);

	                // Period Demand eintragen
	                row.createCell(6).setCellValue(periodDemand);

	                // Erneuerbare Energie eintragen
	                row.createCell(7).setCellValue(renewable);

	                // Fluktuierende erneuerbare Energie eintragen (pro RTO-Schritt)
	                double fluctuatingRenewable = params.fluctuatingRenewableEnergy
	                        .get(currentSWOPeriod)
	                        .get(t);
	                row.createCell(8).setCellValue(fluctuatingRenewable);

	                // Elektrische Leistung des Elektrolyseurs berechnen
	                double electrolyzerPower = params.powerElectrolyzer.get(agent) * intervallLengthRTO * xValue;
	                totalElectrolyzerPower += electrolyzerPower;
	            }

	            // Gesamtenergiebedarf des Systems für diese RTO-Periode berechnen
	            double fluctuatingRenewable = fluctuatingRenewableEnergyPerStep.get(currentSWOPeriod).getOrDefault(t, 0.0);
	            double gridEnergyForPeriod = Math.max(totalElectrolyzerPower - fluctuatingRenewable, 0.0);

	            totalGridEnergyPerPeriod.put(t, gridEnergyForPeriod);

	            // Netzenergie in die Spalte "Used Grid Energy RTO" eintragen
	            Row gridEnergyRow = resultSheet.createRow(rowIndex++);
	            gridEnergyRow.createCell(9).setCellValue(gridEnergyForPeriod);
	            
	            // Eingekaufte Netzenergie für die SWO eintragen
	            double purchasedGridEnergySWO = params.swoGridEnergyResults.getOrDefault(currentSWOPeriod, 0.0);
	            gridEnergyRow.createCell(10).setCellValue(purchasedGridEnergySWO);
	        }

	        // Zusätzliche Informationen einfügen
	        Row computationRow = resultSheet.createRow(rowIndex++);
	        computationRow.createCell(0).setCellValue("Total Computation Time (ns)");
	        computationRow.createCell(1).setCellValue(computationTime);

	        Row objectiveRow = resultSheet.createRow(rowIndex++);
	        objectiveRow.createCell(0).setCellValue("Objective Value");
	        objectiveRow.createCell(1).setCellValue(model.get(GRB.DoubleAttr.ObjVal));

	        // Ergebnisse speichern
	        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
	            workbook.write(fileOut);
	        } finally {
	            workbook.close();
	        }

	        System.out.println("Results successfully written to Excel file: " + filePath);
	    }


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
		            renewableEnergy // Übergabe der erneuerbaren Energien
		    );
		}
		
		
		private static void exportFinalIterationResultsToExcelSWO(
		        Set<Agent> agents,
		        Set<Period> periods,
		        Parameters params,
		        String filePath,
		        Map<Agent, Map<Period, GRBVar>> xVar,
		        Map<Agent, Map<Period, Map<State, GRBVar>>> yVar,
		        GRBModel model,
		        long computationTime) throws IOException, GRBException {

		    Workbook workbook = new XSSFWorkbook();
		    Sheet resultSheet = workbook.createSheet("Optimization Results");

		    // Header erstellen
		    Row headerRow = resultSheet.createRow(0);
		    headerRow.createCell(0).setCellValue("Period");
		    headerRow.createCell(1).setCellValue("Agent");
		    headerRow.createCell(2).setCellValue("X Value");
		    headerRow.createCell(3).setCellValue("Y State");
		    headerRow.createCell(4).setCellValue("Hydrogen Production");
		    headerRow.createCell(5).setCellValue("Period Demand");

		    // Erneuerbare Energie nur hinzufügen, wenn verfügbar
		    boolean includeRenewable = params.renewableEnergy != null && !params.renewableEnergy.isEmpty();
		    if (includeRenewable) {
		        headerRow.createCell(6).setCellValue("Renewable Energy");
		    }

		    // Neue Spalte für die vom Netz einzukaufende Energie
		    headerRow.createCell(7).setCellValue("Net Energy Purchase");

		    int rowIndex = 1;
		    for (Period period : periods) {
		        double periodDemand = params.demand.get(period);
		        double renewable = includeRenewable ? params.renewableEnergy.getOrDefault(period, 0.0) : 0.0;

		        // Berechnung der gesamten Energie, die von allen Elektrolyseuren verbraucht wird
		        double totalElectrolyzerEnergy = 0.0;
		        for (Agent agent : agents) {
		            if (xVar.containsKey(agent) && xVar.get(agent).containsKey(period)) {
		                GRBVar xVariable = xVar.get(agent).get(period);
		                double xValue = (xVariable != null) ? xVariable.get(GRB.DoubleAttr.X) : 0.0;
		                double powerElectrolyzer = params.powerElectrolyzer.get(agent);
		                totalElectrolyzerEnergy += powerElectrolyzer * xValue * params.intervalLengthSWO;
		            }
		        }

		        // Berechnung der vom Netz einzukaufenden Energie
		        double netEnergyPurchase = totalElectrolyzerEnergy - renewable;
		        if (netEnergyPurchase < 0) {
		            netEnergyPurchase = 0.0; // Es kann nicht weniger als 0 Energie gekauft werden
		        }

		        for (Agent agent : agents) {
		            Row row = resultSheet.createRow(rowIndex++);

		            // Grundwerte eintragen
		            row.createCell(0).setCellValue(period.getT());
		            row.createCell(1).setCellValue(agent.a);
		            if (xVar.containsKey(agent) && xVar.get(agent).containsKey(period)) {
		                GRBVar xVariable = xVar.get(agent).get(period);
		                if (xVariable != null) {
		                    row.createCell(2).setCellValue(xVariable.get(GRB.DoubleAttr.X));
		                } else {
		                    row.createCell(2).setCellValue(0.0); // Standardwert für nicht definierte Variablen
		                }
		            } else {
		                row.createCell(2).setCellValue(0.0); // Standardwert für fehlende Einträge
		            }

		            // Aktiven Zustand ermitteln
		            String activeState = "None";
		            for (State state : State.values()) {
		                GRBVar yStateVar = yVar.get(agent).get(period).get(state);
		                if (yStateVar != null && yStateVar.get(GRB.DoubleAttr.X) == 1.0) {
		                    activeState = state.name();
		                    break;
		                }
		            }
		            row.createCell(3).setCellValue(activeState);

		            // Wasserstoffproduktion berechnen
		            double xValue = xVar.get(agent).get(period).get(GRB.DoubleAttr.X);
		            GRBVar productionStateVar = yVar.get(agent).get(period).get(State.PRODUCTION);
		            double productionState = (productionStateVar != null) ? productionStateVar.get(GRB.DoubleAttr.X) : 0.0;
		            double slope = params.slope.get(agent);
		            double intercept = params.intercept.get(agent);
		            double powerElectrolyzer = params.powerElectrolyzer.get(agent);
		            double intervalLength = params.intervalLengthSWO;

		            double hydrogenProduction = intervalLength * (powerElectrolyzer * slope * xValue + intercept * productionState);
		            row.createCell(4).setCellValue(hydrogenProduction);

		            // Period Demand eintragen
		            row.createCell(5).setCellValue(periodDemand);

		            // Erneuerbare Energie eintragen (falls verfügbar)
		            if (includeRenewable) {
		                row.createCell(6).setCellValue(renewable);
		            }

		            // Vom Netz einzukaufende Energie eintragen
		            row.createCell(7).setCellValue(netEnergyPurchase);
		        }
		    }

		    // Zusätzliche Informationen einfügen
		    Row computationRow = resultSheet.createRow(rowIndex++);
		    computationRow.createCell(0).setCellValue("Total Computation Time (ns)");
		    computationRow.createCell(1).setCellValue(computationTime);

		    Row objectiveRow = resultSheet.createRow(rowIndex++);
		    objectiveRow.createCell(0).setCellValue("Objective Value");
		    objectiveRow.createCell(1).setCellValue(model.get(GRB.DoubleAttr.ObjVal));

		    // Ergebnisse speichern
		    try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
		        workbook.write(fileOut);
		    } finally {
		        workbook.close();
		    }

		    System.out.println("Results successfully written to Excel file: " + filePath);
		}

	}
