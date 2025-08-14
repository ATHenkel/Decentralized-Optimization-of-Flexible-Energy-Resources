package behaviours;

import jade.core.behaviours.OneShotBehaviour;

import java.util.*;
import java.util.function.Predicate; 

import com.gurobi.gurobi.*;

import models.*;

public class SWO_SUpdateBehaviour extends OneShotBehaviour {
    /**
     * Global Variables
     */
    final static String ANSI_RESET = "\u001B[0m";
    final static String ANSI_GREEN = "\u001B[32m";

    private static final long serialVersionUID = 1L;
    private GRBModel model;
    private Parameters params;
    private Set<Electrolyzer> electrolyzers; // Set of electrolyzers
    private Set<Period> periods;
    private int iteration;
    private ADMMDataModel dataModel;
    private double rho;
    private Predicate<Electrolyzer> filterCriteria; // Filter criteria for electrolyzers
    
    // Constructor
    public SWO_SUpdateBehaviour(GRBModel model, Parameters params, Set<Electrolyzer> electrolyzers, Set<Period> periods, int iteration, ADMMDataModel dataModel, double rho, Predicate<Electrolyzer> filterCriteria) {
        this.model = model;
        this.params = params;
        this.electrolyzers = electrolyzers;
        this.periods = periods;
        this.iteration = iteration;
        this.dataModel = dataModel;
        this.rho = rho;
        this.filterCriteria = filterCriteria;
    }

    @Override
    public void action() {
    	System.out.println("s-Update von " + myAgent.getLocalName() + " in Iteration: " + iteration);
    	
    	long startTime = System.nanoTime();  // Start time measurement
        try {
            // Filter the electrolyzers that should be considered
            Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);
            
            // Initialize variables for all filtered electrolyzers and periods
            Map<Electrolyzer, Map<Period, GRBVar[]>> allSVars = new HashMap<>();
            GRBQuadExpr quadraticPenalty = new GRBQuadExpr();

            for (Electrolyzer electrolyzer : filteredElectrolyzers) {
            	
                int electrolyzerID = electrolyzer.getId() - 1;
                allSVars.put(electrolyzer, new HashMap<>());

                // Retrieve X- and Y-values for the current iteration
                double[] xValues = dataModel.getXSWOValuesForAgent(iteration + 1, electrolyzerID);
                if (xValues == null) {
                    xValues = new double[periods.size()];
                    Arrays.fill(xValues, 0.0);
                    dataModel.saveXSWOValuesForAgent(iteration, electrolyzerID, xValues);
                }
                boolean[][] yValues = dataModel.getYSWOValuesForAgent(iteration + 1, electrolyzerID);

                for (Period t : periods) {
                    int periodIndex = t.getT() - 1;
                    GRBVar[] sVarArray = new GRBVar[2];
                    sVarArray[0] = model.addVar(0.0, Double.MAX_VALUE, 0.0, GRB.CONTINUOUS, "s1_" + electrolyzerID + "_" + t.getT());
                    sVarArray[1] = model.addVar(0.0, Double.MAX_VALUE, 0.0, GRB.CONTINUOUS, "s2_" + electrolyzerID + "_" + t.getT());
                    allSVars.get(electrolyzer).put(t, sVarArray);

                    double xValue = xValues[periodIndex];
                    double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;
                    double[][] uValues = dataModel.getUSWOValuesForAgent(iteration, electrolyzerID);
                    double opMin = params.minOperation.get(electrolyzer);
                    double opMax = params.maxOperation.get(electrolyzer);

                    // Residual 1
                    if (productionYValue > 0) { // Only active when in Production state
                        GRBLinExpr residual1 = new GRBLinExpr();
                        residual1.addConstant(-xValue);
                        residual1.addConstant(opMin * productionYValue);
                        residual1.addConstant(uValues[periodIndex][0]);
                        residual1.addTerm(1.0, sVarArray[0]);

                        GRBVar residual1Var = model.addVar(-GRB.INFINITY, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "residual1_" + electrolyzerID + "_" + t.getT());
                        model.addConstr(residual1Var, GRB.EQUAL, residual1, "residual1_constr_" + electrolyzerID + "_" + t.getT());

                        // Add quadratic penalties to objective function
                        quadraticPenalty.addTerm(rho / 2, residual1Var, residual1Var);
                    }
                    
                    // Residual 2
                    if (productionYValue > 0) { // Only active when in Production state
                        GRBLinExpr residual2 = new GRBLinExpr();
                        residual2.addConstant(xValue);
                        residual2.addConstant(-opMax * productionYValue);
                        residual2.addConstant(uValues[periodIndex][1]);
                        residual2.addTerm(1.0, sVarArray[1]);

                        GRBVar residual2Var = model.addVar(-GRB.INFINITY, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "residual2_" + electrolyzerID + "_" + t.getT());
                        model.addConstr(residual2Var, GRB.EQUAL, residual2, "residual2_constr_" + electrolyzerID + "_" + t.getT());

                        // Quadratische Strafen zur Zielfunktion hinzufügen
                        quadraticPenalty.addTerm(rho / 2, residual2Var, residual2Var);
                    }
                }
            }

            // Setze die Zielfunktion
            model.setObjective(quadraticPenalty, GRB.MINIMIZE);

            model.getEnv().set(GRB.IntParam.Method, 2);
            model.getEnv().set(GRB.DoubleParam.OptimalityTol, 1e-6);
            model.getEnv().set(GRB.IntParam.OutputFlag, 0); // Deaktiviert alle Ausgaben
            model.optimize();

            if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {

                for (Electrolyzer electrolyzer : filteredElectrolyzers) {
                    int electrolyzerID = electrolyzer.getId() - 1;

                    for (Period t : periods) {
                        int periodIndex = t.getT() - 1;

                        for (int k = 0; k < 2; k++) {
                            double sValue = allSVars.get(electrolyzer).get(t)[k].get(GRB.DoubleAttr.X);
                            dataModel.saveSSWOValueForPeriod(iteration + 1, electrolyzerID, periodIndex, k, sValue);
                        }
                    }
                }

            } else {
                System.out.println("No optimal solution found for the s-Optimization of the filtered agents.");
            }

        } catch (GRBException e) {
            e.printStackTrace();
            System.out.println("Fehler während des S-Updates.");
        }
        
        long sEndTime = System.nanoTime();
        long sDuration = sEndTime - startTime;
        dataModel.saveSUpdateTimeForIteration(iteration, sDuration); 
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
}
