package behavioursCPLEX;

import ilog.concert.*;
import ilog.cplex.*;
import jade.core.behaviours.OneShotBehaviour;

import java.util.*;
import java.util.function.Predicate; // Verwende Predicate für Filterkriterien
import models.*;

public class SUpdateBehaviour extends OneShotBehaviour {
    /**
     * Global Variables
     */
    final static String ANSI_RESET = "\u001B[0m";
    final static String ANSI_GREEN = "\u001B[32m";

    private static final long serialVersionUID = 1L;
    private IloCplex cplex;
    private Parameters params;
    private Set<Electrolyzer> electrolyzers; // Set von Elektrolyseuren
    private Set<Period> periods;
    private int iteration;
    private ADMMDataModel dataModel;
    private double rho;
    private Predicate<Electrolyzer> filterCriteria; // Filterkriterium für Elektrolyseure

    // Konstruktor
    public SUpdateBehaviour(IloCplex cplex, Parameters params, Set<Electrolyzer> electrolyzers, Set<Period> periods, int iteration, ADMMDataModel dataModel, double rho, Predicate<Electrolyzer> filterCriteria) {
        this.cplex = cplex;
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
        try {
            // Filtere die Elektrolyseure, die betrachtet werden sollen
            Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);

            // Führe die Optimierung für die gefilterten Elektrolyseure durch
            for (Electrolyzer electrolyzer : filteredElectrolyzers) {
                optimizeSForElectrolyzer(electrolyzer);
            }
        } catch (IloException e) {
            e.printStackTrace();
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

    // Optimierungsprozess für einen einzelnen Elektrolyseur
    private void optimizeSForElectrolyzer(Electrolyzer electrolyzer) throws IloException {
        System.err.println("-- s-Update for Electrolyzer " + electrolyzer.getId() + " --");
        
        cplex.clearModel();

        int numPeriods = periods.size();
        int nextIteration = iteration + 1;
        int electrolyzerID = electrolyzer.getId() - 1;

        // Initialize variables map for the current electrolyzer
        Map<Period, IloNumVar[]> sVars = new HashMap<>();

        // Initialize the sVars map for the specific electrolyzer
        for (Period t : periods) {
            IloNumVar[] sVarArray = new IloNumVar[2];
            sVarArray[0] = cplex.numVar(0, Double.MAX_VALUE, IloNumVarType.Float, "s1_" + "_" + t.getT());
            sVarArray[1] = cplex.numVar(0, Double.MAX_VALUE, IloNumVarType.Float, "s2_" + "_" + t.getT());
            sVars.put(t, sVarArray);
        }

        // Define the objective function with the quadratic penalty term
        IloNumExpr quadraticPenalty = cplex.constant(0);

        // Access X, Y, and U values for the current iteration from ADMMDataModel
        double[] xValues = dataModel.getXSWOValuesForAgent(nextIteration, electrolyzerID);

        // Überprüfe, ob xValues korrekt geladen wurden
        if (xValues == null) {
            System.err.println("xValues is null for iteration " + iteration + " and electrolyzer " + electrolyzer.getId() + ". Initializing with default values.");

            // Initialisiere xValues mit Standardwerten (z.B. 0.0)
            xValues = new double[periods.size()];
            Arrays.fill(xValues, 0.0);  // Füllt das Array mit dem Wert 0.0

            // Optional: Speichere die initialisierten Werte in ADMMDataModel, falls gewünscht
            dataModel.saveXSWOValuesForAgent(iteration, electrolyzerID, xValues);
        }

        boolean[][] yValues = dataModel.getYSWOValuesForAgent(nextIteration, electrolyzerID);

        // Iterate over all periods
        for (Period t : periods) {
            int periodIndex = t.getT() - 1;

            // Calculate residual for current electrolyzer and period
            IloNumExpr residual1 = cplex.constant(0);
            IloNumExpr residual2 = cplex.constant(0);

            // Access x and y values for the current period
            double xValue = xValues[periodIndex];
            double[][] uValues = dataModel.getUSWOValuesForAgent(iteration, electrolyzerID);
            double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;
            double opMin = params.minOperation.get(electrolyzer);
            double opMax = params.maxOperation.get(electrolyzer);

            // Constraint 1 components for the s-Update
            residual1 = cplex.sum(
                cplex.constant(-xValue),
                cplex.constant(opMin * productionYValue),
                cplex.constant(uValues[periodIndex][0]),
                sVars.get(t)[0]  // Use sVar directly as the decision variable
            );

            // Constraint 2 components for the s-Update
            residual2 = cplex.sum(
                cplex.constant(xValue),
                cplex.constant(-opMax * productionYValue),
                cplex.constant(uValues[periodIndex][1]),
                sVars.get(t)[1]  // Use sVar directly as the decision variable
            );

            // Square the residuals and add to quadratic penalty
            IloNumExpr squaredResidual1 = cplex.prod(residual1, residual1);
            IloNumExpr squaredResidual2 = cplex.prod(residual2, residual2);
            quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(rho / 2, squaredResidual1));
            quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(rho / 2, squaredResidual2));
        }

        // Set the objective function to minimize the quadratic penalty
        cplex.addMinimize(quadraticPenalty);

        // Solve the optimization problem
        if (cplex.solve()) {
            System.err.println(ANSI_GREEN + "Iteration " + iteration + ": s-Optimization for Electrolyzer " + electrolyzer.getId() + " solved successfully." + ANSI_RESET);

            // Extract and update S values for the specific electrolyzer
            double[][] updatedSValues = new double[numPeriods][2];
            for (Period t : periods) {
                for (int k = 0; k < 2; k++) {
                    double sValue = cplex.getValue(sVars.get(t)[k]);
                    updatedSValues[t.getT() - 1][k] = sValue;
                }
            }

            // Update ADMMDataModel with new S values for the specific electrolyzer
            dataModel.saveSSWOValuesForAgent(nextIteration, electrolyzerID, updatedSValues);

        } else {
            System.out.println("Solution status = " + cplex.getStatus());
            System.out.println("Solver status = " + cplex.getCplexStatus());
            System.out.println("No optimal solution found for Electrolyzer " + electrolyzer.getId());
        }
    }
}
