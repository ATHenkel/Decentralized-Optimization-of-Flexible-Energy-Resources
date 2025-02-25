package behaviours;

import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;
import java.util.function.Predicate;

import models.*;

public class RTO_DualUpdateBehaviour extends OneShotBehaviour {
    /**
     * Global Variables
     */
    final static String ANSI_RESET = "\u001B[0m";
    final static String ANSI_GREEN = "\u001B[32m";

    private static final long serialVersionUID = 1L;
    private Parameters params;
    private Set<Electrolyzer> electrolyzers; // Set von Elektrolyseuren
    private Period currentSWOPeriod;
    private int finalSWOIteration;
    private ADMMDataModel dataModel;
    private Predicate<Electrolyzer> filterCriteria;
    private double rho;
    private int currentRTOIteration;
    private int rtoStepsPerSWOPeriod = 10;
    private int currentStartPeriod;

    // Konstruktor
    public RTO_DualUpdateBehaviour(Parameters params, Set<Electrolyzer> electrolyzers, Period currentSWOPeriod, int finalSWOIteration, ADMMDataModel dataModel, double rho, Predicate<Electrolyzer> filterCriteria, int RTOIteration, int currentStartPeriod) {
        this.params = params;
        this.electrolyzers = electrolyzers;
        this.currentSWOPeriod = currentSWOPeriod;
        this.finalSWOIteration = finalSWOIteration;
        this.dataModel = dataModel;
        this.filterCriteria = filterCriteria;
        this.rho = rho;
        this.currentRTOIteration = RTOIteration;
        this.currentStartPeriod = currentStartPeriod;
    }

    @Override
    public void action() {
    	Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);
    	System.out.println("Duales Update von " + myAgent.getLocalName() + " in RTO-Iteration: " + currentRTOIteration + " SWO-Iteration: " + finalSWOIteration +  " in  Startperiode: " + currentStartPeriod);
    	
        optimizeUForElectrolyzer();

