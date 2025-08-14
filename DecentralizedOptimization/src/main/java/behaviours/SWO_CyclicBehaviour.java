package behaviours;

import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import com.gurobi.gurobi.GRBModel;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.lang.acl.ACLMessage;
import models.ADMMDataModel;
import models.Electrolyzer;
import models.Parameters;
import models.Period;
import models.State;

public class SWO_CyclicBehaviour extends CyclicBehaviour {

    private static final long serialVersionUID = 1L;
    private int receivedXMessages = 0;
    private int receivedDualMessages = 0;
    private int receivedConvergenceMessages = 0; // Counter for convergence messages
    private final int totalNumberADMMAgents; // Number of agents in the system
    private GRBModel model;
    private Parameters parameters;
    private ADMMDataModel dataModel;
    private Set<Electrolyzer> electrolyzers;
    private Set<Period> periods;
    private double rho;
    private int swoIterationCount = 0;
    private final int maxIterations; 
    private boolean isFirstXUpdateDone = false; 
    private int currentStartPeriod = 1;
    
    public SWO_CyclicBehaviour(int totalAgents, GRBModel model, Parameters parameters, ADMMDataModel admmDataModel, Set<Electrolyzer> electrolyzers, Set<Period> periods, double rho, int iteration, int maxIterations) {
        this.totalNumberADMMAgents = totalAgents;
        this.model = model;
        this.parameters = parameters;
        this.dataModel = admmDataModel;
        this.electrolyzers = electrolyzers;
        this.periods = periods;
        this.rho = rho;
        this.swoIterationCount = iteration;
        this.maxIterations = maxIterations;
    }

    @Override
    public void action() {
        // Initialization for the first iteration
        if (!isFirstXUpdateDone) {
        	dataModel.setStartComputationTime(System.nanoTime());
            executeSWO_XUpdate();
            isFirstXUpdateDone = true;
            return;
        }

        // Stop when maximum iterations are reached
        if (swoIterationCount >= maxIterations) {
            System.out.println("Maximum iteration count reached. Agent " + myAgent.getLocalName() + " terminates the ADMM cycle.");
            saveSWOResultsAndTerminate();
            return;
        }

        // Receive and process message
        ACLMessage msg = myAgent.receive();
        if (msg != null) {
            processSWOMessage(msg);
        } else {
            block(); 
        }
    }

    /**
     * Processes an incoming message based on its type.
     */
    private void processSWOMessage(ACLMessage msg) {
        String content = msg.getContent();

        if (content.startsWith("xUpdateMessage")) {
            handleSWO_XUpdateMessage(content);
            receivedXMessages++;
            checkSWO_XUpdateCompletion();
        } else if (content.startsWith("dualUpdateMessage")) {
            handleSWO_DualUpdateMessage(content);
            receivedDualMessages++;
            checkSWO_DualUpdateCompletion();
        } else if (content.equals("convergenceReached")) {
            handleSWO_ConvergenceMessage();
        }
    }

    /**
     * Checks if all X-update messages have been received.
     */
    private void checkSWO_XUpdateCompletion() {
        if (receivedXMessages == totalNumberADMMAgents - 1) {
            executeSWO_YSDualUpdates();
        }
    }

    /**
     * Prüft, ob alle Dual-Update-Nachrichten empfangen wurden.
     */
    private void checkSWO_DualUpdateCompletion() {
        if (receivedDualMessages == totalNumberADMMAgents - 1) {
        	dataModel.saveReceivedDualMessagesForIteration(swoIterationCount, receivedDualMessages);
        	receivedDualMessages = 0;
        	
        	        // Calculate residuals
            calculateBoundaryResiduals();

            // Check convergence
            if (checkFeasibility() && swoIterationCount > 0) {
            	savePurchasedGridEnergy();
            	sendConvergenceMessage();
                
            } else {
            	swoIterationCount++;
                executeSWO_XUpdate();
            }
        }
    }
    
    /**
     * Führt die Y-, S- und Dual-Updates aus.
     */
    private void executeSWO_YSDualUpdates() {
        SequentialBehaviour seq = new SequentialBehaviour();
        seq.addSubBehaviour(new SWO_YUpdateBehaviour(model, parameters, electrolyzers, periods, swoIterationCount, dataModel, rho, e -> electrolyzers.contains(e), currentStartPeriod));
        seq.addSubBehaviour(new SWO_SUpdateBehaviour(model, parameters, electrolyzers, periods, swoIterationCount, dataModel, rho, e -> electrolyzers.contains(e)));
        seq.addSubBehaviour(new SWO_DualUpdateBehaviour(parameters, electrolyzers, periods, swoIterationCount, dataModel,rho, e -> electrolyzers.contains(e)));
        myAgent.addBehaviour(seq);
    }


