package behaviours;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.gurobi.gurobi.*;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import models.ADMMDataModel;
import models.Electrolyzer;
import models.Parameters;
import models.Period;
import models.State;

/**
 * SWO X-Update Behavior for ADMM optimization
 * Handles the X-variable updates in the SWO (Slack-Warm-Start-Optimization) process
 */
public class SWO_XUpdateBehaviour extends OneShotBehaviour {

    // ============================================================================
    // CONSTANTS
    // ============================================================================
    private static final long serialVersionUID = 1L;
    private static final double SCALING_FACTOR = 99.0;
    private static final double SMALL_VALUE_THRESHOLD = 1e-6;
    private static final double OPTIMALITY_TOLERANCE = 1e-6;

    // ============================================================================
    // INSTANCE VARIABLES
    // ============================================================================
    
    // Core components
    private final GRBModel model;
    private final Parameters params;
    private final Set<Electrolyzer> electrolyzers;
    private final Set<Period> periods;
    private final int iteration;
    private final ADMMDataModel dataModel;
    private final double rho; // Weighting factor for penalty terms
    private final int currentStartPeriod;
    
    // Performance tracking
    private long xUpdateTime = 0;
    private int sentMessages = 0;
    
    // Optimization variables
    private Map<Electrolyzer, Map<Period, GRBVar>> xVars;
    private Map<Electrolyzer, Map<Period, GRBVar>> residual1Vars; // Residuals for lower boundary
    private Map<Electrolyzer, Map<Period, GRBVar>> residual2Vars; // Residuals for upper boundary
    private Map<Period, GRBVar> positiveDeviations; // Demand deviation (positive)
    private Map<Period, GRBVar> negativeDeviations; // Demand deviation (negative)

    // ============================================================================
    // CONSTRUCTOR
    // ============================================================================
    
    public SWO_XUpdateBehaviour(GRBModel model, Parameters params, Set<Electrolyzer> electrolyzers, 
                                Set<Period> periods, int iteration, ADMMDataModel dataModel, 
                                double rho, int currentStartPeriod) {
        this.model = model;
        this.params = params;
        this.electrolyzers = electrolyzers;
        this.periods = periods;
        this.iteration = iteration;
        this.dataModel = dataModel;
        this.rho = rho;
        this.currentStartPeriod = currentStartPeriod;

        initializeVariablesAndConstraints();
    }

    // ============================================================================
    // INITIALIZATION METHODS
    // ============================================================================
    
