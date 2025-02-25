package DecentralizedOptimizationGurobi;

import com.gurobi.gurobi.*;

import java.util.*;

public class xUpdate {
    /**
     * Global Variables
     */
    final static String ANSI_RESET = "\u001B[0m";
    final static String ANSI_GREEN = "\u001B[32m";
    
    public void optimizeX(GRBModel model, Parameters params, Set<Agent> agents, Set<Period> periods, int iteration, DataExchange dataExchange, double rho) throws GRBException {
        System.out.println("-- xUpdate for all agents --");

        int nextIteration = iteration + 1;

        // Define the variables for the current optimization problem
        Map<Agent, Map<Period, GRBVar>> xVars = new HashMap<>();

        // Initialize x-Variables
        for (Agent a : agents) {
            xVars.put(a, new HashMap<>());
            for (Period t : periods) {
                xVars.get(a).put(t, model.addVar(0, 1, 0, GRB.CONTINUOUS, "x_" + a.a + "_" + t.t));
            }
        }

        // Define the objective function for all agents
        GRBLinExpr objective = new GRBLinExpr();
        for (Agent a : agents) {
            for (Period t : periods) {
                double powerElectrolyzer = params.powerElectrolyzer.get(a);
                double electricityPrice = params.electricityCost.get(t);
                double intervalLength = params.intervalLength;

                // f^V(X) for each agent
                objective.addTerm(powerElectrolyzer * electricityPrice * intervalLength, xVars.get(a).get(t));
            }
        }

        // Demand Deviation Costs
        Map<Period, Double> demandForPeriods = params.demand;
        double demandDeviationCost = params.demandDeviationCost;

        // Create variables for positive and negative deviations for each period
        Map<Period, GRBVar> positiveDeviations = new HashMap<>();
        Map<Period, GRBVar> negativeDeviations = new HashMap<>();

        for (Period t : periods) {
        	  int periodIndex = t.getT() - 1;
        	
            // Positive and negative deviations for each period
            positiveDeviations.put(t, model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "positiveDeviation_period_" + t.getT()));
            negativeDeviations.put(t, model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "negativeDeviation_period_" + t.getT()));

            // Get the demand for the current period
            double periodDemand = demandForPeriods.get(t);

            // Calculate total production for the current period
            GRBLinExpr productionInPeriod = new GRBLinExpr();
            for (Agent a : agents) {
                double slope = params.slope.get(a);
                double intercept = params.intercept.get(a);
                double powerElectrolyzer = params.powerElectrolyzer.get(a);
                double intervalLength = params.intervalLength;
                boolean[][] yValues = dataExchange.getYValuesForAgent(iteration, a.getId() - 1);
                double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;

                // Berechnung: intervalLength * (powerElectrolyzer * slope * xVars + intercept)
                productionInPeriod.addTerm(powerElectrolyzer * slope * intervalLength, xVars.get(a).get(t));
                productionInPeriod.addConstant(intervalLength * intercept * productionYValue);
            }

            // Add constraints for positive and negative deviations
            GRBLinExpr positiveDeviationExpr = new GRBLinExpr();
            positiveDeviationExpr.addTerm(1.0, positiveDeviations.get(t));
            positiveDeviationExpr.addConstant(-periodDemand);
            positiveDeviationExpr.add(productionInPeriod);
            model.addConstr(positiveDeviationExpr, GRB.GREATER_EQUAL, 0.0, "positiveDeviation_" + t.getT());

            GRBLinExpr negativeDeviationExpr = new GRBLinExpr();
            negativeDeviationExpr.addTerm(1.0, negativeDeviations.get(t));
            negativeDeviationExpr.addConstant(periodDemand);
            // Subtract each term in productionInPeriod from negativeDeviationExpr
            for (int i = 0; i < productionInPeriod.size(); i++) {
                negativeDeviationExpr.addTerm(-productionInPeriod.getCoeff(i), productionInPeriod.getVar(i));
            }
            model.addConstr(negativeDeviationExpr, GRB.GREATER_EQUAL, 0.0, "negativeDeviation_" + t.getT());

