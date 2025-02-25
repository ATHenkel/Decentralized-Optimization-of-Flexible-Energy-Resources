package DecentralizedOptimizationGurobi;

import com.gurobi.gurobi.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class sUpdate {
    /**
     * Global Variables
     */
    final static String ANSI_RESET = "\u001B[0m";
    final static String ANSI_GREEN = "\u001B[32m";

    public void optimizeS(GRBModel model, Parameters params, Agent agent, Set<Period> periods, int iteration, DataExchange dataExchange, double rho) throws GRBException {
        System.err.println("-- s-Update for Agent " + agent.getId() + " --");

        int numPeriods = periods.size();
        int nextIteration = iteration + 1;
        int agentID = agent.getId() - 1;

        // Initialize variables map for the current agent
        Map<Period, GRBVar[]> sVars = new HashMap<>();

        // Initialize the sVars map for the specific agent
        for (Period t : periods) {
            GRBVar[] sVarArray = new GRBVar[2];
            sVarArray[0] = model.addVar(0.0, Double.MAX_VALUE, 0.0, GRB.CONTINUOUS, "s1_" + "_" + t.getT());
            sVarArray[1] = model.addVar(0.0, Double.MAX_VALUE, 0.0, GRB.CONTINUOUS, "s2_" + "_" + t.getT());
            sVars.put(t, sVarArray);
        }

        // Define the objective function with the quadratic penalty term
        GRBQuadExpr quadraticPenalty = new GRBQuadExpr();

        // Access X, Y, D, and U values for the current iteration
        double[] xValues = dataExchange.getXValuesForAgent(nextIteration, agent.getId() - 1);

        // Check if xValues are loaded correctly
        if (xValues == null) {
            System.err.println("xValues is null for iteration " + iteration + " and agent " + agent.getId() + ". Initializing with default values.");
            // Initialize xValues with default values (e.g., 0.0)
            xValues = new double[periods.size()];
            for (int i = 0; i < xValues.length; i++) {
                xValues[i] = 0.0;
            }
            // Optionally, save the initialized values in DataExchange if desired
            dataExchange.saveXValuesForAgent(iteration, agentID, xValues);
        }

        boolean[][] yValues = dataExchange.getYValuesForAgent(nextIteration, agent.getId() - 1);

        // Iterate over all periods
        for (Period t : periods) {
            int periodIndex = t.getT() - 1;

            // Access x and y values for the current period
            double xValue = xValues[periodIndex];
            double[][] uValues = dataExchange.getUValuesForAgent(iteration, agent.getId() - 1);
            double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;
            double opMin = params.minOperation.get(agent);
            double opMax = params.maxOperation.get(agent);

            // Create residual expressions for the s-Update
            GRBLinExpr residual1 = new GRBLinExpr();
            residual1.addConstant(-xValue);
            residual1.addConstant(opMin * productionYValue);
            residual1.addConstant(uValues[periodIndex][0]);
            residual1.addTerm(1.0, sVars.get(t)[0]);

            GRBLinExpr residual2 = new GRBLinExpr();
            residual2.addConstant(xValue);
            residual2.addConstant(-opMax * productionYValue);
            residual2.addConstant(uValues[periodIndex][1]);
            residual2.addTerm(1.0, sVars.get(t)[1]);

            // Add squared residuals to quadratic penalty
            GRBVar residual1Var = model.addVar(-GRB.INFINITY, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "residual1_" + t.getT());
            GRBVar residual2Var = model.addVar(-GRB.INFINITY, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "residual2_" + t.getT());

            // Add constraints to bind residual variables to their expressions
            model.addConstr(residual1Var, GRB.EQUAL, residual1, "residual1_constr_" + t.getT());
            model.addConstr(residual2Var, GRB.EQUAL, residual2, "residual2_constr_" + t.getT());

            // Add quadratic terms for the penalty
            quadraticPenalty.addTerm(rho / 2, residual1Var, residual1Var);
            quadraticPenalty.addTerm(rho / 2, residual2Var, residual2Var);
        }

        // Set the objective function to minimize the quadratic penalty
        model.setObjective(quadraticPenalty, GRB.MINIMIZE);

        // Solve the optimization problem
        model.optimize();

        if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
            System.err.println(ANSI_GREEN + "Iteration " + iteration + ": s-Optimization for Agent " + agent.getId() + " solved successfully." + ANSI_RESET);

            // Extract and update S values for the specific agent
            double[][] updatedSValues = new double[numPeriods][2];
            for (Period t : periods) {
                for (int k = 0; k < 2; k++) {
                    double sValue = sVars.get(t)[k].get(GRB.DoubleAttr.X);
                    updatedSValues[t.getT() - 1][k] = sValue;
                }
            }

            // Update DataExchange with new S values for the specific agent
            dataExchange.saveSValuesForAgent(nextIteration, agentID, updatedSValues);

        } else {
            System.out.println("No optimal solution found for Agent " + agent.getId());
        }
    }
}
