package behaviours;

import jade.core.behaviours.OneShotBehaviour;
import java.util.*;
import java.util.function.Predicate;
import models.*;
import com.gurobi.gurobi.*;

public class RTO_SUpdateBehaviour extends OneShotBehaviour {
    
    private static final long serialVersionUID = 1L;
    private Parameters params;
    private Set<Electrolyzer> electrolyzers; // Set of electrolyzers
    private ADMMDataModel dataModel;
    private Predicate<Electrolyzer> filterCriteria;
    private int currentRTOIteration;
    private Period currentSWOPeriod;
    private int finalSWOIteration;
    
    private int rtoStepsPerSWOPeriod = 10;

    // Constructor
    public RTO_SUpdateBehaviour(GRBModel model, Parameters params, Set<Electrolyzer> electrolyzers, 
            Period currentSWOPeriod, int finalSWOIteration, ADMMDataModel dataModel, double rho, 
            Predicate<Electrolyzer> filterCriteria, int RTOIteration) {
        this.params = params;
        this.electrolyzers = electrolyzers;
        this.dataModel = dataModel;
        this.filterCriteria = filterCriteria;
        this.currentRTOIteration = RTOIteration;
        this.currentSWOPeriod = currentSWOPeriod;
        this.finalSWOIteration = finalSWOIteration;
    }
    
    @Override
    public void action() {
        // Filter the electrolyzers that should be considered
        Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);
        
        System.out.println("s-Update von " + myAgent.getLocalName() 
            + " in RTO-Iteration: " + currentRTOIteration );
        
        // For each filtered electrolyzer:
        for (Electrolyzer e : filteredElectrolyzers) {
            int electrolyzerID = e.getId() - 1;
            // Retrieve X-values and Y-values for the current iteration
            double[] xValues = dataModel.getXRTOValuesForAgent(currentRTOIteration + 1, electrolyzerID);
            boolean[][] yValues = dataModel.getYSWOValuesForAgent(finalSWOIteration, electrolyzerID);
            // Determine the production state once - here we use the value for the current SWO time step:
            boolean isProduction = yValues[currentSWOPeriod.getT() - 1][State.PRODUCTION.ordinal()];
            double productionYValue = isProduction ? 1.0 : 0.0;
            
            // For each period:
            for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
                int periodIndex = t - 1;
                // Calculate s-values according to the formulas:
                double s1 = xValues[periodIndex] - params.minOperation.get(e) * productionYValue;
                s1 = Math.max(s1, 0);
                
                // For the upper bound: x - x_max*y + s2 = 0  =>  s2 = x_max*y - x
                double s2 = params.maxOperation.get(e) * productionYValue - xValues[periodIndex];
                s2 = Math.max(s2, 0);
                
                // Save s-values in dataModel:
                dataModel.saveSRTOValueForPeriod(currentRTOIteration + 1, electrolyzerID, periodIndex, 0, s1);
                dataModel.saveSRTOValueForPeriod(currentRTOIteration + 1, electrolyzerID, periodIndex, 1, s2);
                
            }
        }
    }
    
    // Method for filtering electrolyzers based on a criterion
    private Set<Electrolyzer> filterElectrolyzers(Set<Electrolyzer> electrolyzers, Predicate<Electrolyzer> filterCriteria) {
        Set<Electrolyzer> filteredSet = new HashSet<>();
        for (Electrolyzer electrolyzer : electrolyzers) {
            if (filterCriteria.test(electrolyzer)) {
                filteredSet.add(electrolyzer);
            }
        }
        return filteredSet;
    }
    
    // Optional: A method for outputting the values (if needed)
    public void printElectrolyzerValues() {
        System.out.println("\n===== Output of X-, Y- and S-values =====\n");
        for (Electrolyzer e : electrolyzers) {
            int electrolyzerID = e.getId() - 1;
            System.out.println("Electrolyzer ID: " + (electrolyzerID + 1));
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
