package behaviours;

import jade.core.behaviours.OneShotBehaviour;
import java.util.*;
import java.util.function.Predicate;

import org.apache.commons.math3.analysis.function.Max;

import models.*;
import com.gurobi.gurobi.*;

public class RTO_SUpdateBehaviour extends OneShotBehaviour {
    
    private static final long serialVersionUID = 1L;
    private GRBModel model; // Obwohl hier keine Optimierung erfolgt, behalten wir das Modell bei, falls es noch benötigt wird
    private Parameters params;
    private Set<Electrolyzer> electrolyzers; // Menge der Elektrolyseure
    private ADMMDataModel dataModel;
    private double rho; // Wird hier zwar nicht verwendet, aber beibehalten
    private Predicate<Electrolyzer> filterCriteria;
    private int currentRTOIteration;
    private Period currentSWOPeriod;
    private int finalSWOIteration;
    
    private int rtoStepsPerSWOPeriod = 10;

    // Konstruktor
    public RTO_SUpdateBehaviour(GRBModel model, Parameters params, Set<Electrolyzer> electrolyzers, 
            Period currentSWOPeriod, int finalSWOIteration, ADMMDataModel dataModel, double rho, 
            Predicate<Electrolyzer> filterCriteria, int RTOIteration) {
        this.model = model;
        this.params = params;
        this.electrolyzers = electrolyzers;
        this.dataModel = dataModel;
        this.rho = rho;
        this.filterCriteria = filterCriteria;
        this.currentRTOIteration = RTOIteration;
        this.currentSWOPeriod = currentSWOPeriod;
        this.finalSWOIteration = finalSWOIteration;
    }
    
    @Override
    public void action() {
        // Filtere die Elektrolyseure, die betrachtet werden sollen
        Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);
        
        System.out.println("s-Update von " + myAgent.getLocalName() 
            + " in RTO-Iteration: " + currentRTOIteration );
        
        // Für jeden gefilterten Elektrolyseur:
        for (Electrolyzer e : filteredElectrolyzers) {
            int electrolyzerID = e.getId() - 1;
            // X-Werte und Y-Werte für die aktuelle Iteration abrufen
            double[] xValues = dataModel.getXRTOValuesForAgent(currentRTOIteration + 1, electrolyzerID);
            boolean[][] yValues = dataModel.getYSWOValuesForAgent(finalSWOIteration, electrolyzerID);
            // Bestimme den Produktionszustand einmalig – hier verwenden wir den Wert für den aktuellen SWO-Zeitschritt:
            boolean isProduction = yValues[currentSWOPeriod.getT() - 1][State.PRODUCTION.ordinal()];
            double productionYValue = isProduction ? 1.0 : 0.0;
            
            // Für jede Periode:
            for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
                int periodIndex = t - 1;
                // Berechne s-Werte gemäß den Formeln:
                double s1 = xValues[periodIndex] - params.minOperation.get(e) * productionYValue;
                s1 = Math.max(s1, 0);
                
                // Für die obere Schranke gilt: x - x_max*y + s2 = 0  =>  s2 = x_max*y - x
                double s2 = params.maxOperation.get(e) * productionYValue - xValues[periodIndex];
                s2 = Math.max(s2, 0);
                
                // Speichern der s-Werte in dataModel:
                dataModel.saveSRTOValueForPeriod(currentRTOIteration + 1, electrolyzerID, periodIndex, 0, s1);
                dataModel.saveSRTOValueForPeriod(currentRTOIteration + 1, electrolyzerID, periodIndex, 1, s2);
                
            }
        }
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
    
    // Optional: Eine Methode zur Ausgabe der Werte (falls benötigt)
    public void printElectrolyzerValues() {
        System.out.println("\n===== Ausgabe der X-, Y- und S-Werte =====\n");
        for (Electrolyzer e : electrolyzers) {
            int electrolyzerID = e.getId() - 1;
            System.out.println("Elektrolyseur ID: " + (electrolyzerID + 1));
            double[] xValues = dataModel.getXRTOValuesForAgent(currentRTOIteration + 1, electrolyzerID);
            boolean[][] yValues = dataModel.getYSWOValuesForAgent(finalSWOIteration, electrolyzerID);
            boolean production = yValues[currentSWOPeriod.getT() - 1][State.PRODUCTION.ordinal()];
            for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
                int periodIndex = t - 1;
                double s1 = dataModel.getSRTOValueForAgentPeriod(currentRTOIteration + 1, electrolyzerID, periodIndex, 0);
                double s2 = dataModel.getSRTOValueForAgentPeriod(currentRTOIteration + 1, electrolyzerID, periodIndex, 1);
                System.out.println("Periode " + t + ": X=" + xValues[periodIndex] + 
                        ", Production=" + (production ? 1 : 0) +
                        ", s1=" + s1 + ", s2=" + s2);
            }
            System.out.println("-----------------------------------------");
        }
    }
}