    /**
     * Führt das X-Update aus.
     */
    private void executeSWO_XUpdate() {
        Set<Period> filteredPeriods = dataModel.getAssignedPeriods();
        myAgent.addBehaviour(new SWO_XUpdateBehaviour(model, parameters, parameters.getElectrolyzers(), filteredPeriods, swoIterationCount, dataModel, rho, currentStartPeriod));
        receivedXMessages = 0; 
    }

    /**
     * Verarbeitet Konvergenznachrichten.
     */
    private void handleSWO_ConvergenceMessage() {
        receivedConvergenceMessages++;
        if (receivedConvergenceMessages == totalNumberADMMAgents - 1) {
        	
           	        // Save Results and Terminate SWO Optimization
            saveSWOResultsAndTerminate();
        	
        }
    }
    
    // Method for parsing the X update message and saving the details
    private void handleSWO_XUpdateMessage(String content) {
        String[] parts = content.split(";");
        StringBuilder output = new StringBuilder("Gebündelte X-Werte und Wasserstoffproduktion aus x-Update:\n");

        if (parts.length >= 2) {
            int iteration = Integer.parseInt(parts[1]);
            output.append("Iteration: ").append(iteration).append("\n");
            
            if (iteration != swoIterationCount) {
                System.err.println("Falsche Iteration SWO!!");
            }

            for (int i = 2; i < parts.length; i++) {
                String[] resultParts = parts[i].split(",");
                if (resultParts.length == 4) {
                    int electrolyzerID = Integer.parseInt(resultParts[0]) - 1;
                    int periodIndex = Integer.parseInt(resultParts[1]) - 1;
                    double xValue = Double.parseDouble(resultParts[2]);
                    double hydrogenProduction = Double.parseDouble(resultParts[3]);

                    output.append("Agent: ").append(myAgent.getLocalName())
                          .append(", Electrolyzer ID: ").append(electrolyzerID + 1)
                          .append(", Periode: ").append(periodIndex + 1)
                          .append(", X-Wert: ").append(xValue)
                          .append(", Wasserstoffproduktion: ").append(hydrogenProduction)
                          .append("\n");

                    // Save the values in the ADMM data model
                    dataModel.saveXSWOValueForPeriod(iteration + 1, electrolyzerID, periodIndex, xValue);
                    dataModel.saveHydrogenSWOProductionForPeriod(iteration+1, electrolyzerID, periodIndex, hydrogenProduction);
                }
            }
        }
    }
    
