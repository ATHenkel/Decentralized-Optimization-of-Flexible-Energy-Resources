package behaviours;

import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;
import java.util.function.Predicate;

import models.*;

public class SWO_DualUpdateBehaviour extends OneShotBehaviour {
    /**
     * Global Variables
     */
    final static String ANSI_RESET = "\u001B[0m";
    final static String ANSI_GREEN = "\u001B[32m";

    private static final long serialVersionUID = 1L;
    private Parameters params;
    private Set<Electrolyzer> electrolyzers; // Set von Elektrolyseuren
    private Set<Period> periods;
    private int iteration;
    private ADMMDataModel dataModel;
    private Predicate<Electrolyzer> filterCriteria;
    private long dualUpdateTime = 0;
    private int sentMessages = 0;
    private double rho;
    private long startTime;

    // Konstruktor
    public SWO_DualUpdateBehaviour(Parameters params, Set<Electrolyzer> electrolyzers, Set<Period> periods, int iteration, ADMMDataModel dataModel, double rho, Predicate<Electrolyzer> filterCriteria) {
        this.params = params;
        this.electrolyzers = electrolyzers;
        this.periods = periods;
        this.iteration = iteration;
        this.dataModel = dataModel;
        this.filterCriteria = filterCriteria;
        this.rho = rho;
    }

    @Override
    public void action() {
    	System.out.println("Duales Update von " + myAgent.getLocalName() + " in Iteration: " + iteration);
    	
    	startTime = System.nanoTime();  // Start time measurement
    	
        // Filtere die Elektrolyseure, die betrachtet werden sollen
        Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);

        // Führe das Dual-Update gebündelt für die gefilterten Elektrolyseure durch
        Map<Integer, double[][]> updatedUValuesMap = new HashMap<>();
        

        for (Electrolyzer electrolyzer : filteredElectrolyzers) {
            double[][] newUValues = optimizeUForElectrolyzer(electrolyzer);
            if (newUValues != null) {
                updatedUValuesMap.put(electrolyzer.getId() - 1, newUValues);
            }
        }

        // Speichern der neuen U-Werte für alle gefilterten Elektrolyseure im ADMMDataModel
        for (Map.Entry<Integer, double[][]> entry : updatedUValuesMap.entrySet()) {
            dataModel.saveUSWOValuesForAgent(iteration + 1, entry.getKey(), entry.getValue());
        }

        // Senden der gebündelten Dual-Update-Ergebnisse nach Berechnung
        sendBundledDualUpdateResults(filteredElectrolyzers, periods, iteration);
        
