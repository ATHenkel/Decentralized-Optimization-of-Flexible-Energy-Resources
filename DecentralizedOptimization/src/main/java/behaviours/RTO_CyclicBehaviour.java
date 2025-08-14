package behaviours;

import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import com.gurobi.gurobi.GRBConstr;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBModel;
import com.gurobi.gurobi.GRBVar;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import models.ADMMDataModel;
import models.Electrolyzer;
import models.Parameters;
import models.Period;
import models.State;

public class RTO_CyclicBehaviour extends CyclicBehaviour {

    private static final long serialVersionUID = 1L;
    private int receivedXRTOMessages = 0;
    private int receivedDualRTOMessages = 0;
    private int receivedConvergenceRTOMessages = 0;
    private int receivedIterationIncrementedMessages = 0;
    private final int totalNumberADMMAgents; // Number of agents in the system
    private GRBModel model;
    private Parameters parameters;
    private ADMMDataModel dataModel;
    private Set<Electrolyzer> electrolyzers;
    private double rho;
    private int finalSWOIteration = 0;
    private boolean isFirstRTOXUpdateDone = false; 
    private int currentSWOPeriod;
    private int rtoIterationCount = 0;
    private int rtoStepsPerSWOPeriod = 10;
    private int currentStartPeriod = 1;
    private boolean iterationIncremented = false;
    private final Queue<ACLMessage> messageQueue = new LinkedList<>();
    private boolean sendIncrementedMessage;;
    
 // Time measurement
    private long endTime;
    
    public RTO_CyclicBehaviour(int totalAgents, GRBModel model, Parameters parameters, ADMMDataModel admmDataModel, Set<Electrolyzer> electrolyzers, Integer targetPeriod, double rho, int finalIteration) {
        this.totalNumberADMMAgents = totalAgents;
        this.model = model;
        this.parameters = parameters;
        this.dataModel = admmDataModel;
        this.electrolyzers = electrolyzers;
        this.rho = rho;
        this.finalSWOIteration = finalIteration;
        this.currentSWOPeriod = targetPeriod;
    }


    @Override
    public void action() {
        // Initialization for the first iteration
        if (!isFirstRTOXUpdateDone) {
            System.out.println("Starte RTO-Optimierung für Agent: " + myAgent.getLocalName());
            
            // Create fluctuating renewable energies once:
            double renewableEnergySWO = parameters.getRenewableEnergy(new Period(currentSWOPeriod));
            long seed = 10;
            double[][] fluctuatingEnergy = dataModel.calculateFluctuatingRenewableEnergy(dataModel.getRtoStepsPerSWO(), renewableEnergySWO, seed);
            dataModel.setFluctuatingRenewableEnergyMatrix(fluctuatingEnergy);
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = formatter.format(new Date());
            String desktopPath = System.getenv("DESKTOP_PATH");
            String saveDetails;
            saveDetails = timestamp;				
                        
            if (desktopPath == null || desktopPath.isEmpty()) {
                desktopPath = Paths.get(System.getProperty("user.home"), "Desktop").toString(); // Standard path
            }

            String excelFilePath = desktopPath + "/" + saveDetails + "_Fluctuating_Renewable_Energy_" + myAgent.getLocalName() + ".xlsx";
            
            dataModel.exportRenewableEnergyMatrixToExcel(excelFilePath);

            long startComputationTime = System.nanoTime();
            dataModel.setStartRTOComputationTime(startComputationTime);
            
            // Execute xUpdate
            System.err.println("Initiales X-Update von Agent " + myAgent.getLocalName());
            executeRTO_XUpdate();
            isFirstRTOXUpdateDone = true;
            return;
        }

        // FIFO queue for message processing
        while (true) {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                messageQueue.add(msg); // Add message to FIFO queue
            } else {
                break;
            }
        }

