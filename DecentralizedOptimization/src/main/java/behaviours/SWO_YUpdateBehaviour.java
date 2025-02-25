package behaviours;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.gurobi.gurobi.*;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import models.ADMMDataModel;
import models.Electrolyzer;
import models.Parameters;
import models.Period;
import models.State;


public class SWO_YUpdateBehaviour extends OneShotBehaviour {

    private static final long serialVersionUID = 1L;
    final static String ANSI_RESET = "\u001B[0m";
    final static String ANSI_GREEN = "\u001B[32m";

    private GRBModel model;
    private Parameters params;
    private Set<Electrolyzer> electrolyzers;
    private Set<Period> periods;
    private int iteration;
    private ADMMDataModel dataModel;
    private double rho;
    private long yUpdateTime = 0;
    private int sentMessages = 0;
    private Predicate<Electrolyzer> filterCriteria;
    private int currentStartPeriod;

    private Map<Electrolyzer, Map<Period, Map<State, GRBVar>>> yVars;
    private Map<Electrolyzer, Map<Period, GRBVar>> residual1Vars; // Residuals for lower boundary
    private Map<Electrolyzer, Map<Period, GRBVar>> residual2Vars; // Residuals for upper boundary
    private Map<Electrolyzer, Map<Period, GRBVar>> residual3Vars; // Residuals for upper boundary
    
    public SWO_YUpdateBehaviour(GRBModel model, Parameters params, Set<Electrolyzer> electrolyzers, Set<Period> periods, int iteration, ADMMDataModel dataModel, double rho, Predicate<Electrolyzer> filterCriteria, int currentStartPeriod) {
        this.model = model;
        this.params = params;
        this.electrolyzers = electrolyzers;
        this.periods = periods;
        this.iteration = iteration;
        this.dataModel = dataModel;
        this.rho = rho;
        this.filterCriteria = filterCriteria;
        this.currentStartPeriod = currentStartPeriod;
//        this.residualVars = new HashMap<>();
        
        initializeVariablesAndConstraints();
    }