        dualUpdateTime = System.nanoTime() - startTime; 
        dataModel.saveDualUpdateTimeForIteration(iteration, dualUpdateTime);
    }

    // Methode zum Filtern der Elektrolyseure basierend auf einem Kriterium
    private Set<Electrolyzer> filterElectrolyzers(Set<Electrolyzer> electrolyzers, Predicate<Electrolyzer> filterCriteria) {
        Set<Electrolyzer> filteredSet = new HashSet<>();
        for (Electrolyzer electrolyzer : electrolyzers) {
            if (filterCriteria.test(electrolyzer)) {
                filteredSet.add(electrolyzer);
            }
        }
        return filteredSet;
    }


    private double[][] optimizeUForElectrolyzer(Electrolyzer electrolyzer) {

        int nextIteration = iteration + 1;
        int electrolyzerID = electrolyzer.getId() - 1;

        // Zugriff auf X, Y, S Werte für die aktuelle Iteration aus dem ADMMDataModel
        double[] xValues = dataModel.getXSWOValuesForAgent(nextIteration, electrolyzerID);
        boolean[][] yValues = dataModel.getYSWOValuesForAgent(nextIteration, electrolyzerID);
        double[][] sValues = dataModel.getSSWOValuesForAgent(nextIteration, electrolyzerID);
        double[][] uValues = dataModel.getUSWOValuesForAgent(iteration, electrolyzerID);

        // Speicher für neue U-Werte (drei Werte pro Periode)
        double[][] newUValues = new double[periods.size()][3];

        for (Period t : periods) {
            int periodIndex = t.getT() - 1;

            // Zugriff auf die Konstanten für die Berechnung
            double xValue = xValues[periodIndex];
            double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;
            double opMin = params.minOperation.get(electrolyzer);
            double opMax = params.maxOperation.get(electrolyzer);

            // Zugriff auf den alten U-Wert
            double[] oldUValue = uValues[periodIndex];

            // Berechnung des Residuals
            double residual1 = rho*(-xValue + opMin * productionYValue + sValues[periodIndex][0]) + oldUValue[0];
            double residual2 = rho*(xValue - opMax * productionYValue + sValues[periodIndex][1]) +  oldUValue[1];
            double residual3 = productionYValue + oldUValue[2];
            
            // Schwellenwert definieren
            double threshold = 1e-6;
            
            // Überprüfung der Residualwerte gegen den Schwellenwert
            residual1 = Math.abs(residual1) < threshold ? 0.0 : residual1;
            residual2 = Math.abs(residual2) < threshold ? 0.0 : residual2;
            residual3 = Math.abs(residual3) < threshold ? 0.0 : residual3;

            // Speichern der neuen U-Werte
            newUValues[periodIndex][0] = residual1;
            newUValues[periodIndex][1] = residual2;
            newUValues[periodIndex][2] = residual3;
        }

        return newUValues;
    }

    private void sendBundledDualUpdateResults(Set<Electrolyzer> filteredElectrolyzers, Set<Period> periods, int iteration) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);

        // Nachrichtentext mit dualUpdateMessage und Iteration
        StringBuilder content = new StringBuilder();
        content.append("dualUpdateMessage;").append(iteration).append(";");

        // Für jeden Elektrolyseur und jede Periode füge die Daten hinzu
        for (Electrolyzer e : filteredElectrolyzers) {
            for (Period t : periods) {
                int electrolyzerID = e.getId() - 1;
                int periodIndex = t.getT() - 1;

                // Holen der U-, S-, Y- und Residual-Werte aus dem ADMMDataModel
                double[][] uValues = dataModel.getUSWOValuesForAgent(iteration + 1, electrolyzerID);
                double[][] sValues = dataModel.getSSWOValuesForAgent(iteration + 1, electrolyzerID);
                boolean[][] yValues = dataModel.getYSWOValuesForAgent(iteration + 1, electrolyzerID);
                double[] residuals = dataModel.getYSWOResiduals(iteration, electrolyzerID, periodIndex);

                // Füge die Daten für den aktuellen Elektrolyseur und die Periode hinzu
                content.append(e.getId()).append(",")  // Elektrolyseur-ID
                       .append(t.getT()).append(",")   // Periode
                       .append(uValues[periodIndex][0]).append(",")  // Residual 1
                       .append(uValues[periodIndex][1]).append(",")  // Residual 2
                       .append(uValues[periodIndex][2]).append(","); // Residual 3

                // S-Werte (s1 und s2)
                content.append(sValues[periodIndex][0]).append(",")   // s1
                       .append(sValues[periodIndex][1]).append(",");  // s2

                // Residuals
                if (residuals != null) {
                    content.append(residuals[0]).append(",")   // Residual 1
                           .append(residuals[1]).append(",")   // Residual 2
                           .append(residuals[2]).append(",");  // Residual 3
                } else {
                    content.append("0,0,0,");  // Standardwerte, falls keine Residuals existieren
                }

                // y-Werte (als 0 oder 1)
                for (State state : State.values()) {
                    content.append(yValues[periodIndex][state.ordinal()] ? 1 : 0).append(",");
                }

                content.deleteCharAt(content.length() - 1); // Entferne das letzte Komma
                content.append(";"); // Trenne die Daten dieses Elektrolyseurs
            }
        }

        // Setze den Nachrichtentext
        msg.setContent(content.toString());

        // Nachricht an alle Empfänger im Telefonbuch senden
        List<AID> phoneBook = dataModel.getPhoneBook();
        for (AID recipient : phoneBook) {
            if (!recipient.equals(myAgent.getAID())) {
                msg.addReceiver(recipient);
                sentMessages++;
            }
        }

        // Senden der Nachricht
        if (msg.getAllReceiver().hasNext()) {
            myAgent.send(msg);
        } else {
            System.out.println("Keine Empfänger gefunden, Nachricht wurde nicht gesendet.");
        }

        // Gesendete Nachrichten speichern
        int existingSentMessages = dataModel.getSentMessagesForIteration(iteration);
        dataModel.saveSentMessagesForIteration(iteration, existingSentMessages + sentMessages);
    }

}
