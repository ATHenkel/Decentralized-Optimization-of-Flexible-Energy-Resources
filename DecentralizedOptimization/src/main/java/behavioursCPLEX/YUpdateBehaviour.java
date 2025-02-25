package behavioursCPLEX;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate; // Verwende Predicate für Filterkriterien
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumExpr;
import ilog.cplex.IloCplex;
import jade.core.behaviours.OneShotBehaviour;
import models.*;

public class YUpdateBehaviour extends OneShotBehaviour {
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
    private Predicate<Electrolyzer> filterCriteria;

    // Konstruktor: Übergabe der benötigten Parameter für das yUpdate
    public YUpdateBehaviour(IloCplex cplex, Parameters params, Set<Electrolyzer> electrolyzers, Set<Period> periods, int iteration, ADMMDataModel dataModel, double rho, Predicate<Electrolyzer> filterCriteria) {
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
                optimizeYForElectrolyzer(electrolyzer);
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
    private void optimizeYForElectrolyzer(Electrolyzer electrolyzer) throws IloException {
        System.err.println("-- y-Update for Electrolyzer " + electrolyzer.getId() + " --");
        
        cplex.clearModel();

        int numPeriods = periods.size();
        int nextIteration = iteration + 1;
        int electrolyzerID = electrolyzer.getId() - 1;
        
        // Define the variables for the current optimization problem
        Map<Period, Map<State, IloIntVar>> yVars = new HashMap<>();

        // Initialize variables for each period
        for (Period t : periods) {
            yVars.put(t, new HashMap<>());
            for (State s : State.values()) {
                yVars.get(t).put(s, cplex.boolVar("y_" + t.getT() + "_" + s));
            }
        }

        // Define the objective function
        IloLinearNumExpr objective = cplex.linearNumExpr();
        for (Period t : periods) {
            double startupCost = params.startupCost.get(electrolyzer);
            double standbyCost = params.standbyCost.get(electrolyzer);

            // Start-Up Costs
            objective.addTerm(params.intervalLengthSWO * startupCost, yVars.get(t).get(State.STARTING));

            // Standby Costs
            objective.addTerm(params.intervalLengthSWO * standbyCost, yVars.get(t).get(State.STANDBY));
        }

        // Zugriff auf X, S, und U Werte für die aktuelle Iteration
        double[] xValues = dataModel.getXSWOValuesForAgent(nextIteration, electrolyzerID);
        double[][] sValues = dataModel.getSSWOValuesForAgent(iteration, electrolyzerID);
        double[][] uValues = dataModel.getUSWOValuesForAgent(iteration, electrolyzerID);

        // Statusübergänge
        for (Period t : periods) {
            if (t.getT() > 1) {
                Period prevPeriod = new Period(t.getT() - 1);
                int startingHoldingDuration = params.holdingDurations.get(electrolyzer).get(State.STARTING);
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
                cplex.addEq(yVars.get(t).get(State.STANDBY), 0);     // Kein direkter Wechsel zu Standby

                // Idle kann weiterhin erlaubt sein, wenn Idle in der ersten Periode aktiv war
                cplex.addLe(yVars.get(t).get(State.IDLE),
                        yVars.get(firstPeriod).get(State.IDLE)); // Idle darf beibehalten werden
            }
        }

        // Mindesthalterdauern
        for (State s : State.values()) {
            Integer holdingDuration = params.holdingDurations.get(electrolyzer).get(s);
            if (holdingDuration != null) {
                for (Period t : periods) {
                    if (t.getT() >= holdingDuration && t.getT() >= 2) {
                        for (int tau = 1; tau <= holdingDuration - 1; tau++) {
                            if (t.getT() - tau >= 1) { // Ensure that the period exists
                                Period prevPeriod = new Period(t.getT() - 1);
                                Period futurePeriod = new Period(t.getT() - tau);
                                cplex.addLe(cplex.diff(yVars.get(t).get(s), yVars.get(prevPeriod).get(s)),
                                        yVars.get(futurePeriod).get(s));
                            }
                        }
                    }
                }
            }
        }

        // Define the quadratic penalty term
        IloNumExpr quadraticPenalty = cplex.constant(0);

        // Iterate through all periods
        for (Period t : periods) {
            int periodIndex = t.getT() - 1;

            // Access the X-values for the current period
            double xValue = xValues[periodIndex];

            // Constraint 1 components
            double s1Value = sValues[periodIndex][0]; // S_{a,t}^1
            double opMin = params.minOperation.get(electrolyzer);

            // Residual for constraint 1
            IloNumExpr residual1 = cplex.sum(
                cplex.constant(-xValue),
                cplex.prod(opMin, yVars.get(t).get(State.PRODUCTION)),
                cplex.constant(s1Value),
                cplex.constant(uValues[periodIndex][0])
            );

            // Constraint 2 components
            double s2Value = sValues[periodIndex][1]; // S_{a,t}^2
            double opMax = params.maxOperation.get(electrolyzer);

            // Residual for constraint 2
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

            // Quadratischer Strafterm für alle Residuen
            IloNumExpr squaredResiduals = cplex.sum(
                cplex.prod(residual1, residual1),
                cplex.prod(residual2, residual2),
                cplex.prod(yResidual, yResidual)
            );

            // Addiere zur quadratischen Strafe
            quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(rho / 2, squaredResiduals));
        }

        // Add the objective function and quadratic penalty to the model
        cplex.addMinimize(cplex.sum(objective, quadraticPenalty));

        // Solve the optimization problem
        if (cplex.solve()) {
            System.err.println(ANSI_GREEN + "Agent: " + this.myAgent.getLocalName()  + " Iteration " + iteration + ": y-Optimization for Electrolyzer " + electrolyzer.getId() + " solved successfully." + ANSI_RESET);

            // Extract and update Y values for the current electrolyzer
            boolean[][] updatedYValues = new boolean[numPeriods][State.values().length];
            for (Period t : periods) {
                for (State s : State.values()) {
                    try {
                        double yValue = cplex.getValue(yVars.get(t).get(s));
                        updatedYValues[t.getT() - 1][s.ordinal()] = yValue > 0.5; // Assumption: 1.0 is true and 0.0 is false
                    } catch (IloException e) {
                        System.err.println("Error retrieving value for Electrolyzer " + electrolyzer.getId() + ", Period " + t.getT() + ", State " + s);
                        e.printStackTrace();
                    }
                }
            }

            // Update ADMMDataModel with new Y values for the current electrolyzer
            dataModel.saveYSWOValuesForAgent(nextIteration, electrolyzerID, updatedYValues);

            // Output the optimal Y values
//            System.out.println("Optimal Y values for Electrolyzer " + electrolyzer.getId() + ":");
//            for (int tIndex = 0; tIndex < numPeriods; tIndex++) {
//                System.out.print("Period " + (tIndex + 1) + ": ");
//                boolean foundActiveState = false;
//
//                for (State state : State.values()) {
//                    if (updatedYValues[tIndex][state.ordinal()]) {
//                        System.out.println("Active State = " + state);
//                        foundActiveState = true;
//                    }
//                }
//
//                if (!foundActiveState) {
//                    System.out.println("No active state");
//                }
//            }

        } else {
            System.out.println("Solution status = " + cplex.getStatus());
            System.out.println("Solver status = " + cplex.getCplexStatus());
            System.out.println("No optimal solution found");
        }
    }
}
