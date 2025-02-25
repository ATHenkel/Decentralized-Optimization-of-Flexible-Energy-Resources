package behavioursCPLEX;

import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import models.Electrolyzer;
import models.Parameters;
import models.Period;
import models.State;
import models.ADMMDataModel;
import ilog.cplex.IloCplex;
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class XUpdateBehaviour extends OneShotBehaviour {
	
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

    // Konstruktor: Übergabe der benötigten Parameter für das xUpdate
    public XUpdateBehaviour(IloCplex cplex, Parameters params, Set<Electrolyzer> electrolyzers, Set<Period> periods, int iteration, ADMMDataModel dataModel, double rho) {
        this.cplex = cplex;
        this.params = params;
        this.electrolyzers = electrolyzers;
        this.periods = periods;
        this.iteration = iteration;
        this.dataModel = dataModel;
        this.rho = rho;
    }

    @Override
    public void action() {
        try {
            // Führe das xUpdate durch
            optimizeX();

            // Nach erfolgreicher Optimierung, sende die gebündelten Ergebnisse
            sendBundledXUpdateResults(electrolyzers, periods, iteration); 
        } catch (IloException e) {
            e.printStackTrace();
        }
    }

    private void optimizeX() throws IloException {
        System.out.println("-- xUpdate for all electrolyzers --");
        
        cplex.clearModel();

        int nextIteration = iteration + 1;
        Map<Electrolyzer, Map<Period, IloNumVar>> xVars = new HashMap<>();

        // Initialize x-Variables für jeden Elektrolyseur
        for (Electrolyzer e : electrolyzers) {
            xVars.put(e, new HashMap<>());
            for (Period t : periods) {
                double powerElectrolyzer = e.getPowerElectrolyzer();

                // Definiere die Variable x für jeden Elektrolyseur und jede Periode
                xVars.get(e).put(t, cplex.numVar(0, powerElectrolyzer, IloNumVarType.Float, "x_" + e.getId() + "_" + t.getT()));
            }
        }

        // Define the objective function for all electrolyzers
        IloLinearNumExpr objective = cplex.linearNumExpr();
        for (Electrolyzer e : electrolyzers) {
            for (Period t : periods) {
                double powerElectrolyzer = e.getPowerElectrolyzer();
                double electricityPrice = params.electricityCost.get(t);
                double intervalLength = params.intervalLengthSWO;

                // f^V(X) for each Electrolyzer
                objective.addTerm(powerElectrolyzer * electricityPrice * intervalLength, xVars.get(e).get(t));
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
            for (Electrolyzer e : electrolyzers) {
                double slope = params.slope.get(e);
                double intercept = params.intercept.get(e);
                double powerElectrolyzer = params.powerElectrolyzer.get(e);
                double intervalLength = params.intervalLengthSWO;
                boolean[][] yValues = dataModel.getYSWOValuesForAgent(iteration, e.getId() - 1); 
                double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;

                // Berechnung: intervalLength * (powerElectrolyzer * slope * xVars + intercept)
                IloNumExpr electrolyzerProduction = cplex.prod(intervalLength,
                    cplex.sum(
                        cplex.prod(powerElectrolyzer * slope, xVars.get(e).get(t)), // powerElectrolyzer * slope * xVars
                        cplex.constant(intercept * productionYValue) // + intercept
                    )
                );

                // Summiere die Produktionsmenge für den Elektrolyseur zur Gesamtproduktion hinzu
                productionInPeriod = cplex.sum(productionInPeriod, electrolyzerProduction);
            }

            // Add deviations constraints per period
            cplex.addGe(positiveDeviations.get(t), cplex.diff(productionInPeriod, periodDemand));
            cplex.addGe(negativeDeviations.get(t), cplex.diff(periodDemand, productionInPeriod));

            // Add the absolute deviation per period to the objective function
            objective.addTerm(demandDeviationCost, positiveDeviations.get(t));
            objective.addTerm(demandDeviationCost, negativeDeviations.get(t));
        }

        // Access Y, S, and U values for current iteration from ADMMDataModel
        IloNumExpr quadraticPenalty = cplex.constant(0);
        for (Electrolyzer e : electrolyzers) {
            boolean[][] yValues = dataModel.getYSWOValuesForAgent(iteration, e.getId() - 1); 
            double[][] sValues = dataModel.getSSWOValuesForAgent(iteration, e.getId() - 1); 
            double[][] uValues = dataModel.getUSWOValuesForAgent(iteration, e.getId() - 1); 

            for (Period t : periods) {
                int periodIndex = t.getT() - 1;

                // Constraint 1 components for this Electrolyzer
                double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;
                double opMin = params.minOperation.get(e);

                IloNumExpr residual1 = cplex.sum(
                    cplex.prod(-1, xVars.get(e).get(t)),
                    cplex.constant(opMin * productionYValue),
                    cplex.constant(sValues[periodIndex][0]),
                    cplex.constant(uValues[periodIndex][0])
                );

                // Constraint 2 components for this Electrolyzer
                double opMax = params.maxOperation.get(e);
                IloNumExpr residual2 = cplex.sum(
                    xVars.get(e).get(t),
                    cplex.constant(-opMax * productionYValue),
                    cplex.constant(sValues[periodIndex][1]),
                    cplex.constant(uValues[periodIndex][1])
                );

                // Square the residual and add to quadratic penalty
                IloNumExpr squaredResidual1 = cplex.prod(residual1, residual1);
                IloNumExpr squaredResidual2 = cplex.prod(residual2, residual2);
                quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(rho / 2, squaredResidual1));
                quadraticPenalty = cplex.sum(quadraticPenalty, cplex.prod(rho / 2, squaredResidual2));
            }
        }

        // Add the quadratic penalty term to the objective function
        cplex.addMinimize(cplex.sum(objective, quadraticPenalty));

        // Solve the optimization problem
        if (cplex.solve()) {
            System.err.println(ANSI_GREEN +"Agent: " +this.myAgent.getLocalName() +  " Iteration " + iteration + ": x-Optimization solved successfully for all electrolyzers." + ANSI_RESET);

            for (Electrolyzer e : electrolyzers) {
//                System.out.println("Electrolyzer: " + e.getId());
                for (Period t : periods) {
                	
                    // Get the optimized X-value for the electrolyzer and the specific period
                    double xValue = cplex.getValue(xVars.get(e).get(t));
                    double intercept = params.intercept.get(e);
                    int electrolyzerID = e.getId() - 1;
                    int periodIndex = t.getT() - 1;
                    double powerElectrolyzer = params.powerElectrolyzer.get(e);
                    double intervalLength = params.intervalLengthSWO;
                    double slope = params.slope.get(e);
                    boolean[][] yValues = dataModel.getYSWOValuesForAgent(iteration, e.getId() - 1); // Zugriff auf ADMMDataModel
                    double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;

                    // Save Optimization Results in ADMMDataModel
                    dataModel.saveXSWOValueForPeriod(nextIteration, electrolyzerID, periodIndex, xValue);
                    double hydrogenProduction = intervalLength * (slope * powerElectrolyzer * xValue + intercept * productionYValue);
                    dataModel.saveHydrogenSWOProductionForPeriod(nextIteration, electrolyzerID, periodIndex, hydrogenProduction);
                }
            }

        } else {
            System.out.println("Solution status = " + cplex.getStatus());
            System.out.println("Solver status = " + cplex.getCplexStatus());
            System.out.println("No optimal solution found.");
        }
    }
    
    private void sendBundledXUpdateResults(Set<Electrolyzer> electrolyzers, Set<Period> periods, int iteration) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        
        // Erstelle eine Nachricht, die alle Ergebnisse bündelt
        StringBuilder content = new StringBuilder();
        content.append("xUpdateMessage;").append(iteration).append(";");
        
        // Für jeden Elektrolyseur und jede Periode füge die Ergebnisse zur Nachricht hinzu
        for (Electrolyzer e : electrolyzers) {
            for (Period t : periods) {
                double xValue = dataModel.getXSWOValueForAgentPeriod(iteration + 1, e.getId() - 1, t.getT() - 1);
                double hydrogenProduction = dataModel.getHydrogenSWOProductionForAgent(iteration + 1, e.getId() - 1)[t.getT() - 1];

                // Füge die Daten für den aktuellen Elektrolyseur und die Periode hinzu
                content.append(e.getId()).append(",")
                       .append(t.getT()).append(",")
                       .append(xValue).append(",")
                       .append(hydrogenProduction).append(";");
            }
        }
        // Setze den Nachrichtentext
        msg.setContent(content.toString());
       
        // Nachricht an alle Empfänger im Telefonbuch aus dem ADMMDataModel senden
        List<AID> phoneBook = dataModel.getPhoneBook();
        
        
     // Füge alle Empfänger hinzu
        StringBuilder recipients = new StringBuilder(); // StringBuilder für Ausgabe aller Empfänger

        for (AID recipient : phoneBook) {
            if (!recipient.equals(myAgent.getAID())) {
                msg.addReceiver(recipient);
                recipients.append(recipient.getLocalName()).append(" "); // Empfänger zum StringBuilder hinzufügen
//                System.out.println("Agent: " + this.myAgent.getLocalName() + " fügt Empfänger hinzu: " + recipient.getLocalName());
            }
        }

        // Ausgabe der gesamten Liste der Empfänger
        if (msg.getAllReceiver().hasNext()) {
            System.out.println("Agent: " + this.myAgent.getLocalName() + " Nachricht wird an folgende Empfänger gesendet: " + recipients.toString());

            // Nachricht senden
            myAgent.send(msg);
            System.out.println("Agent: " + this.myAgent.getLocalName() + " Nachricht gesendet.");
        } else {
            System.out.println("Keine Empfänger gefunden, Nachricht wurde nicht gesendet.");
        }

    }

}