    private void savePurchasedGridEnergy() {
        for (Period period : periods) {
            double totalElectrolyzerEnergy = 0.0;  // <-- Variable is reset for each period!

            double renewableEnergyForCurrentPeriod = parameters.renewableEnergyForecast.get(period);

            for (Electrolyzer electrolyzer : dataModel.getAllElectrolyzers()) {
                double xSWOValue = dataModel.getXSWOValueForAgentPeriod(swoIterationCount + 1, electrolyzer.getId() - 1, period.getT() - 1);
                double electrolyzerPower = parameters.powerElectrolyzer.get(electrolyzer);
                double electrolyzerEnergy = xSWOValue * electrolyzerPower * parameters.intervalLengthSWO;
                totalElectrolyzerEnergy += electrolyzerEnergy;
            }

            double purchasedGridEnergy = totalElectrolyzerEnergy - renewableEnergyForCurrentPeriod;

            // Save the calculated values
            parameters.totalElectrolyzerEnergy.put(period, totalElectrolyzerEnergy);
            parameters.purchasedGridEnergy.put(period, purchasedGridEnergy);
        }
    }

    
    private void handleSWO_DualUpdateMessage(String content) {
        String[] parts = content.split(";");

        if (parts.length >= 2) {
            int iteration = Integer.parseInt(parts[1]);
            for (int i = 2; i < parts.length; i++) {
                String[] resultParts = parts[i].split(",");
                if (resultParts.length >= 13) { // U-values (3), S-values (2), Residuals (3), Y-values
                    int electrolyzerID = Integer.parseInt(resultParts[0]) - 1;
                    int periodIndex = Integer.parseInt(resultParts[1]) - 1;

                    // Save U-values
                    double u1 = Double.parseDouble(resultParts[2]);
                    double u2 = Double.parseDouble(resultParts[3]);
                    double u3 = Double.parseDouble(resultParts[4]);
                    dataModel.saveUSWOValueForAgentPeriod(iteration + 1, electrolyzerID, periodIndex, 0, u1);
                    dataModel.saveUSWOValueForAgentPeriod(iteration + 1, electrolyzerID, periodIndex, 1, u2);
                    dataModel.saveUSWOValueForAgentPeriod(iteration + 1, electrolyzerID, periodIndex, 2, u3);

                    // Save S-values
                    double s1 = Double.parseDouble(resultParts[5]);
                    double s2 = Double.parseDouble(resultParts[6]);
                    dataModel.saveSSWOValueForPeriod(iteration + 1, electrolyzerID, periodIndex, 0, s1);
                    dataModel.saveSSWOValueForPeriod(iteration + 1, electrolyzerID, periodIndex, 1, s2);

                    // Save Residuals
                    double residual1 = Double.parseDouble(resultParts[7]);
                    double residual2 = Double.parseDouble(resultParts[8]);
                    double residual3 = Double.parseDouble(resultParts[9]);
                    dataModel.saveYResiduals(iteration, electrolyzerID, periodIndex, 
                        new double[]{residual1, residual2, residual3});

                    // Save Y-values for each state
                    boolean[] yValues = new boolean[State.values().length];
                    for (int j = 0; j < State.values().length; j++) {
                        yValues[j] = resultParts[10 + j].equals("1");
                    }
                    dataModel.saveYSWOValuesForAgentPeriod(iteration + 1, electrolyzerID, periodIndex, yValues);
                }
            }
        }
    }

    private boolean checkFeasibility() {
        return checkFeasibilityAndCalculateObjective(
            periods,
            parameters,
            dataModel,
            swoIterationCount
        );
    }

