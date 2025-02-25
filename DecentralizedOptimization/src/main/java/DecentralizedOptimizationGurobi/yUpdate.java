package DecentralizedOptimizationGurobi;

import com.gurobi.gurobi.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class yUpdate {
    /**
     * Global Variables
     */
    final static String ANSI_RESET = "\u001B[0m";
    final static String ANSI_GREEN = "\u001B[32m";

    public void optimizeY(GRBModel model, Parameters params, Agent agent, Set<Period> periods, int iteration, DataExchange dataExchange, double rho) throws GRBException {
        System.err.println("-- y-Update for Agent " + agent.getId() + " --");

        int numPeriods = periods.size();
        int nextIteration = iteration + 1;
        int agentID = agent.getId() - 1;

        // Define the variables for the current optimization problem
        Map<Period, Map<State, GRBVar>> yVars = new HashMap<>();

        // Initialize variables for each period
        for (Period t : periods) {
            yVars.put(t, new HashMap<>());
            for (State s : State.values()) {
                yVars.get(t).put(s, model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y_" + t.getT() + "_" + s));
            }
        }

        // Define the objective function
        GRBLinExpr objective = new GRBLinExpr();
        for (Period t : periods) {
            double startupCost = params.startupCost.get(agent);
            double standbyCost = params.standbyCost.get(agent);

            // Start-Up Costs
            objective.addTerm(params.intervalLength * startupCost, yVars.get(t).get(State.STARTING));

            // Standby Costs
            objective.addTerm(params.intervalLength * standbyCost, yVars.get(t).get(State.STANDBY));
        }

        // Zugriff auf X, S und U Werte für die aktuelle Iteration
        double[] xValues = dataExchange.getXValuesForAgent(nextIteration, agentID);
        double[][] sValues = dataExchange.getSValuesForAgent(iteration, agentID);
        double[][] uValues = dataExchange.getUValuesForAgent(iteration, agentID);

        // Statusübergänge
        for (Period t : periods) {
            if (t.getT() > 1) {
                Period prevPeriod = new Period(t.getT() - 1);
                int startingHoldingDuration = params.holdingDurations.get(agent).get(State.STARTING);
                Period startupPeriod = new Period(t.getT() - startingHoldingDuration);

                // Ausdruck für STARTING Zustand
                GRBLinExpr exprStarting = new GRBLinExpr();
                exprStarting.addTerm(1.0, yVars.get(prevPeriod).get(State.IDLE));
                exprStarting.addTerm(1.0, yVars.get(prevPeriod).get(State.STARTING));
                model.addConstr(yVars.get(t).get(State.STARTING), GRB.LESS_EQUAL, exprStarting, "transition_STARTING_" + t.getT());

                // Ausdruck für PRODUCTION Zustand
                GRBLinExpr exprProduction = new GRBLinExpr();
                exprProduction.addTerm(1.0, yVars.get(startupPeriod).get(State.STARTING));
                exprProduction.addTerm(1.0, yVars.get(prevPeriod).get(State.PRODUCTION));
                exprProduction.addTerm(1.0, yVars.get(prevPeriod).get(State.STANDBY));
                model.addConstr(yVars.get(t).get(State.PRODUCTION), GRB.LESS_EQUAL, exprProduction, "transition_PRODUCTION_" + t.getT());

                // Ausdruck für STANDBY Zustand
                GRBLinExpr exprStandby = new GRBLinExpr();
                exprStandby.addTerm(1.0, yVars.get(prevPeriod).get(State.PRODUCTION));
                exprStandby.addTerm(1.0, yVars.get(prevPeriod).get(State.STANDBY));
                model.addConstr(yVars.get(t).get(State.STANDBY), GRB.LESS_EQUAL, exprStandby, "transition_STANDBY_" + t.getT());

                // Ausdruck für IDLE Zustand
                GRBLinExpr exprIdle = new GRBLinExpr();
                exprIdle.addTerm(1.0, yVars.get(prevPeriod).get(State.PRODUCTION));
                exprIdle.addTerm(1.0, yVars.get(prevPeriod).get(State.STANDBY));
                exprIdle.addTerm(1.0, yVars.get(prevPeriod).get(State.IDLE));
                model.addConstr(yVars.get(t).get(State.IDLE), GRB.LESS_EQUAL, exprIdle, "transition_IDLE_" + t.getT());
            }

            // Besondere Nebenbedingung für die zweite Periode
            if (t.getT() == 2) {
                Period firstPeriod = new Period(1);

                model.addConstr(yVars.get(t).get(State.STARTING), GRB.LESS_EQUAL,
                        yVars.get(firstPeriod).get(State.IDLE),
                        "starting_if_idle_" + t.getT());

                model.addConstr(yVars.get(t).get(State.PRODUCTION), GRB.EQUAL, 0,
                        "no_production_if_idle_" + t.getT());
                model.addConstr(yVars.get(t).get(State.STANDBY), GRB.EQUAL, 0,
                        "no_standby_if_idle_" + t.getT());

                model.addConstr(yVars.get(t).get(State.IDLE), GRB.LESS_EQUAL,
                        yVars.get(firstPeriod).get(State.IDLE),
                        "idle_continued_" + t.getT());
            }
        }

        // Mindesthalterdauern
        for (State s : State.values()) {
            Integer holdingDuration = params.holdingDurations.get(agent).get(s);
            if (holdingDuration != null) {
                for (Period t : periods) {
                    if (t.getT() >= holdingDuration && t.getT() >= 2) {
                        for (int tau = 1; tau <= holdingDuration - 1; tau++) {
                            if (t.getT() - tau >= 1) {
                                Period prevPeriod = new Period(t.getT() - 1);
                                Period futurePeriod = new Period(t.getT() - tau);

                                // Erstellen des GRBLinExpr-Ausdrucks für die Einschränkung
                                GRBLinExpr expr = new GRBLinExpr();
                                expr.addTerm(1.0, yVars.get(t).get(s));
                                expr.addTerm(-1.0, yVars.get(prevPeriod).get(s));

                                // Hinzufügen der Einschränkung zum Modell
                                model.addConstr(expr, GRB.LESS_EQUAL, yVars.get(futurePeriod).get(s), "holding_duration_" + t.getT() + "_" + s);
                            }
                        }
                    }
                }
            }
        }

     // Definieren Sie den quadratischen Strafterm
        GRBQuadExpr quadraticPenalty = new GRBQuadExpr();

        for (Period t : periods) {
            int periodIndex = t.getT() - 1;

            double xValue = xValues[periodIndex];
            double s1Value = sValues[periodIndex][0];
            double opMin = params.minOperation.get(agent);

            // Residual 1
            GRBLinExpr residual1 = new GRBLinExpr();
            residual1.addConstant(-xValue);
            residual1.addTerm(opMin, yVars.get(t).get(State.PRODUCTION));
            residual1.addConstant(s1Value);
            residual1.addConstant(uValues[periodIndex][0]);

            // Residual 2
            double s2Value = sValues[periodIndex][1];
            double opMax = params.maxOperation.get(agent);

            GRBLinExpr residual2 = new GRBLinExpr();
            residual2.addConstant(xValue);
            residual2.addTerm(-opMax, yVars.get(t).get(State.PRODUCTION));
            residual2.addConstant(s2Value);
            residual2.addConstant(uValues[periodIndex][1]);

            // Residual für die Bedingung: Summe der y-Variablen muss 1 sein
            GRBLinExpr ySum = new GRBLinExpr();
            for (State s : State.values()) {
                ySum.addTerm(1.0, yVars.get(t).get(s));
            }
            GRBLinExpr yResidual = new GRBLinExpr();
            yResidual.add(ySum);
            yResidual.addConstant(-1);

            // Umwandeln der linearen Ausdrücke in Variablen, um quadratische Terme zu definieren
            GRBVar residual1Var = model.addVar(-GRB.INFINITY, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "residual1_" + t.getT());
            GRBVar residual2Var = model.addVar(-GRB.INFINITY, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "residual2_" + t.getT());
            GRBVar yResidualVar = model.addVar(-GRB.INFINITY, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "yResidual_" + t.getT());

            // Hinzufügen der Nebenbedingungen, um die Residualvariablen mit den berechneten linearen Ausdrücken gleichzusetzen
            model.addConstr(residual1Var, GRB.EQUAL, residual1, "residual1_constr_" + t.getT());
            model.addConstr(residual2Var, GRB.EQUAL, residual2, "residual2_constr_" + t.getT());
            model.addConstr(yResidualVar, GRB.EQUAL, yResidual, "yResidual_constr_" + t.getT());

            // Quadratische Strafterme hinzufügen
            quadraticPenalty.addTerm(30*rho / 2, residual1Var, residual1Var);
            quadraticPenalty.addTerm(30*rho / 2, residual2Var, residual2Var);
            quadraticPenalty.addTerm(rho / 2, yResidualVar, yResidualVar);
        }

        // Kombinieren Sie die lineare Zielfunktion mit dem quadratischen Strafterm
        GRBQuadExpr objectiveWithPenalty = new GRBQuadExpr();
        objectiveWithPenalty.add(quadraticPenalty);
        objectiveWithPenalty.add(objective);

        // Setzen Sie die Zielfunktion im Modell
        model.setObjective(objectiveWithPenalty, GRB.MINIMIZE);

        model.optimize();

        if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
            System.err.println(ANSI_GREEN + "Iteration " + iteration + ": y-Optimization for Agent " + agent.getId() + " solved successfully." + ANSI_RESET);

            boolean[][] updatedYValues = new boolean[numPeriods][State.values().length];
            for (Period t : periods) {
                for (State s : State.values()) {
                    double yValue = yVars.get(t).get(s).get(GRB.DoubleAttr.X);
                    updatedYValues[t.getT() - 1][s.ordinal()] = yValue > 0.5;
                }
            }

            dataExchange.saveYValuesForAgent(nextIteration, agentID, updatedYValues);

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
            System.out.println("No optimal solution found.");
        }
    }
}
