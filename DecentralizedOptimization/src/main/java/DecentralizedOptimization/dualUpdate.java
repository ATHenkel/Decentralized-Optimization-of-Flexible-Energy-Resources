package DecentralizedOptimization;

import ilog.concert.*;
import ilog.cplex.*;
import java.util.*;


public class dualUpdate {
    /**
     * Global Variables
     */
    final static String ANSI_RESET = "\u001B[0m";
    final static String ANSI_GREEN = "\u001B[32m";

    public void optimizeU(IloCplex cplex, Parameters params, Agent agent, Set<Period> periods, int iteration, DataExchange dataExchange, double rho) throws IloException {
        System.err.println("-- U-Update for Agent " + agent.getId() + " --");

        int numPeriods = periods.size();
        int nextIteration = iteration + 1;
        int agentID = agent.getId() - 1;

     // Zugriff auf X, Y, S Werte für die aktuelle Iteration
        double[] xValues = dataExchange.getXValuesForAgent(nextIteration, agentID);
        boolean[][] yValues = dataExchange.getYValuesForAgent(nextIteration, agentID);
        double[][] sValues = dataExchange.getSValuesForAgent(nextIteration, agentID);
        double[][] uValues = dataExchange.getUValuesForAgent(iteration, agentID);  

        // Speicher für neue U-Werte (nur ein Wert pro Periode)
        double[][] newUValues = new double[numPeriods][4];

        for (Period t : periods) {
            int periodIndex = t.getT() - 1;

            // Zugriff auf die Konstanten für die Berechnung
            double xValue = xValues[periodIndex];
            double productionYValue = yValues[periodIndex][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;
            double standbyYValue = yValues[periodIndex][State.STANDBY.ordinal()] ? 1.0 : 0.0;
            double startingYValue = yValues[periodIndex][State.STARTING.ordinal()] ? 1.0 : 0.0;
            double idleYValue = yValues[periodIndex][State.IDLE.ordinal()] ? 1.0 : 0.0;
            double opMin = params.minOperation.get(agent);
            double opMax = params.maxOperation.get(agent);

            
            // Zugriff auf den alten U-Wert
            double oldUValue[] = uValues[periodIndex];

            // Berechnung des Residuals
            double residual1 = -xValue + opMin * productionYValue + sValues[periodIndex][0] + oldUValue[0];
            double residual2 = xValue - opMax * productionYValue  + sValues[periodIndex][1] + oldUValue[1];
            double residual3 = productionYValue + standbyYValue + startingYValue + idleYValue + oldUValue[2];
            
            //TODO: Rampenrate festlegen: 
            double rampRate =  0.6;
            
            // Berechnung des Rampenresiduals, nur wenn es nicht die erste Periode ist
            double rampResidual = 0.0;
            if (periodIndex > 0) {
                double previousXValue = xValues[periodIndex - 1];
                // Berechnung der Differenz zwischen aktuellem und vorherigem x-Wert
                double xDiff = Math.abs(xValue - previousXValue);
                // Berechnung des Rampenresiduals: xDiff - Rampenrate * productionYValue
                rampResidual = xDiff - rampRate * productionYValue + oldUValue[3];
                rampResidual *= 0.5; // Beispielskala, um den Effekt zu dämpfen
            }
            
            newUValues[periodIndex][0] = residual1;
            newUValues[periodIndex][1] = residual2;
            newUValues[periodIndex][2] = residual3;
            newUValues[periodIndex][3] = rampResidual; 
        }

        // Speichern der neuen U-Werte für den aktuellen Agenten und die aktuelle Iteration
        dataExchange.saveUValuesForAgent(nextIteration, agentID, newUValues);
        System.err.println(ANSI_GREEN + "Iteration " + iteration + ": DualUpdate for Agent " + agent.getId() + " solved successfully." + ANSI_RESET);
    }
}