    private void sendConvergenceMessage() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setContent("convergenceReached");
        for (AID agent : dataModel.getPhoneBook()) {
            msg.addReceiver(agent);
        }
        myAgent.send(msg);
    }
    
    private void saveSWOResultsAndTerminate() {
        long endTime = System.nanoTime(); 
        dataModel.setComputationTime(endTime - dataModel.getStartComputationTime()); // Save computing time
        try {
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
                desktopPath = Paths.get(System.getProperty("user.home"), "Desktop").toString(); // Standard path
            }

            String excelFilePathFinalResults = desktopPath + "/" + saveDetails + "_FinalSWOResults_" + myAgent.getLocalName() + ".xlsx";
            dataModel.exportFinalIterationResultsToExcel(swoIterationCount, parameters.getElectrolyzers(), parameters.getPeriods(), parameters, excelFilePathFinalResults);
            
            // Export all variables per electrolyzer to separate Excel files
            try {
                String baseFilePath = desktopPath + "/" + saveDetails + "_ADMM_Variables_Iteration_" + swoIterationCount;
                dataModel.exportAllVariablesPerElectrolyzerToExcel(
                    swoIterationCount, 
                    parameters.getElectrolyzers(), 
                    parameters.getPeriods(), 
                    baseFilePath
                );
                System.out.println("Alle Variablen erfolgreich in separate Excel-Dateien exportiert.");
            } catch (Exception e) {
                System.err.println("Fehler beim Exportieren der Variablen: " + e.getMessage());
                e.printStackTrace();
            }

            /* 
            // Export of final results
            dataModel.exportFinalIterationResultsToExcel(swoIterationCount, parameters.getElectrolyzers(), parameters.getPeriods(), parameters, excelFilePathFinalResults);
            
            System.out.println("Starting RTO Optimization Behaviour...");
            
            myAgent.addBehaviour(new RTO_CyclicBehaviour(
                totalNumberADMMAgents,
                model,
                parameters,
                dataModel,	
                electrolyzers,
                7,  
                rho,
                swoIterationCount // corresponds to final iteration
            ));*/
            
            System.out.println("Entferne SWO-Cyclic Behaviour für Agent: " + myAgent.getLocalName());
            myAgent.removeBehaviour(this);

            
//            // Export of iteration data
//            dataModel.writeValuesToExcel_Distributed(excelFilePathIterationResults);
//
//            System.out.println("Results successfully saved:");
//            System.out.println("Iteration data: " + excelFilePathIterationResults);
//            System.out.println("Final results: " + excelFilePathFinalResults);
        } catch (Exception e) {
            System.err.println("Fehler beim Schreiben der Excel-Dateien: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void calculateBoundaryResiduals() {
        double dualResidual = 0.0; // Sum of primary residuals

        for (Electrolyzer e : dataModel.getAllElectrolyzers()) {
            int agentIndex = e.getId() - 1;

            for (Period t : periods) {
                int periodIndex = t.getT() - 1;

                // Values from DataModel
                double xValue = dataModel.getXSWOValueForAgentPeriod(swoIterationCount, agentIndex, periodIndex);
                boolean isProducing = dataModel.getYSWOValuesForAgent(swoIterationCount, agentIndex)[periodIndex][State.PRODUCTION.ordinal()];

                // Calculation of boundary values
                double opMin = e.getMinOperation();
                double opMax = e.getMaxOperation();

                // Calculation of primary residual
                if (isProducing) {
                    double lowerBoundary = opMin;
                    double upperBoundary = opMax;

                    // Violations of lower boundary
                    if (xValue < lowerBoundary) {
                        double violation = lowerBoundary - xValue;
                        dualResidual += violation * violation;
                    }

                    // Violations of upper boundary
                    if (xValue > upperBoundary) {
                        double violation = xValue - upperBoundary;
                        dualResidual += violation * violation;
                    }
                } else {
                    // Check if x > 0 even though the Production state is not active
                    if (xValue > 0) {
                        double violation = xValue; // Value of x directly as violation
                        dualResidual += violation * violation;
                    }
                }
            }
        }

        dualResidual = Math.sqrt(dualResidual);

        // Save the residuals in DataModel
        dataModel.saveDualResidualForIteration(swoIterationCount, dualResidual);
    }
   
    public static boolean checkFeasibilityAndCalculateObjective(Set<Period> currentPeriods,
            Parameters params, ADMMDataModel dataExchange, int admmIter) {

	    System.out.println("Überprüfe Zulässigkeit nach Iteration " + admmIter);

	            double tolerancePercentage = 0.0005; // 0.5% tolerance allowed
        double zeroTolerance = 0.01; // Fixed tolerance when boundary value is zero

	    double[][] xValues = dataExchange.getXSWOValuesForIteration(admmIter);
	    boolean[][][] yValues = dataExchange.getYSWOValuesForIteration(admmIter);
	    boolean feasible = true;
	    
	    Set<Electrolyzer> electrolyzers = dataExchange.getAllElectrolyzers();
	    double objectiveValue = 0.0; // Collect objective function value

	            // Validation check and calculation of objective function value
	    for (Electrolyzer electrolyzer : electrolyzers) {
	        int agentIndex = electrolyzer.getId() - 1;
	        double powerElectrolyzer = params.powerElectrolyzer.get(electrolyzer);
	        double startupCost = params.startupCost.get(electrolyzer);
	        double standbyCost = params.standbyCost.get(electrolyzer);

	        for (Period period : currentPeriods) {
	            int periodIndex = period.getT() - 1;
	            double electricityPrice = params.electricityCost.get(period);
	            double intervalLength = params.intervalLengthSWO;

	            double x = xValues[agentIndex][periodIndex];
	            boolean[] y = yValues[agentIndex][periodIndex];

	            // Calculation of objective function value
	            objectiveValue += x * powerElectrolyzer * electricityPrice * intervalLength;
	            objectiveValue += y[State.STARTING.ordinal()] ? startupCost * intervalLength : 0.0;
	            objectiveValue += y[State.STANDBY.ordinal()] ? standbyCost * intervalLength : 0.0;

	            // Constraint 1: Sum of y-variables equals 1
	            int ySum = 0;
	            for (boolean yVal : y) {
	                ySum += yVal ? 1 : 0;
	            }
	            double ySumTolerance = tolerancePercentage; // Tolerance for sum condition
	            if (Math.abs(ySum - 1) > ySumTolerance) {
	                feasible = false;
	            }

	            // Constraint 2: x ≥ 0
	            double lowerBound = 0.0;
	            double lowerTolerance = (lowerBound == 0.0) ? zeroTolerance : tolerancePercentage * Math.abs(lowerBound);
	            if (x < lowerBound - lowerTolerance) {
	                System.out.println("Nebenbedingungsverletzung: x (" + x + ") < 0 (mit Toleranz " + lowerTolerance + ") für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT());
	                feasible = false;
	            }

	            // Constraint 3: x ≤ maxOperation * y_PRODUCTION + standbyLoad * y_STANDBY
	            double opMax = params.maxOperation.get(electrolyzer);
	            double standbyLoad = 0;
	            double xUpperBound = opMax * (y[State.PRODUCTION.ordinal()] ? 1 : 0)
	                               + standbyLoad * (y[State.STANDBY.ordinal()] ? 1 : 0);

	            double upperTolerance = (xUpperBound == 0.0) ? zeroTolerance : tolerancePercentage * Math.abs(xUpperBound);

	            if (x > xUpperBound + upperTolerance) {
	                System.out.println("Nebenbedingungsverletzung: x (" + x + ") überschreitet obere Grenze (" + xUpperBound + ") plus Toleranz (" + upperTolerance + ") für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT());
	                feasible = false;
	            }

	            // Constraint 4: x ≥ minOperation * y_PRODUCTION + standbyLoad * y_STANDBY
	            double minOperation = params.minOperation.get(electrolyzer);
	            double xLowerBound = minOperation * (y[State.PRODUCTION.ordinal()] ? 1 : 0)
	                               + standbyLoad * (y[State.STANDBY.ordinal()] ? 1 : 0);
	            boolean[] currentState = yValues[agentIndex][periodIndex];
	            
	            String activeState = "Unknown";
	            // Find the active state
	            for (State state : State.values()) {
	                if (currentState[state.ordinal()]) {
	                    activeState = state.name();
	                    break;
	                }
	            }

	            double lowerToleranceBound = (xLowerBound == 0.0) ? zeroTolerance : tolerancePercentage * Math.abs(xLowerBound);

	            if (x < xLowerBound - lowerToleranceBound) {
	                System.out.println("Nebenbedingungsverletzung: x (" + x + ") unterschreitet untere Grenze (" 
	                    + xLowerBound + ") minus Toleranz (" + lowerToleranceBound 
	                    + ") für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT() 
	                    + " (Zustand: " + activeState + ")");
	                feasible = false;
	            }
	            // State transition conditions
	            if (period.getT() > 1) {
	                int prevPeriodIndex = periodIndex - 1;

	                // Get y-values of previous period
	                boolean[] yPrev = yValues[agentIndex][prevPeriodIndex];

	                // Constraint: y_t,STARTING ≤ y_{t-1,IDLE} + y_{t-1,STARTING}
	                boolean lhs_STARTING = y[State.STARTING.ordinal()];
	                boolean rhs_STARTING = yPrev[State.IDLE.ordinal()] || yPrev[State.STARTING.ordinal()];
	                if (lhs_STARTING && !rhs_STARTING) {
	                    System.out.println("Nebenbedingungsverletzung: Ungültiger Übergang zu STARTING für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT());
	                    feasible = false;
	                }

	                // Constraint: y_t,PRODUCTION ≤ y_{t-1,PRODUCTION} + y_{t-1,STANDBY} + y_{t-startupDuration,STARTING}
	                boolean lhs_PRODUCTION = y[State.PRODUCTION.ordinal()];
	                boolean rhs_PRODUCTION = yPrev[State.PRODUCTION.ordinal()] || yPrev[State.STANDBY.ordinal()];
	                int startingHoldingDuration = params.holdingDurations.get(electrolyzer).get(State.STARTING);
	                
	                if (period.getT() > startingHoldingDuration) {
	                    int startupPeriodIndex = periodIndex - startingHoldingDuration;
	                    boolean yStartPrev = yValues[agentIndex][startupPeriodIndex][State.STARTING.ordinal()];
	                    rhs_PRODUCTION = rhs_PRODUCTION || yStartPrev;
	                }
	                if (lhs_PRODUCTION && !rhs_PRODUCTION) {
	                    System.out.println("Nebenbedingungsverletzung: Ungültiger Übergang zu PRODUCTION für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT());
	                    feasible = false;
	                }

	                // Constraint: y_t,STANDBY ≤ y_{t-1,PRODUCTION} + y_{t-1,STANDBY}
	                boolean lhs_STANDBY = y[State.STANDBY.ordinal()];
	                boolean rhs_STANDBY = yPrev[State.PRODUCTION.ordinal()] || yPrev[State.STANDBY.ordinal()];
	                if (lhs_STANDBY && !rhs_STANDBY) {
	                    System.out.println("Nebenbedingungsverletzung: Ungültiger Übergang zu STANDBY für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT());
	                    feasible = false;
	                }

	                // Constraint: y_t,IDLE ≤ y_{t-1,IDLE} + y_{t-1,PRODUCTION} + y_{t-1,STANDBY}
	                boolean lhs_IDLE = y[State.IDLE.ordinal()];
	                boolean rhs_IDLE = yPrev[State.IDLE.ordinal()] || yPrev[State.PRODUCTION.ordinal()] || yPrev[State.STANDBY.ordinal()];
	                if (lhs_IDLE && !rhs_IDLE) {
	                    System.out.println("Nebenbedingungsverletzung: Ungültiger Übergang zu IDLE für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT());
	                    feasible = false;
	                }
	                
					        // Ramp rates:
					        double rampTolerance = 0.001; // Example value for a fixed tolerance
                   // Ramp rate condition: Only check if the agent is in production in the current period
					if (y[State.PRODUCTION.ordinal()]) {
					    double currentXValue = xValues[agentIndex][periodIndex];
					    double previousXValue = xValues[agentIndex][prevPeriodIndex];

					            // Calculate the difference (ramp residual) between current and previous x-value
					    double diff1 = Math.abs(currentXValue - previousXValue);
					    double rampRate = params.getRampRate(electrolyzer);
					    		
					                            // Calculation of allowed ramp rate with tolerance
					    double upperRampTolerance = (rampRate == 0.0) ? rampTolerance : rampTolerance * Math.abs(rampRate);
					    double rampUpConstraint1 = rampRate;

					                            // Check for ramp rate violation
					    if (diff1 > rampUpConstraint1 + upperRampTolerance) {
					        System.out.println("Rampenratenverletzung (Bedingung 1): x_{a," + period.getT() + "} (" + currentXValue
					                + ") - x_{a," + (period.getT() - 1) + "} (" + previousXValue + ") überschreitet RampRateMax ("
					                + rampUpConstraint1 + ") plus Toleranz (" + upperRampTolerance + ") für Elektrolyseur "
					                + electrolyzer.getId() + " in Periode " + period.getT());
					        feasible = false;
					    }
					}

	            }
	        }
	    }

	 // Calculation of deviation costs
	    for (Period period : currentPeriods) {
	        int periodIndex = period.getT() - 1;
	        double demandPeriod = params.demand.get(period);
	        double demandDeviationCost = params.demandDeviationCost;

	        double productionSum = 0.0;
	        for (Electrolyzer electrolyzer : electrolyzers) {
	            int agentIndex = electrolyzer.getId() - 1;
	            boolean[] y = yValues[agentIndex][periodIndex];
	            int agentID = electrolyzer.getId() - 1;
	            double powerElectrolyzer = params.powerElectrolyzer.get(electrolyzer);
	            double slope = params.slope.get(electrolyzer);
	            double intercept = params.intercept.get(electrolyzer);
	            double x = xValues[agentID][periodIndex];
	            double intervalLength = params.intervalLengthSWO;

	            // Conversion from boolean to 0 (false) or 1 (true)
	            int isProductionActive = y[State.PRODUCTION.ordinal()] ? 1 : 0;

	            // Calculation of production: intercept is only used when isProductionActive == 1
	            productionSum += intervalLength * (x * slope * powerElectrolyzer + intercept * isProductionActive);
	        }

	        double deviation = Math.abs(demandPeriod - productionSum);
	        objectiveValue += deviation * demandDeviationCost;
	    }

	    // Output and return of the feasibility
	    System.out.println("Zielfunktionswert: " + objectiveValue);
	    
	    // Save the objective function value in dataExchange
	    dataExchange.saveObjectiveValueForIteration(admmIter, objectiveValue);
	    
	    if (feasible) {
	        System.out.println("Lösung ist zulässig nach Iteration " + admmIter);
	    } else {
	        System.out.println("Lösung ist unzulässig nach Iteration " + admmIter);
	    }
	    dataExchange.saveFeasibilityForIteration(admmIter, feasible);
	    return feasible;
	}
    
}