        if (!messageQueue.isEmpty()) {
            ACLMessage nextMessage = messageQueue.poll(); // Process oldest message (FIFO)
            processRTOMessage(nextMessage);
        } else {
            block();
        }
    }


    /**
     * Verarbeitet eine eingehende Nachricht basierend auf ihrem Typ.
     */
	private void processRTOMessage(ACLMessage msg) {
		String content = msg.getContent();

		if (content.startsWith("RTOxUpdateMessage")) {
			handleRTO_XUpdateMessage(content);
			receivedXRTOMessages++;
			checkRTO_XUpdateCompletion();
		} else if (content.startsWith("RTOdualUpdateMessage")) {
			handleRTO_DualUpdateMessage(content);
			receivedDualRTOMessages++;
			checkRTO_DualUpdateCompletion();
		} else if (content.equals("RTOconvergenceReached")) {
			handleRTO_ConvergenceMessage();
		} else if (content.startsWith("iterationIncremented")) {
			handleRTO_messagesIncrementedIteration();
			sendIncrementedMessage = false;

		}
	}

    /**
     * Prüft, ob alle X-Update-Nachrichten empfangen wurden.
     */
    private void checkRTO_XUpdateCompletion() {
        if (receivedXRTOMessages == totalNumberADMMAgents - 1) {
            executeRTO_SDualUpdates();
        }
    }
   
	/**
	 * Prüft, ob alle Dual-Update-Nachrichten empfangen wurden.
	 */

	private void checkRTO_DualUpdateCompletion() {
	            // Check if all dual messages have been received and if the dual update process is reported as completed.
	    if (receivedDualRTOMessages != totalNumberADMMAgents - 1 ) {
	        return;
	    }

	            // Save the dual messages for the current iteration and reset the counter.
	    dataModel.saveReceivedDualMessagesForIteration(finalSWOIteration, receivedDualRTOMessages);
	    receivedDualRTOMessages = 0;
	    dataModel.setDualUpdateCompleted(false);

	            // Calculate the absolute EnergyBalance value for the next iteration.
	    double energyBalance = Math.abs(dataModel.getEnergyBalanceResultForIteration(rtoIterationCount + 1));

	            // Check if convergence criteria are met:
	    boolean isConverged = (rtoIterationCount > 0 && energyBalance <= 0.005)
	                          || (rtoIterationCount == dataModel.getMaxIterations() - 1);
	    
	    if (isConverged) {
	        handleConvergence(energyBalance);
	    } else if (!isConverged) {
	        // Not yet converged: increase iteration and inform other agents.
	        rtoIterationCount++;
	        iterationIncremented = true;
	        sendIncrementMessage();
		}
 }	


	/**
	 * Wird aufgerufen, wenn die Konvergenz erreicht wurde.
	 * Speichert die x-Werte, exportiert die Ergebnisse, setzt Zähler zurück
	 * und erhöht currentStartPeriod.
	 */
	private void handleConvergence(double energyBalance) {
	    System.out.println("Energy Balance: " + energyBalance + " Agent: " + myAgent.getLocalName());
	    
	            // Save the x-values for each electrolyzer and each period of the current iteration.
	    for (Electrolyzer e : dataModel.getAllElectrolyzers()) {
	        int agentID = e.getId() - 1;
	        for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
	            int periodIndex = t - 1;
	            double xValue = dataModel.getXRTOValueForAgentPeriod(rtoIterationCount + 1, agentID, periodIndex);
	            if (xValue <= 0.01) {
	                xValue = 0;
	            }
	            dataModel.saveXRTOResultForAgentPeriod(agentID, periodIndex, xValue);
	        }
	    }
	    
	    // Berechne die finale Rechenzeit
	    long finalComputationTime = System.nanoTime() - dataModel.getStartRTOComputationTime();
	    dataModel.setFinalRTOComputationTime(finalComputationTime);

	            // Create the file path for Excel export
	    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
	    String timestamp = formatter.format(new Date());
	    String desktopPath = System.getenv("DESKTOP_PATH");
	    if (desktopPath == null || desktopPath.isEmpty()) {
	        desktopPath = Paths.get(System.getProperty("user.home"), "Desktop").toString();
	    }
	    String excelFilePathFinalResults = desktopPath + "/" + timestamp + "_CurrentStartPeriod_"
	                                        + currentStartPeriod + "_RTO-Results_" + myAgent.getLocalName() + ".xlsx";
	    
	    // Exportiere die Ergebnisse
	    dataModel.exportXRTOResultsToExcel(excelFilePathFinalResults, rtoIterationCount, energyBalance, finalComputationTime);

	            // Set the start time for the next iteration
	    long startComputationTime = System.nanoTime();
	    dataModel.setStartRTOComputationTime(startComputationTime);
	    
	            // Reset the iteration counter and increase currentStartPeriod
	    rtoIterationCount = 0;
	    currentStartPeriod++;

	            // Start the next x-update phase if not all RTO steps have been processed yet.
	    if (currentStartPeriod <= rtoStepsPerSWOPeriod) {
	        if (receivedXRTOMessages == totalNumberADMMAgents - 1) {
	        	System.err.println("Hier! Konvergenz-Fall");
	        	
	            executeRTO_XUpdate();
	        }
	    } else {
	        // Falls alle Perioden bearbeitet wurden, sende eine Konvergenznachricht.
	        sendConvergenceMessage();
	    }
	}

    /**
     * Führt die Y-, S- und Dual-Updates aus.
     */
	private void executeRTO_SDualUpdates() {
	    SequentialBehaviour seq = new SequentialBehaviour();

	    // Erster Schritt: SUpdate
	    seq.addSubBehaviour(new RTO_SUpdateBehaviour(model, parameters, electrolyzers, new Period(7), finalSWOIteration, dataModel, rho, e -> electrolyzers.contains(e), rtoIterationCount));

	    // Zweiter Schritt: DualUpdate
	    seq.addSubBehaviour(new RTO_DualUpdateBehaviour(parameters, electrolyzers, new Period(7), finalSWOIteration, dataModel, rho, e -> electrolyzers.contains(e), rtoIterationCount, currentStartPeriod));

	    seq.addSubBehaviour(new WakerBehaviour(myAgent, 10) {
			private static final long serialVersionUID = 1L;

			protected void onWake() {
	            System.out.println("5 ms nach Abschluss von SequentialBehaviour vergangen.");
	        }
	    });

	            // Add behavior to agent
	    myAgent.addBehaviour(seq);
	}


    /**
     * Führt das X-Update aus.
     */
    private void executeRTO_XUpdate() {
    	dataModel.setDualUpdateCompleted(false);
    	for (GRBConstr constr : model.getConstrs()) {
    	    try {
				model.remove(constr);
			} catch (GRBException e) {
				e.printStackTrace();
			} 
    	}
    	
    	// Entferne alle Variablen
    	for (GRBVar var : model.getVars()) {
    	    try {
				model.remove(var);
			} catch (GRBException e) {
				e.printStackTrace();
			}
    	}
    	
    	try {
			model.update();
		} catch (GRBException e) {
			e.printStackTrace();
		}
        myAgent.addBehaviour(new RTO_XUpdateBehaviour(model, parameters, parameters.getElectrolyzers(), new Period(7), finalSWOIteration, dataModel, rho, e -> electrolyzers.contains(e), rtoIterationCount, currentStartPeriod));
        receivedXRTOMessages = 0; 
    }

    /**
     * Verarbeitet Konvergenznachrichten.
     */
    private void handleRTO_ConvergenceMessage() {
        receivedConvergenceRTOMessages++;
        if (receivedConvergenceRTOMessages == totalNumberADMMAgents - 1) {
            saveRTOResultsAndTerminate();
        }
    }
    
    /**
     * Verarbeitet Konvergenznachrichten.
     */
    private void handleRTO_messagesIncrementedIteration() {
    	receivedIterationIncrementedMessages++;
    	
        if (receivedIterationIncrementedMessages == totalNumberADMMAgents - 1 && iterationIncremented == true) {
        	receivedIterationIncrementedMessages = 0;
        	iterationIncremented = false;
            executeRTO_XUpdate();
        }
    }
    
    // Method for parsing the X update message and saving the details
    private void handleRTO_XUpdateMessage(String content) {
        String[] parts = content.split(";");
        StringBuilder output = new StringBuilder("Gebündelte X-Werte und Wasserstoffproduktion aus x-Update:\n");

        if (parts.length >= 2) {
            int iteration = Integer.parseInt(parts[1]);
            output.append("Iteration: ").append(iteration).append("\n");
            
            if (iteration != rtoIterationCount) {
                System.err.println("Falsche Iteration RTO bei Agent " + myAgent.getLocalName() + " ist Iteration " + rtoIterationCount);
            }

            for (int i = 2; i < parts.length; i++) {
                String[] resultParts = parts[i].split(",");
                if (resultParts.length == 4) {
                    int electrolyzerID = Integer.parseInt(resultParts[0]) ;
                    int periodIndex = Integer.parseInt(resultParts[1]);
                    double xValue = Double.parseDouble(resultParts[2]);
                    double hydrogenProduction = Double.parseDouble(resultParts[3]);

                    output.append("Agent: ").append(myAgent.getLocalName())
                          .append(", Electrolyzer ID: ").append(electrolyzerID)
                          .append(", Periode: ").append(periodIndex)
                          .append(", X-Wert: ").append(xValue)
                          .append(", Wasserstoffproduktion: ").append(hydrogenProduction)
                          .append("\n");

                    // Save the values in the ADMM data model
                    dataModel.saveXRTOValueForPeriod(iteration +1, electrolyzerID-1, periodIndex-1, xValue);
                }
            }
            
        }
    }
    
    private void handleRTO_DualUpdateMessage(String content) {
        String[] parts = content.split(";");

        if (parts.length >= 2) {
            int iteration = Integer.parseInt(parts[1]);
            for (int i = 2; i < parts.length; i++) {
                String[] resultParts = parts[i].split(",");
                if (resultParts.length >= 13) {
                    int electrolyzerID = Integer.parseInt(resultParts[0]) - 1;
                    int periodIndex = Integer.parseInt(resultParts[1]) - 1;

                    // Speichere U-Werte
                    double u1 = Double.parseDouble(resultParts[2]);
                    double u2 = Double.parseDouble(resultParts[3]);
                    double u3 = Double.parseDouble(resultParts[4]);
                    dataModel.saveURTOValueForAgentPeriod(iteration + 1, electrolyzerID, periodIndex, 0, u1);
                    dataModel.saveURTOValueForAgentPeriod(iteration + 1, electrolyzerID, periodIndex, 1, u2);
                    dataModel.saveURTOValueForAgentPeriod(iteration + 1, electrolyzerID, periodIndex, 2, u3);
                    
                    // Speichere S-Werte
                    double s1 = Double.parseDouble(resultParts[5]);
                    double s2 = Double.parseDouble(resultParts[7]);
                    dataModel.saveSRTOValueForPeriod(iteration + 1, electrolyzerID, periodIndex, 0, s1);
                    dataModel.saveSRTOValueForPeriod(iteration + 1, electrolyzerID, periodIndex, 1, s2);

                    // Speichere Residuals
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
                }
            }
        }
    }

    private void sendConvergenceMessage() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setContent("RTOconvergenceReached");
        for (AID agent : dataModel.getPhoneBook()) {
            msg.addReceiver(agent);
        }
        myAgent.send(msg);
    }
    
    private final Object lock = new Object();  // Synchronisationsobjekt

    private void sendIncrementMessage() {
        synchronized (lock) {  // Der Thread muss den Monitor des Objekts 'lock' besitzen
            try {
                // Deine Logik zum Versenden der Nachricht
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.setContent("iterationIncremented");
                for (AID agent : dataModel.getPhoneBook()) {
                    if (!agent.equals(myAgent.getAID())) {
                        msg.addReceiver(agent);
                    }
                }
                myAgent.send(msg);
                sendIncrementedMessage = true;

                lock.wait(10);  
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private void saveRTOResultsAndTerminate() {
        endTime = System.nanoTime(); 
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

            String excelFilePathIterationResults = desktopPath + "/" + saveDetails + "_RTO_Results_" + myAgent.getLocalName() + ".xlsx";
            String excelFilePathFinalResults = desktopPath + "/" + saveDetails + "_FinalResults_" + myAgent.getLocalName() + ".xlsx";

            dataModel.writeRTOValuesToExcel(dataModel, 10, excelFilePathIterationResults, finalSWOIteration, currentSWOPeriod);
            
            System.out.println("Ergebnisse erfolgreich gespeichert:");
            System.out.println("Iterationsdaten: " + excelFilePathIterationResults);
            System.out.println("Finale Ergebnisse: " + excelFilePathFinalResults);
        } catch (Exception e) {
            System.err.println("Fehler beim Schreiben der Excel-Dateien: " + e.getMessage());
            e.printStackTrace();
        }

        myAgent.doDelete(); 
    }
     
    public static boolean checkFeasibilityAndCalculateObjective(Set<Period> currentPeriods,
            Parameters params, ADMMDataModel dataExchange, int admmIter) {
	    boolean feasible = false;
		return feasible;
	}
    
}