            // Add the absolute deviation per period to the objective function
            objective.addTerm(demandDeviationCost, positiveDeviations.get(t));
            objective.addTerm(demandDeviationCost, negativeDeviations.get(t));
        }


        // Zugriff auf Y-, S- und U-Werte für die aktuelle Iteration
        GRBQuadExpr quadraticPenalty = new GRBQuadExpr();
        for (Agent a : agents) {
            boolean[][] yValues = dataExchange.getYValuesForAgent(iteration, a.getId() - 1);
            double[][] sValues = dataExchange.getSValuesForAgent(iteration, a.getId() - 1);
            double[][] uValues = dataExchange.getUValuesForAgent(iteration, a.getId() - 1);
            double rampRate = params.getRampRate(a);
            
            for (Period t : periods) {
                int periodIndex = t.getT() - 1;

                // Komponenten für die erste Nebenbedingung für diesen Agenten
                double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;
                double opMin = params.minOperation.get(a);

                // Quadratischer Term für Residual1
                GRBVar xVar = xVars.get(a).get(t);
                double residual1Const = opMin * productionYValue + sValues[periodIndex][0] + uValues[periodIndex][0];
                quadraticPenalty.addTerm(rho / 2, xVar, xVar);
                quadraticPenalty.addTerm(rho / 2 * residual1Const, xVar);

                // Quadratischer Term für Residual2
                double opMax = params.maxOperation.get(a);
                double residual2Const = -opMax * productionYValue + sValues[periodIndex][1] + uValues[periodIndex][1];
                quadraticPenalty.addTerm(rho / 2, xVar, xVar);
                quadraticPenalty.addTerm(rho / 2 * residual2Const, xVar);
                
                if (periodIndex > 0 && iteration > 0) {
                    // Vorheriger x-Wert der letzten Periode für den Agenten
                    double previousXValue = dataExchange.getXValueForAgentPeriod(iteration - 1, a.getId() - 1, periodIndex - 1);

                    GRBLinExpr xDiff = new GRBLinExpr();
                    xDiff.addTerm(1.0, xVar);         // aktueller x-Wert
                    xDiff.addConstant(-previousXValue); // subtrahiere vorherigen x-Wert

                    // Berechne das Rampenresidual durch Abzug der zulässigen Rampenrate
                    GRBLinExpr rampResidual = new GRBLinExpr();
                    rampResidual.add(xDiff);                // Differenz xDiff hinzufügen
                    rampResidual.addConstant(-rampRate);     // zulässige Rampenrate abziehen

                    GRBLinExpr weightedRampResidual = new GRBLinExpr();
                    weightedRampResidual.multAdd(productionYValue, rampResidual); 

                    GRBVar weightedRampResidualVar = model.addVar(-GRB.INFINITY, GRB.INFINITY, 0, GRB.CONTINUOUS, "weightedRampResidual_" + a.getId() + "_" + periodIndex);

                    // Füge eine Nebenbedingung hinzu, um die Zwischenvariable auf weightedRampResidual zu setzen
                    model.addConstr(weightedRampResidualVar, GRB.EQUAL, weightedRampResidual, "setWeightedRampResidual_" + a.getId() + "_" + periodIndex);

                    GRBQuadExpr squaredRampResidual = new GRBQuadExpr();
                    squaredRampResidual.addTerm((rho*9500) / 2, weightedRampResidualVar, weightedRampResidualVar);

                    quadraticPenalty.add(squaredRampResidual);
                }
            }
        }

        // Combine the linear objective and quadratic penalty
        GRBQuadExpr objectiveWithPenalty = new GRBQuadExpr();
        objectiveWithPenalty.add(quadraticPenalty);
        for (int i = 0; i < objective.size(); i++) {
            objectiveWithPenalty.addTerm(objective.getCoeff(i), objective.getVar(i));
        }

        model.setObjective(objectiveWithPenalty, GRB.MINIMIZE);

        // Solve the optimization problem
        model.optimize();

        // Check if an optimal solution was found
        if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
            System.err.println(ANSI_GREEN + "Iteration " + iteration + ": x-Optimization solved successfully for all agents." + ANSI_RESET);

            for (Agent a : agents) {
                for (Period t : periods) {
                    double xValue = xVars.get(a).get(t).get(GRB.DoubleAttr.X);
                    double intercept = params.intercept.get(a);
                    int agentID = a.getId() - 1;
                    int periodIndex = t.getT() - 1;
                    double powerElectrolyzer = params.powerElectrolyzer.get(a);
                    double intervalLength = params.intervalLength;
                    double slope = params.slope.get(a);
                    boolean[][] yValues = dataExchange.getYValuesForAgent(iteration, a.getId() - 1);
                    double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;

                    // Save Optimization Results in DataExchange
                    dataExchange.saveXValueForPeriod(nextIteration, agentID, periodIndex, xValue);
                    double hydrogenProduction = intervalLength * (slope * powerElectrolyzer * xValue + intercept * productionYValue);
                    dataExchange.saveHydrogenProductionForPeriod(nextIteration, agentID, periodIndex, hydrogenProduction);
                    double periodDemand = params.demand.get(t);

                    System.out.println(String.format("Optimal X for Agent %d, Period %d: %.3f, Hydrogen Production: %.3f, Demand: %.3f",
                        a.getId(), t.getT(), xValue, hydrogenProduction, periodDemand));
                }
            }
        } else {
            System.out.println("No optimal solution found.");
        }
    }


}