        sendBundledDualUpdateResults(filteredElectrolyzers);
    }

    private Set<Electrolyzer> filterElectrolyzers(Set<Electrolyzer> electrolyzers, Predicate<Electrolyzer> filterCriteria) {
        Set<Electrolyzer> filteredSet = new HashSet<>();
        for (Electrolyzer electrolyzer : electrolyzers) {
            if (filterCriteria.test(electrolyzer)) {
                filteredSet.add(electrolyzer);
            }
        }
        return filteredSet;
    }
    
    private void optimizeUForElectrolyzer() {
        int nextIteration = currentRTOIteration + 1;

        // Berechnung der gesamten erzeugten Energie der Elektrolyseure
        double totalElectrolyzerEnergy = 0.0;
        for (Electrolyzer e : dataModel.getAllElectrolyzers()) {
            int electrolyzerIndex = e.getId() - 1;
            double[] xValues = dataModel.getXRTOValuesForAgent(nextIteration, electrolyzerIndex);
            double powerElectrolyzer = params.powerElectrolyzer.get(e);
            double intervalLengthRTO = params.intervalLengthSWO / rtoStepsPerSWOPeriod;

            for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
                int periodIndex = t - 1;
                double xValue = xValues[periodIndex];
                totalElectrolyzerEnergy += powerElectrolyzer * intervalLengthRTO * xValue;
            }
        }

        // Berechnung der globalen Energiebilanz
        double[][] renewableEnergyMatrix = dataModel.getFluctuatingRenewableEnergyMatrix(); 
        double totalRenewableEnergy = 0.0;
        for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
            totalRenewableEnergy += renewableEnergyMatrix[t - 1][currentStartPeriod-1];
        }
                
        double purchasedGridEnergy = params.getPurchasedEnergy(currentSWOPeriod);
        double energyBalance = totalElectrolyzerEnergy - (totalRenewableEnergy + purchasedGridEnergy);
                
        // newUValues: [0] = untere Dualvariable, [1] = obere Dualvariable,
        double[][] newUValues = new double[rtoStepsPerSWOPeriod][4];

        // Iteration über alle Elektrolyzer
		for (Electrolyzer e : dataModel.getAllElectrolyzers()) {
			int electrolyzerIndex = e.getId() - 1;

			for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
				int periodIndex = t - 1;

				double[] xValues = dataModel.getXRTOValuesForAgent(nextIteration, electrolyzerIndex);
				boolean[][] yValues = dataModel.getYSWOValuesForAgent(finalSWOIteration, electrolyzerIndex);
				double[][] sValues = dataModel.getSRTOValuesForAgent(nextIteration, electrolyzerIndex);
				double[][] uValues = dataModel.getURTOValuesForAgent(currentRTOIteration, electrolyzerIndex);

				double xValue = xValues[periodIndex];
				// productionYValue ist 1.0, wenn im entsprechenden SWO-Zeitschritt produziert
				// wird, sonst 0.0.
				double productionYValue = yValues[currentSWOPeriod.getT() - 1][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;
				double opMin = params.minOperation.get(e);
				double opMax = params.maxOperation.get(e);

				double[] oldUValue = uValues[periodIndex];

				double newU1, newU2;

				// Fallunterscheidung: Zuerst, wenn Produktion aktiv ist:
				if (productionYValue > 0) {
					if (xValue < opMin) {
						// Untere Betriebsgrenze verletzt: Update der unteren Dualvariablen
						newU1 = oldUValue[0] + rho * (-xValue + opMin * productionYValue + sValues[periodIndex][0]);
						newU2 = 0.0; // obere Dualvariable zurücksetzen
					} else if (xValue > opMax) {
						// Obere Betriebsgrenze verletzt: Update der oberen Dualvariablen
						newU2 = oldUValue[1] + rho * (xValue - opMax * productionYValue + sValues[periodIndex][1]);
						newU1 = 0.0; // untere Dualvariable zurücksetzen
					} else {
						// Keine Verletzung: Beide Dualvariablen auf 0 setzen
						newU1 = 0.0;
						newU2 = 0.0;
					}
				} else {
					// Falls keine Produktion aktiv ist, soll dennoch bestraft werden, wenn x > 0
					// ist.
					if (xValue > 0) {
						// Hier wird ausgehend von den Constraints
						// -x + x_min*y + s_I = 0 und x - x_max*y + s_II = 0
						// für y = 0 zu
						// -x + s_I = 0 und x + s_II = 0,
						// also als Residuen: -x + sValues[periodIndex][0] bzw. x +
						// sValues[periodIndex][1].
						newU1 = oldUValue[0] + rho * (xValue + sValues[periodIndex][0]);
//                        newU2 = oldUValue[1] + rho * (xValue + sValues[periodIndex][1]);
						newU2 = oldUValue[1];
					} else {
						newU1 = 0.0;
						newU2 = 0.0;
					}
				}

				// Holen Sie sich den EnergyBalance der Voriteration
				double previousEnergyBalance = dataModel.getEnergyBalanceResultForIteration(currentRTOIteration);
				double deltaEnergy = Math.abs(energyBalance - previousEnergyBalance);

				double rhoScaling;
				if (deltaEnergy <= 0.005) {
					rhoScaling = 0.5;
				} else {
					rhoScaling = 0.01;
				}

				newUValues[periodIndex][0] = newU1;
				newUValues[periodIndex][1] = newU2;
				newUValues[periodIndex][2] = energyBalance;
				newUValues[periodIndex][3] = rhoScaling * energyBalance
						+ dataModel.getEnergyBalanceDualVariable(currentRTOIteration);

				dataModel.saveURTOValuesForAgent(nextIteration, electrolyzerIndex, newUValues);

				// Update der globalen Energiebilanz-Dualvariable
				double energyBalanceDualVariable = dataModel.getEnergyBalanceDualVariable(currentRTOIteration)
						+ rhoScaling * energyBalance;
				dataModel.setEnergyBalanceDualVariable(currentRTOIteration + 1, energyBalanceDualVariable);

				dataModel.setEnergyResultForIteration(nextIteration, energyBalance);

			}
		}
	}

    private void sendBundledDualUpdateResults(Set<Electrolyzer> filteredElectrolyzers) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);

        // Nachrichtentext mit dualUpdateMessage und Iteration
        StringBuilder content = new StringBuilder();
        content.append("RTOdualUpdateMessage;").append(currentRTOIteration).append(";");

        for (Electrolyzer e : electrolyzers) {
        	for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
                int electrolyzerIndex = e.getId() - 1;
                
                int periodIndex = t-1;

                double[][] uValues = dataModel.getURTOValuesForAgent(currentRTOIteration +1, electrolyzerIndex);
                double[][] sValues = dataModel.getSRTOValuesForAgent(currentRTOIteration + 1, electrolyzerIndex);
                boolean[][] yValues = dataModel.getYSWOValuesForAgent(finalSWOIteration, electrolyzerIndex);
                double[] residuals = dataModel.getYSWOResiduals(currentRTOIteration, electrolyzerIndex, periodIndex);

                content.append(e.getId()).append(",")  // Elektrolyseur-ID
                       .append(t).append(",")   // Periode
                       .append(uValues[periodIndex][0]).append(",")  
                       .append(uValues[periodIndex][1]).append(",") 
                       .append(uValues[periodIndex][2]).append(","); 

                content.append(sValues[periodIndex][0]).append(",")  
                       .append(sValues[periodIndex][1]).append(","); 

                if (residuals != null) {
                    content.append(residuals[0]).append(",")   
                           .append(residuals[1]).append(",")  
                           .append(residuals[2]).append(",");  
                } else {
                    content.append("0,0,0,");  
                }

                // y-Werte (als 0 oder 1)
                for (State state : State.values()) {
                    content.append(yValues[periodIndex][state.ordinal()] ? 1 : 0).append(",");
                }

                content.deleteCharAt(content.length() - 1); 
                content.append(";"); 
            }
        }

        // Setze den Nachrichtentext
        msg.setContent(content.toString());

        // Nachricht an alle Empfänger im Telefonbuch senden
        List<AID> phoneBook = dataModel.getPhoneBook();
        for (AID recipient : phoneBook) {
            if (!recipient.equals(myAgent.getAID())) {
                msg.addReceiver(recipient);
            }
        }

        // Senden der Nachricht
        if (msg.getAllReceiver().hasNext()) {
            myAgent.send(msg);
           
        } else {
            System.out.println("Keine Empfänger gefunden, Nachricht wurde nicht gesendet.");
        }
        
        dataModel.setDualUpdateCompleted(true);
    }
}