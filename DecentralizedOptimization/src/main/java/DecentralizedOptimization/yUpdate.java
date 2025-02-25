package DecentralizedOptimization;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumExpr;
import ilog.cplex.IloCplex;

public class yUpdate {
    /**
     * Global Variables
     */
    final static String ANSI_RESET = "\u001B[0m";
    final static String ANSI_GREEN = "\u001B[32m";

    public void optimizeY(IloCplex cplex, Parameters params, Agent agent, Set<Period> periods, int iteration, DataExchange dataExchange, double rho) throws IloException {
        System.err.println("-- y-Update for Agent " + agent.getId() + " --");

        int numPeriods = periods.size();
        int nextIteration = iteration + 1;
        int agentID = agent.getId() - 1;

        // Define the variables for the current optimization problem
        Map<Period, Map<State, IloIntVar>> yVars = new HashMap<>();

        // Initialize variables for each period without agent-specific dependence
        for (Period t : periods) {
            yVars.put(t, new HashMap<>());
            for (State s : State.values()) {
                yVars.get(t).put(s, cplex.boolVar("y_" + t.getT() + "_" + s));
            }
        }

        // Define the objective function
        IloLinearNumExpr objective = cplex.linearNumExpr();

        for (Period t : periods) {
            double startupCost = params.startupCost.get(agent);
            double standbyCost = params.standbyCost.get(agent);

            //Start-Up Costs
            objective.addTerm(params.intervalLength * startupCost, yVars.get(t).get(State.STARTING));
            
            //Standby Costs
            objective.addTerm(params.intervalLength * standbyCost, yVars.get(t).get(State.STANDBY));
        }

        // Zugriff auf X, S, und U Werte für die aktuelle Iteration
        double[] xValues = dataExchange.getXValuesForAgent(nextIteration, agentID);
        double[][] sValues = dataExchange.getSValuesForAgent(iteration, agentID);
        double[][] uValues = dataExchange.getUValuesForAgent(iteration, agentID);
        
     // Statusübergänge
        for (Period t : periods) {
            if (t.getT() > 1) {
                Period prevPeriod = new Period(t.getT() - 1);
                
                int startingHoldingDuration = params.holdingDurations.get(agent).get(State.STARTING);
                Period startupPeriod = new Period(t.getT() - startingHoldingDuration);
                
                cplex.addLe(yVars.get(t).get(State.STARTING),
                        cplex.sum(yVars.get(prevPeriod).get(State.IDLE),
                                yVars.get(prevPeriod).get(State.STARTING)));

                if (t.getT() > startingHoldingDuration) {
                    cplex.addLe(yVars.get(t).get(State.PRODUCTION),
                            cplex.sum(yVars.get(startupPeriod).get(State.STARTING),
                                    yVars.get(prevPeriod).get(State.PRODUCTION),
                                    yVars.get(prevPeriod).get(State.STANDBY)));
                } else {
                    cplex.addLe(yVars.get(t).get(State.PRODUCTION),
                            cplex.sum(yVars.get(startupPeriod).get(State.STARTING),
                                    yVars.get(prevPeriod).get(State.PRODUCTION),
                                    yVars.get(prevPeriod).get(State.STANDBY)));
                }

                cplex.addLe(yVars.get(t).get(State.STANDBY),
                        cplex.sum(yVars.get(prevPeriod).get(State.PRODUCTION),
                                yVars.get(prevPeriod).get(State.STANDBY)));

                cplex.addLe(yVars.get(t).get(State.IDLE),
                        cplex.sum(yVars.get(prevPeriod).get(State.PRODUCTION),
                                yVars.get(prevPeriod).get(State.STANDBY),
                                yVars.get(prevPeriod).get(State.IDLE)));
            }

            // Besondere Nebenbedingung für die zweite Periode
            if (t.getT() == 2) {
                Period firstPeriod = new Period(1);

                // Wenn in der ersten Periode (t = 1) Idle aktiv ist
                cplex.addLe(yVars.get(t).get(State.STARTING),
                        yVars.get(firstPeriod).get(State.IDLE));  // Starting nur erlaubt, wenn vorher Idle war

                // Produktion und Standby nicht erlauben, wenn in der ersten Periode Idle aktiv war
                cplex.addEq(yVars.get(t).get(State.PRODUCTION), 0);  // Kein direkter Wechsel zu Produktion
                cplex.addEq(yVars.get(t).get(State.STANDBY), 0);     // Kein direkter Wechsel zu Stanydby

                // Idle kann weiterhin erlaubt sein, wenn Idle in der ersten Periode aktiv war
                cplex.addLe(yVars.get(t).get(State.IDLE),
                        yVars.get(firstPeriod).get(State.IDLE)); // Idle darf beibehalten werden
            }
        }
        
//        //Mindesthalterdauern 
		for (State s : State.values()) {
			Integer holdingDuration = params.holdingDurations.get(agent).get(s);
			if (holdingDuration != null) {
				for (Period t : periods) {
					if (t.t >= holdingDuration && t.t >= 2) { 
						for (int tau = 1; tau <= holdingDuration - 1; tau++) {
							if (t.t - tau >= 1) { // Ensure that the period exists
								Period prevPeriod = new Period(t.t - 1);
								Period futurePeriod = new Period(t.t - tau);
								cplex.addLe(cplex.diff(yVars.get(t).get(s), yVars.get(prevPeriod).get(s)),
										yVars.get(futurePeriod).get(s));
							}
						}
					}
				}
			}
		}

        // Definieren Sie den quadratischen Strafterm
        IloNumExpr quadraticPenalty = cplex.constant(0);

        // Iterieren Sie über alle Perioden
        for (Period t : periods) {
            int periodIndex = t.getT() - 1;

            // Zugriff auf die X-Werte für die aktuelle Periode
            double xValue = xValues[periodIndex]; 

            // Constraint 1 Komponenten
            double s1Value = sValues[periodIndex][0]; // S_{a,t}^1
            double opMin = params.minOperation.get(agent);

            // Residual für constraint 1
            IloNumExpr residual1 = cplex.sum(
                cplex.constant(-xValue),
                cplex.prod(opMin, yVars.get(t).get(State.PRODUCTION)),
                cplex.constant(s1Value),
                cplex.constant(uValues[periodIndex][0])
            );
            
            // Constraint 2 Komponenten
            double s2Value = sValues[periodIndex][1]; // S_{a,t}^2
            double opMax = params.maxOperation.get(agent);

            // Residual für constraint 2
            IloNumExpr residual2 = cplex.sum(
                cplex.constant(xValue),
                cplex.prod(-opMax, yVars.get(t).get(State.PRODUCTION)),
                cplex.constant(s2Value),
                cplex.constant(uValues[periodIndex][1])
            );

            // Erstelle einen linearen Ausdruck für die Summe der y-Variablen über alle Zustände
            IloLinearNumExpr ySum = cplex.linearNumExpr();
            for (State s : State.values()) {
                ySum.addTerm(1.0, yVars.get(t).get(s));
            }

            // Residual für die Bedingung: Summe der y-Variablen muss 1 sein
            IloNumExpr yResidual = cplex.diff(ySum, 1);

        	IloNumExpr squaredResidual1 = cplex.prod(residual1, residual1);
			IloNumExpr squaredResidual2 = cplex.prod(residual2, residual2);
			IloNumExpr squaredResidual3 = cplex.prod(yResidual, yResidual);
			
			quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(30*rho / 2, squaredResidual1));
			quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(30*rho / 2, squaredResidual2));
			quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(rho / 2, squaredResidual3));
        }
        
        // Add the objective function and quadratic penalty to the model
        cplex.addMinimize(cplex.sum(objective, quadraticPenalty));

        // Solve the optimization problem
        if (cplex.solve()) {
            System.err.println(ANSI_GREEN + "Iteration " + iteration + ": y-Optimization for Agent " + agent.getId() + " solved successfully." + ANSI_RESET);

            // Extract and update Y values for the current agent
            boolean[][] updatedYValues = new boolean[numPeriods][State.values().length];
            for (Period t : periods) {
                for (State s : State.values()) {
                    try {
                        double yValue = cplex.getValue(yVars.get(t).get(s));
                        updatedYValues[t.getT() - 1][s.ordinal()] = yValue > 0.5; // Assumption: 1.0 is true and 0.0 is false
                    } catch (IloException e) {
                        System.err.println("Error retrieving value for Agent " + agent.getId() + ", Period " + t.getT() + ", State " + s);
                        e.printStackTrace();
                    }
                }
            }

            // Update DataExchange with new Y values for the current agent
            dataExchange.saveYValuesForAgent(nextIteration, agentID, updatedYValues);

            // Output the optimal Y values
            System.out.println("Optimal Y values for Agent " + agent.getId() + ":");
            for (int tIndex = 0; tIndex < numPeriods; tIndex++) {
                System.out.print("Period " + (tIndex + 1) + ": ");
                boolean foundActiveState = false;

                for (State state : State.values()) {
                    if (updatedYValues[tIndex][state.ordinal()]) {
                        System.out.println("Active State = " + state);
                        foundActiveState = true;
                    }
                }

                if (!foundActiveState) {
                    System.out.println("No active state");
                }
            }

        } else {
            System.out.println("Solution status = " + cplex.getStatus());
            System.out.println("Solver status = " + cplex.getCplexStatus());
            System.out.println("No optimal solution found");
        }
    }
}