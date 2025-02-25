package DecentralizedOptimization;

import ilog.concert.*;
import ilog.cplex.*;

import java.util.*;

public class xUpdate {
	/**
	 * Global Variables
	 */
	final static String ANSI_RESET = "\u001B[0m";
	final static String ANSI_GREEN = "\u001B[32m";

	public void optimizeX(IloCplex cplex, Parameters params, Set<Agent> agents, Set<Period> periods, int iteration,
			DataExchange dataExchange, double rho) throws IloException {
		System.out.println("-- xUpdate for all agents --");

		int nextIteration = iteration + 1;

		// Define the variables for the current optimization problem
		Map<Agent, Map<Period, IloNumVar>> xVars = new HashMap<>();

		// Initialize x-Variables
		for (Agent a : agents) {
			xVars.put(a, new HashMap<>());
			for (Period t : periods) {
				xVars.get(a).put(t, cplex.numVar(0, 1, IloNumVarType.Float, "x_" + a.a + "_" + t.t));
			}
		}

		// Define the objective function for all agents
		IloLinearNumExpr objective = cplex.linearNumExpr();
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
		Map<Period, IloNumVar> positiveDeviations = new HashMap<>();
		Map<Period, IloNumVar> negativeDeviations = new HashMap<>();

		for (Period t : periods) {
			int periodIndex = t.getT() - 1;

			// Positive and negative deviations for each period
			positiveDeviations.put(t, cplex.numVar(0, Double.MAX_VALUE, "positiveDeviation_period_" + t.getT()));
			negativeDeviations.put(t, cplex.numVar(0, Double.MAX_VALUE, "negativeDeviation_period_" + t.getT()));

			// Add constraints to capture the deviations per period
			double periodDemand = demandForPeriods.get(t);

			IloNumExpr productionInPeriod = cplex.numExpr();
			for (Agent a : agents) {

				double slope = params.slope.get(a);
				double intercept = params.intercept.get(a);
				double powerElectrolyzer = params.powerElectrolyzer.get(a);
				double intervalLength = params.intervalLength;
				boolean[][] yValues = dataExchange.getYValuesForAgent(iteration, a.getId() - 1);
				double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;

				// Berechnung: intervalLength * (powerElectrolyzer * slope * xVars + intercept)
				IloNumExpr agentProduction = cplex.prod(intervalLength,
						cplex.sum(cplex.prod(powerElectrolyzer * slope, xVars.get(a).get(t)), // powerElectrolyzer *
																								// slope * xVars
								cplex.constant(intercept * productionYValue) // + intercept
						));

				// Summiere die Produktionsmenge für den Agenten zur Gesamtproduktion hinzu
				productionInPeriod = cplex.sum(productionInPeriod, agentProduction);
			}

			// Add deviations constraints per period
			cplex.addGe(positiveDeviations.get(t), cplex.diff(productionInPeriod, periodDemand));
			cplex.addGe(negativeDeviations.get(t), cplex.diff(periodDemand, productionInPeriod));

			// Add the absolute deviation per period to the objective function
			objective.addTerm(demandDeviationCost, positiveDeviations.get(t));
			objective.addTerm(demandDeviationCost, negativeDeviations.get(t));
		}

		// Access Y, S, and U values for current iteration
		IloNumExpr quadraticPenalty = cplex.constant(0);
		Map<Agent, Map<Period, Double>> rampPenalties = new HashMap<>(); // Map to store ramp penalties for output

		for (Agent a : agents) {
			boolean[][] yValues = dataExchange.getYValuesForAgent(iteration, a.getId() - 1);
			double[][] sValues = dataExchange.getSValuesForAgent(iteration, a.getId() - 1);
			double[][] uValues = dataExchange.getUValuesForAgent(iteration, a.getId() - 1);

			rampPenalties.put(a, new HashMap<>()); // Initialize the map for this agent

			for (Period t : periods) {
				int periodIndex = t.getT() - 1;

				// Constraint 1 components for this agent
				double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;
				double opMin = params.minOperation.get(a);

				IloNumExpr residual1 = cplex.sum(cplex.prod(-1, xVars.get(a).get(t)),
						cplex.constant(opMin * productionYValue), cplex.constant(sValues[periodIndex][0]),
						cplex.constant(uValues[periodIndex][0]));

				// Constraint 2 components for this agent
				double opMax = params.maxOperation.get(a);
				IloNumExpr residual2 = cplex.sum(xVars.get(a).get(t), cplex.constant(-opMax * productionYValue),
						cplex.constant(sValues[periodIndex][1]), cplex.constant(uValues[periodIndex][1]));

				// Square the residual and add to quadratic penalty
				IloNumExpr squaredResidual1 = cplex.prod(residual1, residual1);
				IloNumExpr squaredResidual2 = cplex.prod(residual2, residual2);
				quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(rho / 2, squaredResidual1));
				quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(rho / 2, squaredResidual2));

				// Rampenbedingung nur berücksichtigen, wenn eine vorherige Periode existiert
//				if (periodIndex > 0 && iteration > 0) {
//					double[][] uRampValues = dataExchange.getUValuesForAgent(iteration, a.getId() - 1);
//					double previousURamp = uRampValues[periodIndex][3]; // 4. Index für Rampenraten verwenden
//
//					// Berechne den Unterschied zwischen aktuellem x und vorherigem x
//					double previousXValue = dataExchange.getXValueForAgentPeriod(iteration - 1, a.getId() - 1,
//							periodIndex - 1);
//					IloNumExpr rampResidual = cplex.diff(xVars.get(a).get(t), cplex.constant(previousXValue));
//
//					// Berechnung des quadratischen Strafterms unter Berücksichtigung der dualen
//					// Variable
//					IloNumExpr weightedRampResidual = cplex.sum(rampResidual, cplex.constant(previousURamp));
//					IloNumExpr squaredRampResidual = cplex.prod(weightedRampResidual, weightedRampResidual);
//
//					// Hinzufügen des Rampenstrafterms zur Zielfunktion
//					quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(rho*3, squaredRampResidual));
//				}
				
				    double rampRate = 0.6; // Zulässige Rampenrate pro Agent und Periode
				    

				        // Überprüfen, ob eine vorherige Periode existiert
				        if (periodIndex > 0 && iteration > 0) {
				            double previousXValue = dataExchange.getXValueForAgentPeriod(iteration - 1, a.getId() - 1,
									periodIndex - 1);
				            IloNumVar currentX = xVars.get(a).get(t);
				            
				            // Berechne die Differenz zwischen den x-Werten in aufeinanderfolgenden Perioden
				            IloNumExpr xDiff = cplex.diff(currentX,  cplex.constant(previousXValue));

				            // Berechne das Rampenresidual, indem die zulässige Rampenrate abgezogen wird
				            IloNumExpr rampResidual = cplex.prod(
				            	    cplex.diff(xDiff, cplex.constant(rampRate)), 
				            	    cplex.constant(productionYValue) //
				            	);

				            // Quadratischer Strafterm für Rampenverletzung (falls Differenz größer als zulässige Rate)
				            IloNumExpr squaredRampResidual = cplex.max(cplex.constant(0), rampResidual);
				            squaredRampResidual = cplex.prod(squaredRampResidual, squaredRampResidual);

				            // Hinzufügen des gewichteteten Rampenstrafterms zur Zielfunktion
				            quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(rho * 9500, squaredRampResidual));
				        }
				
			}
		}

		// Add the quadratic penalty term to the objective function
		cplex.addMinimize(cplex.sum(objective, quadraticPenalty));
		
		// Solve the optimization problem
		if (cplex.solve()) {
			
			System.err.println(ANSI_GREEN + "Iteration " + iteration
					+ ": x-Optimization solved successfully for all agents." + ANSI_RESET);

			for (Agent a : agents) {
				System.out.println("Agent: " + a.a);
				for (Period t : periods) {

					// Get the optimized X-value for the agent and the specific period
					double xValue = cplex.getValue(xVars.get(a).get(t));
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
					double hydrogenProduction = intervalLength
							* (slope * powerElectrolyzer * xValue + intercept * productionYValue);
					dataExchange.saveHydrogenProductionForPeriod(nextIteration, agentID, periodIndex,
							hydrogenProduction);
					double periodDemand = params.demand.get(t);

					System.out.println(String.format(
							"Optimal X for Agent %d, Period %d: %.3f, Hydrogen Production: %.3f, Demand: %.3f",
							a.getId(), t.getT(), xValue, hydrogenProduction, periodDemand));
				}
			}

		} else {
			System.out.println("Solution status = " + cplex.getStatus());
			System.out.println("Solver status = " + cplex.getCplexStatus());
			System.out.println("No optimal solution found.");
		}
	}

}