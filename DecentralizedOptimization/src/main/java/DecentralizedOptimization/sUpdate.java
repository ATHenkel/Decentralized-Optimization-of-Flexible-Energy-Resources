package DecentralizedOptimization;

import ilog.concert.*;
import ilog.cplex.*;
import java.util.*;


public class sUpdate {
    /**
     * Global Variables
     */
    final static String ANSI_RESET = "\u001B[0m";
    final static String ANSI_GREEN = "\u001B[32m";

    public void optimizeS(IloCplex cplex, Parameters params, Agent agent, Set<Period> periods, int iteration, DataExchange dataExchange, double rho) throws IloException {
        System.err.println("-- s-Update for Agent " + agent.getId() + " --");

        int numPeriods = periods.size();
        int nextIteration = iteration + 1;
        int agentID = agent.getId() - 1;

        // Initialize variables map for the current agent
        Map<Period, IloNumVar[]> sVars = new HashMap<>();

        // Initialize the sVars map for the specific agent
        for (Period t : periods) {
            IloNumVar[] sVarArray = new IloNumVar[2];
            sVarArray[0] = cplex.numVar(0, Double.MAX_VALUE, IloNumVarType.Float, "s1_" + "_" + t.getT());
            sVarArray[1] = cplex.numVar(0, Double.MAX_VALUE, IloNumVarType.Float, "s2_" + "_" + t.getT());
            sVars.put(t, sVarArray);
        }

        // Define the objective function with the quadratic penalty term
        IloNumExpr quadraticPenalty = cplex.constant(0);

        // Access X, Y, D, and U values for the current iteration
        double[] xValues = dataExchange.getXValuesForAgent(nextIteration, agent.getId() -1 );
        
        // Überprüfe, ob xValues korrekt geladen wurden
        if (xValues == null) {
            System.err.println("xValues is null for iteration " + iteration + " and agent " + agent.getId() + ". Initializing with default values.");
            
            // Initialisiere xValues mit Standardwerten (z.B. 0.0)
            xValues = new double[periods.size()];
            Arrays.fill(xValues, 0.0);  // Füllt das Array mit dem Wert 0.0

            // Optional: Speichere die initialisierten Werte in DataExchange, falls gewünscht
            dataExchange.saveXValuesForAgent(iteration, agentID, xValues);
        }
        
        boolean[][] yValues = dataExchange.getYValuesForAgent(nextIteration, agent.getId() -1 );

        // Iterate over all periods
        for (Period t : periods) {
            int periodIndex = t.getT() - 1;

            // Calculate residual for current agent and period
            IloNumExpr residual1 = cplex.constant(0);
            IloNumExpr residual2 = cplex.constant(0);

            // Access x and y values for the current period
            double xValue = xValues[periodIndex];
            double[][] uValues = dataExchange.getUValuesForAgent(iteration, agent.getId() -1 );
            double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;
            double opMin = params.minOperation.get(agent);
            double opMax = params.maxOperation.get(agent);

            // Constraint 1 components for the s-Update
            residual1 = cplex.sum(
                cplex.constant(-xValue),
                cplex.constant(opMin * productionYValue),
                cplex.constant(uValues[periodIndex][0]),
                sVars.get(t)[0] // Use sVar directly as the decision variable
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
        	System.err.println(ANSI_GREEN + "Iteration " + iteration + ": s-Optimization for Agent " + agent.getId() + " solved successfully." + ANSI_RESET);

            // Extract and update S values for the specific agent
            double[][] updatedSValues = new double[numPeriods][2];
            for (Period t : periods) {
                for (int k = 0; k < 2; k++) {
                    double sValue = cplex.getValue(sVars.get(t)[k]);
                    updatedSValues[t.getT() - 1][k] = sValue;
                }
            }

            // Update DataExchange with new S values for the specific agent
            dataExchange.saveSValuesForAgent(nextIteration, agentID, updatedSValues);

        } else {
            System.out.println("Solution status = " + cplex.getStatus());
            System.out.println("Solver status = " + cplex.getCplexStatus());
            System.out.println("No optimal solution found for Agent " + agent.getId());
        }
    }
}