    /**
     * Initializes all optimization variables and constraints
     */
    private void initializeVariablesAndConstraints() {
        try {
            initializeVariableMaps();
            initializeOptimizationVariables();
            initializeDemandDeviationConstraints();
            model.update(); // Apply variable and constraint updates
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Initializes the variable maps for storing optimization variables
     */
    private void initializeVariableMaps() {
        xVars = new HashMap<>();
        residual1Vars = new HashMap<>();
        residual2Vars = new HashMap<>();
        positiveDeviations = new HashMap<>();
        negativeDeviations = new HashMap<>();
    }
    
    /**
     * Creates optimization variables for each electrolyzer and period
     */
    private void initializeOptimizationVariables() throws GRBException {
        // Initialize variables for each electrolyzer and period
        for (Electrolyzer e : electrolyzers) {
            xVars.put(e, new HashMap<>());
            residual1Vars.put(e, new HashMap<>());
            residual2Vars.put(e, new HashMap<>());

            for (Period t : periods) {
                xVars.get(e).put(t, model.addVar(0, 1, 0, GRB.CONTINUOUS, "x_" + e.getId() + "_" + t.getT()));
                residual1Vars.get(e).put(t, model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "residual1_" + e.getId() + "_" + t.getT()));
                residual2Vars.get(e).put(t, model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "residual2_" + e.getId() + "_" + t.getT()));
            }
        }

        // Initialize deviation variables
        for (Period t : periods) {
            positiveDeviations.put(t, model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "positiveDeviation_" + t.getT()));
            negativeDeviations.put(t, model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "negativeDeviation_" + t.getT()));
        }
    }
    
    /**
     * Creates constraints for demand deviations
     */
    private void initializeDemandDeviationConstraints() throws GRBException {
        for (Period t : periods) {
            GRBLinExpr productionInPeriod = new GRBLinExpr();
            for (Electrolyzer e : electrolyzers) {
                double powerElectrolyzer = params.powerElectrolyzer.get(e);
                double slope = params.slope.get(e);
                double intervalLengthSWO = params.intervalLengthSWO;
                productionInPeriod.addTerm(powerElectrolyzer * slope * intervalLengthSWO, xVars.get(e).get(t));
            }

            double periodDemand = params.demand.get(t);

            // Positive deviation constraint
            GRBLinExpr positiveDeviationExpr = new GRBLinExpr();
            positiveDeviationExpr.addTerm(1.0, positiveDeviations.get(t));
            positiveDeviationExpr.addConstant(-periodDemand);
            positiveDeviationExpr.add(productionInPeriod);
            model.addConstr(positiveDeviationExpr, GRB.GREATER_EQUAL, 0.0, "positiveDeviationConstr_" + t.getT());

            // Negative deviation constraint
            GRBLinExpr negativeDeviationExpr = new GRBLinExpr();
            negativeDeviationExpr.addTerm(1.0, negativeDeviations.get(t));
            negativeDeviationExpr.addConstant(periodDemand);
            negativeDeviationExpr.multAdd(-1.0, productionInPeriod);
            model.addConstr(negativeDeviationExpr, GRB.GREATER_EQUAL, 0.0, "negativeDeviationConstr_" + t.getT());
        }
    }

    // ============================================================================
    // MAIN EXECUTION METHOD
    // ============================================================================
    
    @Override
    public void action() {
        dataModel.setStartXSWOUpdateComputationTime(System.nanoTime());
        
        try {
            optimizeX();
            xUpdateTime = System.nanoTime() - dataModel.getStartTimeXSWOComputationTime();
            dataModel.saveXUpdateTimeForIteration(iteration, xUpdateTime);
            sendBundledXUpdateResults();
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }
    
    // ============================================================================
    // OPTIMIZATION METHODS
    // ============================================================================
    
    /**
     * Performs the main X-optimization for the SWO update
     */
    private void optimizeX() throws GRBException {
        System.out.println("x-SWO-Update von " + myAgent.getLocalName() + " in Iteration: " + iteration + " in Startperiode: " + currentStartPeriod);

        int nextIteration = iteration + 1;
        
        try {
            // Build and set objective function
            GRBQuadExpr objectiveWithPenalty = buildObjectiveFunction();
            model.setObjective(objectiveWithPenalty, GRB.MINIMIZE);
            
            // Configure solver parameters
            configureSolverParameters();
            
            // Solve optimization problem
            model.optimize();

            if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
                saveResults(nextIteration);
                saveObjectiveAndPenaltyValues();
            } else {
                System.out.println("No optimal solution found.");
            }
        } catch (Exception e) {
            System.err.println("Error during X-optimization: " + e.getMessage());
            e.printStackTrace();
            throw new GRBException("X-optimization failed");
        }
    }
    
    /**
     * Builds the complete objective function including penalty terms
     */
    private GRBQuadExpr buildObjectiveFunction() throws GRBException {
        GRBLinExpr objective = buildLinearObjective();
        GRBQuadExpr quadraticPenalty = buildQuadraticPenalty();
        
        GRBQuadExpr objectiveWithPenalty = new GRBQuadExpr();
        objectiveWithPenalty.add(objective);
        objectiveWithPenalty.add(quadraticPenalty);
        
        return objectiveWithPenalty;
    }
    
    /**
     * Builds the linear part of the objective function
     */
    private GRBLinExpr buildLinearObjective() throws GRBException {
        GRBLinExpr objective = new GRBLinExpr();
        GRBLinExpr productionExpr = new GRBLinExpr();
        
        for (Period t : periods) {
            if (t.getT() >= currentStartPeriod) {
                double renewableEnergy = params.renewableEnergyForecast.getOrDefault(t, 0.0);
                double electricityPrice = params.electricityCost.get(t);

                for (Electrolyzer e : electrolyzers) {
                    double powerElectrolyzer = params.powerElectrolyzer.get(e);
                    double intervalLengthSWO = params.intervalLengthSWO;

                    // Add electricity cost
                    productionExpr.addTerm(electricityPrice * powerElectrolyzer * intervalLengthSWO, xVars.get(e).get(t));
                }

                // Subtract renewable energy
                productionExpr.addConstant(-renewableEnergy);
            }
        }
        
        objective.add(productionExpr);
        
        // Add penalty terms for demand deviations
        double demandDeviationCost = params.demandDeviationCost;
        for (Period t : periods) {
            if (t.getT() >= currentStartPeriod) {
                objective.addTerm(demandDeviationCost, positiveDeviations.get(t));
                objective.addTerm(demandDeviationCost, negativeDeviations.get(t));
            }
        }
        
        return objective;
    }
    
    /**
     * Builds the quadratic penalty terms for boundary residuals
     */
    private GRBQuadExpr buildQuadraticPenalty() throws GRBException {
        GRBQuadExpr quadraticPenalty = new GRBQuadExpr();
        
        for (Electrolyzer e : electrolyzers) {
            boolean[][] yValues = dataModel.getYSWOValuesForAgent(iteration, e.getId() - 1);
            double[][] sValues = dataModel.getSSWOValuesForAgent(iteration, e.getId() - 1);
            double[][] uValues = dataModel.getUSWOValuesForAgent(iteration, e.getId() - 1);
            double opMin = params.minOperation.get(e);
            double opMax = params.maxOperation.get(e);

            for (Period t : periods) {
                if (t.getT() >= currentStartPeriod) {
                    GRBVar xVar = xVars.get(e).get(t);
                    int periodIndex = t.getT() - 1;
                    double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;

                    // Create boundary constraints
                    try {
                        createBoundaryConstraints(e, t, xVar, periodIndex, productionYValue, opMin, opMax, sValues, uValues);
                    } catch (GRBException ex) {
                        System.err.println("Error creating boundary constraints: " + ex.getMessage());
                        ex.printStackTrace();
                        throw ex;
                    }
                    
                    // Add quadratic penalty terms
                    quadraticPenalty.addTerm(rho * SCALING_FACTOR, residual1Vars.get(e).get(t), residual1Vars.get(e).get(t));
                    quadraticPenalty.addTerm(rho * SCALING_FACTOR, residual2Vars.get(e).get(t), residual2Vars.get(e).get(t));
                }
            }
        }
        
        return quadraticPenalty;
    }
    
    /**
     * Creates the boundary constraints for residual variables
     */
    private void createBoundaryConstraints(Electrolyzer e, Period t, GRBVar xVar, int periodIndex, 
                                          double productionYValue, double opMin, double opMax, 
                                          double[][] sValues, double[][] uValues) throws GRBException {
        
        // Lower boundary residual: residual1 >= OpMin * yProduction - x
        GRBLinExpr lowerBoundaryConstraint = new GRBLinExpr();
        lowerBoundaryConstraint.addConstant(opMin * productionYValue);
        lowerBoundaryConstraint.addTerm(-1, xVar);
        lowerBoundaryConstraint.addConstant(sValues[periodIndex][0] + uValues[periodIndex][0]);
        model.addConstr(residual1Vars.get(e).get(t), GRB.GREATER_EQUAL, lowerBoundaryConstraint,
                "lowerBoundaryResidual_" + e.getId() + "_" + t.getT());

        // Upper boundary residual: residual2 >= x - OpMax * yProduction
        GRBLinExpr upperBoundaryConstraint = new GRBLinExpr();
        upperBoundaryConstraint.addTerm(-opMax * productionYValue, xVar);
        upperBoundaryConstraint.addConstant(sValues[periodIndex][1] + uValues[periodIndex][1]);
        model.addConstr(residual2Vars.get(e).get(t), GRB.GREATER_EQUAL, upperBoundaryConstraint,
                "upperBoundaryResidual_" + e.getId() + "_" + t.getT());
    }
    
    /**
     * Configures solver parameters for optimization
     */
    private void configureSolverParameters() throws GRBException {
        model.getEnv().set(GRB.DoubleParam.OptimalityTol, OPTIMALITY_TOLERANCE);
        model.getEnv().set(GRB.IntParam.OutputFlag, 0); // Disable all output
    }

    // ============================================================================
    // RESULT PROCESSING METHODS
    // ============================================================================
    
    /**
     * Saves optimization results for the next iteration
     */
    private void saveResults(int nextIteration) throws GRBException {
        for (Electrolyzer e : electrolyzers) {
            int agentID = e.getId() - 1;

            for (Period t : periods) {
                int periodIndex = t.getT() - 1;
                double xValue = xVars.get(e).get(t).get(GRB.DoubleAttr.X);

                if (Math.abs(xValue) < SMALL_VALUE_THRESHOLD) {
                    xValue = 0; // Threshold for small values
                }

                dataModel.saveXSWOValueForPeriod(nextIteration, agentID, periodIndex, xValue);
                double hydrogenProduction = params.intervalLengthSWO * (params.slope.get(e) * params.powerElectrolyzer.get(e) * xValue + params.intercept.get(e));
                dataModel.saveHydrogenSWOProductionForPeriod(nextIteration, agentID, periodIndex, hydrogenProduction);
            }
        }
    }
    
    /**
     * Saves objective and penalty values for analysis
     */
    private void saveObjectiveAndPenaltyValues() throws GRBException {
        // Save the current X-objective
        double xObjectiveValue = model.get(GRB.DoubleAttr.ObjVal);
        dataModel.saveXObjective(iteration, xObjectiveValue);

        // Calculate penalty value for debugging or storage
        double penaltyValue = 0.0;
        for (Electrolyzer e : electrolyzers) {
            for (Period t : periods) {
                penaltyValue += residual1Vars.get(e).get(t).get(GRB.DoubleAttr.X) * residual1Vars.get(e).get(t).get(GRB.DoubleAttr.X);
                penaltyValue += residual2Vars.get(e).get(t).get(GRB.DoubleAttr.X) * residual2Vars.get(e).get(t).get(GRB.DoubleAttr.X);
            }
        }
        dataModel.saveXPenaltyForIteration(iteration, penaltyValue);
    }

    // ============================================================================
    // COMMUNICATION METHODS
    // ============================================================================
    
    /**
     * Sends bundled X-update results to all other agents
     */
    private void sendBundledXUpdateResults() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        StringBuilder content = new StringBuilder();
        content.append("xUpdateMessage;").append(iteration).append(";");

        for (Electrolyzer e : electrolyzers) {
            for (Period t : periods) {
                double xValue = dataModel.getXSWOValueForAgentPeriod(iteration + 1, e.getId() - 1, t.getT() - 1);
                double hydrogenProduction = dataModel.getHydrogenSWOProductionForAgent(iteration + 1, e.getId() - 1)[t.getT() - 1];
                content.append(e.getId()).append(",").append(t.getT()).append(",").append(xValue).append(",").append(hydrogenProduction).append(";");
            }
        }

        msg.setContent(content.toString());
        for (AID recipient : dataModel.getPhoneBook()) {
            if (!recipient.equals(myAgent.getAID())) {
                msg.addReceiver(recipient);
                sentMessages++;
            }
        }
        myAgent.send(msg);
        dataModel.saveSentMessagesForIteration(iteration, sentMessages);
    }
}