    private void initializeVariablesAndConstraints() {
        // Filtere die Elektrolyseure, die betrachtet werden sollen
        Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);
        try {
            yVars = new HashMap<>();
            residual1Vars = new HashMap<>();
            residual2Vars = new HashMap<>();
            residual3Vars = new HashMap<>();
            
            for (Electrolyzer e : electrolyzers) {
                yVars.put(e, new HashMap<>());
                residual1Vars.put(e, new HashMap<>());
                residual2Vars.put(e, new HashMap<>());
                residual3Vars.put(e, new HashMap<>());
                for (Period t : periods) {
                    yVars.get(e).put(t, new HashMap<>());
                    for (State s : State.values()) {
                        yVars.get(e).get(t).put(s, model.addVar(0, 1, 0, GRB.BINARY, "y_" + e.getId() + "_" + t.getT() + "_" + s));
                        residual1Vars.get(e).put(t, model.addVar(-GRB.INFINITY, GRB.INFINITY, 0, GRB.CONTINUOUS, "residual1_" + e.getId() + "_" + t.getT()));
                        residual2Vars.get(e).put(t, model.addVar(-GRB.INFINITY, GRB.INFINITY, 0, GRB.CONTINUOUS, "residual2_" + e.getId() + "_" + t.getT()));
                        residual3Vars.get(e).put(t, model.addVar(-GRB.INFINITY, GRB.INFINITY, 0, GRB.CONTINUOUS, "residual2_" + e.getId() + "_" + t.getT()));
                    }
                }
            }

            // Nebenbedingungen: Zustandstransitionen und Mindesthalterdauern
            for (Electrolyzer e : electrolyzers) {
                int electrolyzerID = e.getId() - 1;

                for (Period t : periods) {
                    if (t.getT() > 1) {
                        Period prevPeriod = new Period(t.getT() - 1);

                        // STARTING Transition
                        GRBLinExpr exprStarting = new GRBLinExpr();
                        exprStarting.addTerm(1.0, yVars.get(e).get(prevPeriod).get(State.IDLE));
                        exprStarting.addTerm(1.0, yVars.get(e).get(prevPeriod).get(State.STARTING));
                        model.addConstr(yVars.get(e).get(t).get(State.STARTING), GRB.LESS_EQUAL, exprStarting, "transition_STARTING_" + electrolyzerID + "_" + t.getT());

                        // PRODUCTION Transition
                        GRBLinExpr exprProduction = new GRBLinExpr();
                        exprProduction.addTerm(1.0, yVars.get(e).get(prevPeriod).get(State.STARTING));
                        exprProduction.addTerm(1.0, yVars.get(e).get(prevPeriod).get(State.PRODUCTION));
                        exprProduction.addTerm(1.0, yVars.get(e).get(prevPeriod).get(State.STANDBY));
                        model.addConstr(yVars.get(e).get(t).get(State.PRODUCTION), GRB.LESS_EQUAL, exprProduction, "transition_PRODUCTION_" + electrolyzerID + "_" + t.getT());

                        // STANDBY Transition
                        GRBLinExpr exprStandby = new GRBLinExpr();
                        exprStandby.addTerm(1.0, yVars.get(e).get(prevPeriod).get(State.PRODUCTION));
                        exprStandby.addTerm(1.0, yVars.get(e).get(prevPeriod).get(State.STANDBY));
                        model.addConstr(yVars.get(e).get(t).get(State.STANDBY), GRB.LESS_EQUAL, exprStandby, "transition_STANDBY_" + electrolyzerID + "_" + t.getT());

                        // IDLE Transition
                        GRBLinExpr exprIdle = new GRBLinExpr();
                        exprIdle.addTerm(1.0, yVars.get(e).get(prevPeriod).get(State.IDLE));
                        exprIdle.addTerm(1.0, yVars.get(e).get(prevPeriod).get(State.STANDBY));
                        exprIdle.addTerm(1.0, yVars.get(e).get(prevPeriod).get(State.PRODUCTION));
                        model.addConstr(yVars.get(e).get(t).get(State.IDLE), GRB.LESS_EQUAL, exprIdle, "transition_IDLE_" + electrolyzerID + "_" + t.getT());
                    }

                    // Mindesthalterdauern
                    for (State s : State.values()) {
                        Integer holdingDuration = params.holdingDurations.get(e).get(s);
                        if (holdingDuration != null && t.getT() >= holdingDuration) {
                            for (int tau = 1; tau < holdingDuration; tau++) {
                                if (t.getT() - tau >= 1) {
                                    Period futurePeriod = new Period(t.getT() - tau);
                                    GRBLinExpr expr = new GRBLinExpr();
                                    expr.addTerm(1.0, yVars.get(e).get(t).get(s));
                                    expr.addTerm(-1.0, yVars.get(e).get(futurePeriod).get(s));
                                    model.addConstr(expr, GRB.LESS_EQUAL, 0, "holding_duration_" + electrolyzerID + "_" + t.getT() + "_" + s);
                                }
                            }
                        }
                    }

                    // Constraints for the second period 
                    if (t.getT() == 2) {
                        Period firstPeriod = new Period(1);

                        // STARTING only if the previous period was IDLE
                        model.addConstr(yVars.get(e).get(t).get(State.STARTING), GRB.LESS_EQUAL,
                                yVars.get(e).get(firstPeriod).get(State.IDLE),
                                "starting_if_idle_" + electrolyzerID + "_" + t.getT());

                        // No PRODUCTION if the previous period was IDLE
                        model.addConstr(yVars.get(e).get(t).get(State.PRODUCTION), GRB.EQUAL, 0,
                                "no_production_if_idle_" + electrolyzerID + "_" + t.getT());

                        // No STANDBY if the previous period was IDLE
                        model.addConstr(yVars.get(e).get(t).get(State.STANDBY), GRB.EQUAL, 0,
                                "no_standby_if_idle_" + electrolyzerID + "_" + t.getT());

                        // IDLE must continue if it was IDLE in the first period
                        model.addConstr(yVars.get(e).get(t).get(State.IDLE), GRB.LESS_EQUAL,
                                yVars.get(e).get(firstPeriod).get(State.IDLE),
                                "idle_continued_" + electrolyzerID + "_" + t.getT());
                    }
                }
            }

            model.update(); // Apply variable and constraint updates
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void action() {
    	System.out.println("y-SWO-Update von " + myAgent.getLocalName() + " in Iteration: " + iteration + " in Startperiode: " + currentStartPeriod);
    	
        long startTime = System.nanoTime();
        try {
            optimizeY();
            yUpdateTime = System.nanoTime() - startTime;
            dataModel.saveYUpdateTimeForIteration(iteration, yUpdateTime);
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }
    

    private void optimizeY() throws GRBException {
    	int nextIteration = iteration + 1;
        GRBQuadExpr objectiveWithPenalty = new GRBQuadExpr();
        
        for (Electrolyzer e : electrolyzers) {
            int electrolyzerID = e.getId() - 1;
            double[][] sValues = dataModel.getSSWOValuesForAgent(iteration, electrolyzerID);
            double[][] uValues = dataModel.getUSWOValuesForAgent(iteration, electrolyzerID);
            double[] xValues = dataModel.getXSWOValuesForAgent(nextIteration, electrolyzerID);

            for (Period t : periods) {
                int periodIndex = t.getT() - 1;

                // Residuals 1 for min operation
                GRBLinExpr residual1 = new GRBLinExpr();
                residual1.addConstant(-xValues[periodIndex]);
                residual1.addTerm(params.minOperation.get(e), yVars.get(e).get(t).get(State.PRODUCTION));
                residual1.addConstant(sValues[periodIndex][0] + uValues[periodIndex][0]);

                model.addConstr(residual1Vars.get(e).get(t), GRB.EQUAL, residual1, "residual1_constr_" + electrolyzerID + "_" + t.getT());
                objectiveWithPenalty.addTerm(rho, residual1Vars.get(e).get(t), residual1Vars.get(e).get(t));
                
                // Residuals 2 for max operation
                GRBLinExpr residual2 = new GRBLinExpr();
                residual2.addConstant(xValues[periodIndex]);
                residual2.addTerm(-params.maxOperation.get(e), yVars.get(e).get(t).get(State.PRODUCTION));
                residual2.addConstant(sValues[periodIndex][1] + uValues[periodIndex][1]);

                model.addConstr(residual2Vars.get(e).get(t), GRB.EQUAL, residual2, "residual2_constr_" + electrolyzerID + "_" + t.getT());
                objectiveWithPenalty.addTerm(rho, residual2Vars.get(e).get(t), residual2Vars.get(e).get(t));

                // Residual for sum of y variables being 1
                GRBLinExpr ySum = new GRBLinExpr();
                for (State state : State.values()) {
                    ySum.addTerm(1.0, yVars.get(e).get(t).get(state));
                }
                GRBLinExpr yResidual = new GRBLinExpr();
                yResidual.add(ySum);
                yResidual.addConstant(-1);

                model.addConstr(residual3Vars.get(e).get(t), GRB.EQUAL, yResidual, "yResidual_constr_" + electrolyzerID + "_" + t.getT());
                objectiveWithPenalty.addTerm(rho, residual3Vars.get(e).get(t), residual3Vars.get(e).get(t));
            }
        }

        model.setObjective(objectiveWithPenalty, GRB.MINIMIZE);
        model.getEnv().set(GRB.IntParam.Method, 2);
        model.getEnv().set(GRB.DoubleParam.OptimalityTol, 1e-3);
        model.getEnv().set(GRB.IntParam.OutputFlag, 0); // Deaktiviert alle Ausgaben
        model.optimize();

        if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
            saveResults(nextIteration);
            
            // Speichern des aktuellen Y-Objective-Werts
            double yObjectiveValue = model.get(GRB.DoubleAttr.ObjVal);
            dataModel.saveYObjective(iteration, yObjectiveValue);

         // Speicherung der Residual-Werte
            for (Electrolyzer e : electrolyzers) {
                int electrolyzerID = e.getId() - 1;

                for (Period t : periods) {
                    int periodIndex = t.getT() - 1;

                    // Residual 1 Wert
                    double residual1Value = residual1Vars.get(e).get(t).get(GRB.DoubleAttr.X);

                    // Residual 2 Wert
                    double residual2Value = residual2Vars.get(e).get(t).get(GRB.DoubleAttr.X);

                    // Residual 3 (yResidual) Wert
                    double residual3Value = residual3Vars.get(e).get(t).get(GRB.DoubleAttr.X);

                    // Array für die Residual-Werte in der Datenstruktur speichern
                    double[] residuals = new double[]{residual1Value, residual2Value, residual3Value};
                    dataModel.saveYResiduals(iteration, electrolyzerID, periodIndex, residuals);
            }
        }
        }
    }

    private void saveResults(int nextIteration) throws GRBException {
        for (Electrolyzer e : electrolyzers) {
            int agentID = e.getId() - 1;
            boolean[][] updatedYValues = new boolean[periods.size()][State.values().length];

            for (Period t : periods) {
                int periodIndex = t.getT() - 1;
                for (State s : State.values()) {
                    double yValue = yVars.get(e).get(t).get(s).get(GRB.DoubleAttr.X);
                    updatedYValues[periodIndex][s.ordinal()] = yValue > 0.5;
                }
            }

            dataModel.saveYSWOValuesForAgent(nextIteration, agentID, updatedYValues);
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
}