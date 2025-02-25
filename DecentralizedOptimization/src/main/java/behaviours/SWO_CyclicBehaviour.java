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
    private int receivedConvergenceMessages = 0; // Zähler für Konvergenznachrichten
    private final int totalNumberADMMAgents; // Die Anzahl der Agenten im System
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
        // Initialisierung für die erste Iteration
        if (!isFirstXUpdateDone) {
        	dataModel.setStartComputationTime(System.nanoTime());
            executeSWO_XUpdate();
            isFirstXUpdateDone = true;
            return;
        }

        // Stop, wenn maximale Iterationen erreicht sind
        if (swoIterationCount >= maxIterations) {
            System.out.println("Maximale Iterationszahl erreicht. Agent " + myAgent.getLocalName() + " beendet den ADMM-Zyklus.");
            saveSWOResultsAndTerminate();
            return;
        }

        // Nachricht empfangen und verarbeiten
        ACLMessage msg = myAgent.receive();
        if (msg != null) {
            processSWOMessage(msg);
        } else {
            block(); 
        }
    }

    /**
     * Verarbeitet eine eingehende Nachricht basierend auf ihrem Typ.
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
     * Prüft, ob alle X-Update-Nachrichten empfangen wurden.
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
        	
        	// Residuen berechnen
            calculateBoundaryResiduals();

            // Prüfe Konvergenz
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
        	
           	//Save Results and Terminate SWO Optimization
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
            double totalElectrolyzerEnergy = 0.0;  // <-- Hier wird die Variable für jede Periode zurückgesetzt!

            double renewableEnergyForCurrentPeriod = parameters.renewableEnergyForecast.get(period);

            for (Electrolyzer electrolyzer : dataModel.getAllElectrolyzers()) {
                double xSWOValue = dataModel.getXSWOValueForAgentPeriod(swoIterationCount + 1, electrolyzer.getId() - 1, period.getT() - 1);
                double electrolyzerPower = parameters.powerElectrolyzer.get(electrolyzer);
                double electrolyzerEnergy = xSWOValue * electrolyzerPower * parameters.intervalLengthSWO;
                totalElectrolyzerEnergy += electrolyzerEnergy;
            }

            double purchasedGridEnergy = totalElectrolyzerEnergy - renewableEnergyForCurrentPeriod;

            // Speichern der berechneten Werte
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
                if (resultParts.length >= 13) { // U-Werte (3), S-Werte (2), Residuals (3), Y-Werte
                    int electrolyzerID = Integer.parseInt(resultParts[0]) - 1;
                    int periodIndex = Integer.parseInt(resultParts[1]) - 1;

                    // Speichere U-Werte
                    double u1 = Double.parseDouble(resultParts[2]);
                    double u2 = Double.parseDouble(resultParts[3]);
                    double u3 = Double.parseDouble(resultParts[4]);
                    dataModel.saveUSWOValueForAgentPeriod(iteration + 1, electrolyzerID, periodIndex, 0, u1);
                    dataModel.saveUSWOValueForAgentPeriod(iteration + 1, electrolyzerID, periodIndex, 1, u2);
                    dataModel.saveUSWOValueForAgentPeriod(iteration + 1, electrolyzerID, periodIndex, 2, u3);

                    // Speichere S-Werte
                    double s1 = Double.parseDouble(resultParts[5]);
                    double s2 = Double.parseDouble(resultParts[6]);
                    dataModel.saveSSWOValueForPeriod(iteration + 1, electrolyzerID, periodIndex, 0, s1);
                    dataModel.saveSSWOValueForPeriod(iteration + 1, electrolyzerID, periodIndex, 1, s2);

                    // Speichere Residuals
                    double residual1 = Double.parseDouble(resultParts[7]);
                    double residual2 = Double.parseDouble(resultParts[8]);
                    double residual3 = Double.parseDouble(resultParts[9]);
                    dataModel.saveYResiduals(iteration, electrolyzerID, periodIndex, 
                        new double[]{residual1, residual2, residual3});

                    // Speichere Y-Werte für jeden Zustand
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
                desktopPath = Paths.get(System.getProperty("user.home"), "Desktop").toString(); // Standardpfad
            }

            String excelFilePathIterationResults = desktopPath + "/" + saveDetails + "_ADMM_Results_" + myAgent.getLocalName() + ".xlsx";
            String excelFilePathFinalResults = desktopPath + "/" + saveDetails + "_FinalSWOResults_" + myAgent.getLocalName() + ".xlsx";

            // Export der finalen Ergebnisse
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
                swoIterationCount //entspricht finaler Iteration
            ));
            
            System.out.println("Entferne SWO-Cyclic Behaviour für Agent: " + myAgent.getLocalName());
            myAgent.removeBehaviour(this);

            
//            // Export der Iterationsdaten
//            dataModel.writeValuesToExcel_Distributed(excelFilePathIterationResults);
//
//            System.out.println("Ergebnisse erfolgreich gespeichert:");
//            System.out.println("Iterationsdaten: " + excelFilePathIterationResults);
//            System.out.println("Finale Ergebnisse: " + excelFilePathFinalResults);
        } catch (Exception e) {
            System.err.println("Fehler beim Schreiben der Excel-Dateien: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void calculateBoundaryResiduals() {
        double dualResidual = 0.0; // Summe der Primärresiduen

        for (Electrolyzer e : dataModel.getAllElectrolyzers()) {
            int agentIndex = e.getId() - 1;

            for (Period t : periods) {
                int periodIndex = t.getT() - 1;

                // Werte aus dem DataModel
                double xValue = dataModel.getXSWOValueForAgentPeriod(swoIterationCount, agentIndex, periodIndex);
                boolean isProducing = dataModel.getYSWOValuesForAgent(swoIterationCount, agentIndex)[periodIndex][State.PRODUCTION.ordinal()];

                // Berechnung der Grenzwerte
                double opMin = e.getMinOperation();
                double opMax = e.getMaxOperation();

                // Berechnung des Primärresiduums
                if (isProducing) {
                    double lowerBoundary = opMin;
                    double upperBoundary = opMax;

                    // Verletzungen der unteren Grenze
                    if (xValue < lowerBoundary) {
                        double violation = lowerBoundary - xValue;
                        dualResidual += violation * violation;
                    }

                    // Verletzungen der oberen Grenze
                    if (xValue > upperBoundary) {
                        double violation = xValue - upperBoundary;
                        dualResidual += violation * violation;
                    }
                } else {
                    // Prüfen, ob x > 0 ist, obwohl der Zustand Production nicht aktiv ist
                    if (xValue > 0) {
                        double violation = xValue; // Wert von x direkt als Verletzung
                        dualResidual += violation * violation;
                    }
                }
            }
        }

        dualResidual = Math.sqrt(dualResidual);

        // Speichere die Residuen im DataModel
        dataModel.saveDualResidualForIteration(swoIterationCount, dualResidual);
    }
   
    public static boolean checkFeasibilityAndCalculateObjective(Set<Period> currentPeriods,
            Parameters params, ADMMDataModel dataExchange, int admmIter) {

	    System.out.println("Überprüfe Zulässigkeit nach Iteration " + admmIter);

	    double tolerancePercentage = 0.0005; // 0,5% Toleranz erlaubt
	    double zeroTolerance = 0.01; // Feste Toleranz, wenn Grenzwert Null ist

	    double[][] xValues = dataExchange.getXSWOValuesForIteration(admmIter);
	    boolean[][][] yValues = dataExchange.getYSWOValuesForIteration(admmIter);
	    boolean feasible = true;
	    
	    Set<Electrolyzer> electrolyzers = dataExchange.getAllElectrolyzers();

	    double objectiveValue = 0.0; // Zielfunktionswert sammeln

	    // Überprüfung der Gültigkeit und Berechnung des Zielfunktionswertes
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
	                feasible = false;
	            }

	            // Nebenbedingung 2: x ≥ 0
	            double lowerBound = 0.0;
	            double lowerTolerance = (lowerBound == 0.0) ? zeroTolerance : tolerancePercentage * Math.abs(lowerBound);
	            if (x < lowerBound - lowerTolerance) {
	                System.out.println("Nebenbedingungsverletzung: x (" + x + ") < 0 (mit Toleranz " + lowerTolerance + ") für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT());
	                feasible = false;
	            }

	            // Nebenbedingung 3: x ≤ maxOperation * y_PRODUCTION + standbyLoad * y_STANDBY
	            double opMax = params.maxOperation.get(electrolyzer);
	            double standbyLoad = 0;
	            double xUpperBound = opMax * (y[State.PRODUCTION.ordinal()] ? 1 : 0)
	                               + standbyLoad * (y[State.STANDBY.ordinal()] ? 1 : 0);

	            double upperTolerance = (xUpperBound == 0.0) ? zeroTolerance : tolerancePercentage * Math.abs(xUpperBound);

	            if (x > xUpperBound + upperTolerance) {
	                System.out.println("Nebenbedingungsverletzung: x (" + x + ") überschreitet obere Grenze (" + xUpperBound + ") plus Toleranz (" + upperTolerance + ") für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT());
	                feasible = false;
	            }

	            // Nebenbedingung 4: x ≥ minOperation * y_PRODUCTION + standbyLoad * y_STANDBY
	            double minOperation = params.minOperation.get(electrolyzer);
	            double xLowerBound = minOperation * (y[State.PRODUCTION.ordinal()] ? 1 : 0)
	                               + standbyLoad * (y[State.STANDBY.ordinal()] ? 1 : 0);
	            boolean[] currentState = yValues[agentIndex][periodIndex];
	            
	            String activeState = "Unknown";
	            // Finde den aktiven Zustand
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
	            // Zustandsübergangsbedingungen
	            if (period.getT() > 1) {
	                int prevPeriodIndex = periodIndex - 1;

	                // Hole y-Werte der vorherigen Periode
	                boolean[] yPrev = yValues[agentIndex][prevPeriodIndex];

	                // Nebenbedingung: y_t,STARTING ≤ y_{t-1,IDLE} + y_{t-1,STARTING}
	                boolean lhs_STARTING = y[State.STARTING.ordinal()];
	                boolean rhs_STARTING = yPrev[State.IDLE.ordinal()] || yPrev[State.STARTING.ordinal()];
	                if (lhs_STARTING && !rhs_STARTING) {
	                    System.out.println("Nebenbedingungsverletzung: Ungültiger Übergang zu STARTING für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT());
	                    feasible = false;
	                }

	                // Nebenbedingung: y_t,PRODUCTION ≤ y_{t-1,PRODUCTION} + y_{t-1,STANDBY} + y_{t-startupDuration,STARTING}
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

	                // Nebenbedingung: y_t,STANDBY ≤ y_{t-1,PRODUCTION} + y_{t-1,STANDBY}
	                boolean lhs_STANDBY = y[State.STANDBY.ordinal()];
	                boolean rhs_STANDBY = yPrev[State.PRODUCTION.ordinal()] || yPrev[State.STANDBY.ordinal()];
	                if (lhs_STANDBY && !rhs_STANDBY) {
	                    System.out.println("Nebenbedingungsverletzung: Ungültiger Übergang zu STANDBY für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT());
	                    feasible = false;
	                }

	                // Nebenbedingung: y_t,IDLE ≤ y_{t-1,IDLE} + y_{t-1,PRODUCTION} + y_{t-1,STANDBY}
	                boolean lhs_IDLE = y[State.IDLE.ordinal()];
	                boolean rhs_IDLE = yPrev[State.IDLE.ordinal()] || yPrev[State.PRODUCTION.ordinal()] || yPrev[State.STANDBY.ordinal()];
	                if (lhs_IDLE && !rhs_IDLE) {
	                    System.out.println("Nebenbedingungsverletzung: Ungültiger Übergang zu IDLE für Elektrolyseur " + electrolyzer.getId() + " in Periode " + period.getT());
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
					    double rampRate = params.getRampRate(electrolyzer);
					    		
					    // Berechnung der zulässigen Rampenrate mit Toleranz
					    double upperRampTolerance = (rampRate == 0.0) ? rampTolerance : rampTolerance * Math.abs(rampRate);
					    double rampUpConstraint1 = rampRate;

					    // Überprüfung auf Verletzung der Rampenrate
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

	            // Umwandlung von boolean in 0 (false) oder 1 (true)
	            int isProductionActive = y[State.PRODUCTION.ordinal()] ? 1 : 0;

	            // Berechnung der Produktion: intercept wird nur verwendet, wenn isProductionActive == 1
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
