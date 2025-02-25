package plugfest;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

import com.gurobi.gurobi.*;

//TODO: 
//- Server einbauen
//- States schreiben können
//- Verschiedene OPC UA Adressen 

public class OptimizationModel_Plugfest_V2 {
	
	// Simulation parameters: Default interval is 90 seconds, but can be adjusted (e.g., 10 seconds for faster execution).
    private static final int SIMULATION_INTERVAL_SECONDS = 90;

    // OPC UA Server configuration
    private static final String OPC_UA_ADDRESS = "opc.tcp://127.0.0.1:8001";
    private static final String OPC_UA_NODE_H2FlowRate = "ns=2;s=H2FlowRate.V"; // Monitoring node (Nl/h)
    private static final String OPC_UA_NODE_H2Production_VOp = "ns=2;s=H2ProductionRate.VOp";
    private static final String OPC_UA_NODE_H2Production_ApplyOp = "ns=2;s=H2ProductionRate.ApplyOp";
    private static final String OPC_UA_NODE_H2Production_yOp = "ns=2;s=H2ProductionRate.yOp";

    // Piecewise linearization: Breakpoints (x-values) and production values (kg/h)
    private static final double[] breakpoints = {
            0.0, 0.1, 0.2, 0.3, 0.4, 0.5,
            0.6, 0.7, 0.8, 0.9, 1.0
        };

    // Updated production values (Version 13)
    private static final double[] productionValues = {
            0.0,          // x=0.0
            0.000670423,  // x=0.1
            0.005640204,  // x=0.2
            0.01383188,   // x=0.3
            0.02035048,   // x=0.4
            0.024748446,  // x=0.5
            0.028724425,  // x=0.6
            0.032779994,  // x=0.7
            0.036855249,  // x=0.8
            0.040909417,  // x=0.9
            0.044937609   // x=1.0
        };

    // Map to store production decision variables for agents
    private static final Map<Agent, Map<Period, GRBVar>> productionVarMap = new HashMap<>();

    // Global maps to track measured and calculated hydrogen production values (in kg)
    private static final Map<Period, Double> measuredProductionMap = new HashMap<>();
    private static final Map<Period, Map<Agent, List<Measurement>>> globalAgentMeasuredMap = new HashMap<>();
    private static final Map<Period, Double> calculatedHydrogenProductionPerPeriod = new HashMap<>();
    private static final Map<Period, Integer> measurementIterationMap = new HashMap<>();

    // Global map holding OPC-UA clients for each agent
    private static final Map<Agent, OpcUaClient> opcUaClients = new HashMap<>();

    /**
     * Helper class to store iteration results.
     */
    public static class IterationResult {
        public int iteration;  
        public Map<Period, Map<Agent, Double>> xValues = new HashMap<>();  // x values per agent and period
        public Map<Period, Map<Agent, String>> yStates = new HashMap<>();  // y states per agent and period
        public Map<Period, Double> aggregatedCalculated = new HashMap<>(); // Aggregated planned production
        public Map<Period, Double> aggregatedSimulated = new HashMap<>();  // Aggregated measured production
        public Map<Period, Map<Agent, Double>> agentCalculated = new HashMap<>(); // Planned production per agent
        public Map<Period, Map<Agent, Double>> agentSimulated = new HashMap<>();  // Measured production per agent
    }

    
    public static void main(String[] args) {
        try {
            // --- Load input data from Excel ---
            String excelFilePath = System.getenv("EXCEL_FILE_PATH");
            if (excelFilePath == null || excelFilePath.isEmpty()) {
                excelFilePath = "in/InputData_Plugfest.xlsx";
            }

            Workbook workbook = loadExcelWorkbook(excelFilePath);
            if (workbook == null) return;

            // --- Define periods and agents ---
            Parameters params = loadParameters(workbook);
            Set<Agent> agents = params.startupCost.keySet();
            Set<Period> periods = params.periods;
            workbook.close();

            // --- Establish OPC-UA connections for all agents ---
            for (Agent a : agents) {
                connectToOPCUA(a);
            }

            // --- Start OPC-UA polling (every second) ---
            ScheduledExecutorService opcPoller = startOPCUAPolling(agents);

            // --- Sort periods (ascending order) ---
            List<Period> sortedPeriods = new ArrayList<>(periods);
            sortedPeriods.sort(Comparator.comparingInt(Period::getT));

            // --- Initialize data structures ---
            Map<Agent, Map<Period, Double>> fixedXValues = new HashMap<>();
            Map<Agent, Map<Period, Map<State, Double>>> fixedYValues = new HashMap<>();
            List<IterationResult> iterationResults = new ArrayList<>();

            // --- Rolling optimization process ---
            for (int i = 0; i < sortedPeriods.size(); i++) {
                Period currentStart = sortedPeriods.get(i);
                System.out.println("Starting rolling optimization from period: " + currentStart.getT());

                // --- Create a new Gurobi model ---
                GRBEnv env = new GRBEnv(true);
                env.start();
                GRBModel model = new GRBModel(env);
                configureGurobi(model);

                // --- Define decision variables ---
                Map<Agent, Map<Period, GRBVar>> xVar = new HashMap<>();
                Map<Agent, Map<Period, Map<State, GRBVar>>> yVar = new HashMap<>();
                defineDecisionVariables(model, agents, periods, xVar, yVar);

                // --- Set objective function and constraints ---
                GRBLinExpr objective = new GRBLinExpr();
                defineObjectiveFunction(model, params, agents, periods, xVar, yVar, objective);
                model.setObjective(objective, GRB.MINIMIZE);
                defineConstraintsWithoutDemand(model, params, agents, periods, xVar, yVar);

                // --- Compute remaining demand ---
                double measuredProductionSoFar = calculateMeasuredProductionSoFar(sortedPeriods, currentStart);
                double remainingDemand = params.demand - measuredProductionSoFar;
                System.err.println("Remaining demand from period " + currentStart.getT() + ": " + remainingDemand);

                // --- Demand constraints ---
                // Keep this constraint (commented out for reference)
                /*
                GRBLinExpr demandConstraintExpr = new GRBLinExpr();
                for (Period t : sortedPeriods) {
                    if (t.getT() >= currentStart.getT()) {
                        for (Agent a : agents) {
                            double alpha = params.slope.get(a);
                            demandConstraintExpr.addTerm(alpha * params.intervalLengthSWO, xVar.get(a).get(t));
                        }
                    }
                }
                model.addConstr(demandConstraintExpr, GRB.GREATER_EQUAL, remainingDemand, "demandConstraint");
                */

                // Demand constraint using piecewise linear approximation (PLA)
                GRBLinExpr demandConstraintExpr = new GRBLinExpr();
                for (Period t : sortedPeriods) {
                    if (t.getT() >= currentStart.getT()) {
                        for (Agent a : agents) {
                            GRBVar prodVar = productionVarMap.get(a).get(t);
                            demandConstraintExpr.addTerm(params.intervalLengthSWO, prodVar);
                        }
                    }
                }
                model.addConstr(demandConstraintExpr, GRB.GREATER_EQUAL, remainingDemand, "demandConstraint");

                // --- Fix values for past periods ---
                fixPastPeriodVariables(sortedPeriods, currentStart, agents, fixedXValues, fixedYValues, xVar, yVar);

                // --- Optimize model ---
                model.optimize();
                IterationResult iterResult = processOptimizationResults(params, model, sortedPeriods, currentStart, agents, xVar, yVar, fixedXValues, fixedYValues);
                iterationResults.add(iterResult);

                // --- Write optimization results to OPC-UA ---
                Map<Agent, Double> currentXValues = extractCurrentXValues(fixedXValues, xVar, currentStart);
                Map<Agent, String> currentYValues = extractCurrentYValues(fixedYValues, yVar, currentStart);
                writeOptimizationResultsToOPCUA(agents, currentXValues, currentYValues);

                // --- Start measurement phase (OPC-UA) ---
                System.out.println("Measurement phase (OPC-UA) for " + SIMULATION_INTERVAL_SECONDS + " seconds...");
                performMeasurements(agents, currentStart);

                // --- Cleanup Gurobi model ---
                model.dispose();
                env.dispose();
            }

            // --- Stop OPC-UA polling ---
            opcPoller.shutdown();
            opcPoller.awaitTermination(5, TimeUnit.SECONDS);

            // --- Export results to Excel ---
            String desktopPath = getDesktopPath();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = formatter.format(new Date());
            String excelFilePathFinalResults = desktopPath + "/" + "PlugfestResults_" + timestamp + ".xlsx";
            String measurementsExcelPath = desktopPath + "/OPCUAMeasurements_" + timestamp + ".xlsx";

            exportIterationOverviewToExcelAlternate(iterationResults, sortedPeriods, agents, excelFilePathFinalResults);
            exportOPCUAMeasurementsToExcel(measurementsExcelPath, iterationResults);

            // --- Close OPC-UA clients ---
            closeOpcUaClients();

        } catch (GRBException | IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    
    /**
     * Reads the current value from the OPC UA server.
     * Converts flow rate from Nm³/h to kg/h using a predefined conversion factor.
     * 
     * @param agent The agent whose measurement is being read.
     * @param intervalSeconds The interval length in seconds.
     * @return The measured hydrogen production in kg/h.
     */
    public static double readElectrolyzerProductionRate(Agent agent, double intervalSeconds) {
        try {
            OpcUaClient client = opcUaClients.get(agent);
            if (client == null) {
                System.err.println("No OPC-UA client available for agent " + agent.a);
                return 0.0;
            }

            NodeId nodeId = NodeId.parse(OPC_UA_NODE_H2FlowRate);
            DataValue value = client.readValue(0.0, TimestampsToReturn.Both, nodeId).get();

            if (value != null && value.getValue() != null && value.getValue().getValue() instanceof Number) {
                double nm3PerLiter = ((Number) value.getValue().getValue()).doubleValue();
                return nm3PerLiter * 8.991521642124052e-5; // Convert to kg/h
            } else {
                System.err.println("OPC UA Read: No numeric value available for agent " + agent.a);
            }
        } catch (Exception e) {
            System.err.println("OPC UA Read failed for agent " + agent.a + ": " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Reads monitoring values from OPC-UA for all agents.
     * Used for debugging purposes.
     * 
     * @param agents The set of agents to read from.
     * @param endpoint The OPC-UA endpoint.
     * @param nodeIdString The OPC-UA node ID.
     * @return A map containing agent IDs and their corresponding measured values.
     */
    public static Map<Agent, Double> readMonitoringValues(Set<Agent> agents, String endpoint, String nodeIdString) {
        Map<Agent, Double> values = new HashMap<>();
        for (Agent agent : agents) {
            double val = readElectrolyzerProductionRate(agent, 1.0);
            values.put(agent, val);
        }
        return values;
    }

    /**
     * Writes the optimization results (x and y values) to the OPC-UA server.
     * 
     * @param agents The set of agents whose values need to be written.
     * @param xValues The calculated production values (x).
     * @param yValues The operational states (y).
     */
    public static void writeOptimizationResultsToOPCUA(Set<Agent> agents, Map<Agent, Double> xValues, Map<Agent, String> yValues) {
        NodeId nodeVOp = NodeId.parse(OPC_UA_NODE_H2Production_VOp);
        NodeId nodeApplyOp = NodeId.parse(OPC_UA_NODE_H2Production_ApplyOp);
        NodeId nodeYOp = NodeId.parse(OPC_UA_NODE_H2Production_yOp);

        for (Agent agent : agents) {
            try {
                OpcUaClient client = opcUaClients.get(agent);
                if (client == null) {
                    System.err.println("No OPC-UA client available for agent " + agent.a);
                    continue;
                }

                // Write x-value to OPC-UA server
                float valueToWrite = (float) (xValues.getOrDefault(agent, 0.0) * 100);
                DataValue dataValue = new DataValue(new Variant(valueToWrite));
                client.writeValue(nodeVOp, dataValue).get();
                System.out.println("OPC-UA: Writing x-value (" + valueToWrite + ") for agent " + agent.a);

                // Write apply operation flag
                DataValue applyValue = new DataValue(new Variant(true));
                client.writeValue(nodeApplyOp, applyValue).get();
                System.out.println("OPC-UA: Setting ApplyOp to true for agent " + agent.a);

                // Write y-value (state) to OPC-UA
                boolean yVal = "PRODUCTION".equalsIgnoreCase(yValues.getOrDefault(agent, "false"));
                DataValue yDataValue = new DataValue(new Variant(yVal));
                client.writeValue(nodeYOp, yDataValue).get();
                System.out.println("OPC-UA: Writing y-value (" + yVal + ") for agent " + agent.a);

            } catch (Exception e) {
                System.err.println("OPC-UA Write failed for agent " + agent.a + ": " + e.getMessage());
            }
        }
    }
    
    private static Workbook loadExcelWorkbook(String filePath) {
        try (FileInputStream excelFile = new FileInputStream(filePath)) {
            System.out.println(filePath + " found and loaded.");
            return new XSSFWorkbook(excelFile);
        } catch (FileNotFoundException e) {
            System.err.println(filePath + " not found.");
        } catch (IOException e) {
            System.err.println("Error loading workbook.");
        }
        return null;
    }

    private static double calculateMeasuredProductionSoFar(List<Period> sortedPeriods, Period currentStart) {
        double measuredProduction = 0.0;
        for (Period t : sortedPeriods) {
            if (t.getT() < currentStart.getT() && measuredProductionMap.containsKey(t)) {
                measuredProduction += measuredProductionMap.get(t);
            }
        }
        return measuredProduction;
    }

    private static String getDesktopPath() {
        String desktopPath = System.getenv("DESKTOP_PATH");
        return (desktopPath == null || desktopPath.isEmpty()) ? Paths.get(System.getProperty("user.home"), "Desktop").toString() : desktopPath;
    }

    private static void closeOpcUaClients() {
        for (OpcUaClient client : opcUaClients.values()) {
            try {
                client.disconnect().get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void fixPastPeriodVariables(
            List<Period> sortedPeriods,
            Period currentStart,
            Set<Agent> agents,
            Map<Agent, Map<Period, Double>> fixedXValues,
            Map<Agent, Map<Period, Map<State, Double>>> fixedYValues,
            Map<Agent, Map<Period, GRBVar>> xVar,
            Map<Agent, Map<Period, Map<State, GRBVar>>> yVar) throws GRBException {

        for (Period t : sortedPeriods) {
            if (t.getT() <= currentStart.getT()) {
                for (Agent a : agents) {
                    if (fixedXValues.containsKey(a) && fixedXValues.get(a).containsKey(t)) {
                        double fixedX = fixedXValues.get(a).get(t);
                        GRBVar varX = xVar.get(a).get(t);
                        varX.set(GRB.DoubleAttr.LB, fixedX);
                        varX.set(GRB.DoubleAttr.UB, fixedX);
                    }
                    for (State s : State.values()) {
                        if (fixedYValues.containsKey(a) && fixedYValues.get(a).containsKey(t)) {
                            double fixedY = fixedYValues.get(a).get(t).get(s);
                            GRBVar varY = yVar.get(a).get(t).get(s);
                            varY.set(GRB.DoubleAttr.LB, fixedY);
                            varY.set(GRB.DoubleAttr.UB, fixedY);
                        }
                    }
                }
            }
        }
    }

    private static void performMeasurements(Set<Agent> agents, Period currentStart) throws InterruptedException {
        System.out.println("Starting measurement phase for period " + currentStart.getT());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long startTime = System.currentTimeMillis();
        long endTime = startTime + SIMULATION_INTERVAL_SECONDS * 1000L;

        while (System.currentTimeMillis() <= endTime) {
            for (Agent a : agents) {
                double opcValue = readElectrolyzerProductionRate(a, SIMULATION_INTERVAL_SECONDS);
                double measuredKg = opcValue / 3600; // Convert from kg/h to kg/s

                // Update aggregated measured production
                double currentMeasuredValue = measuredProductionMap.getOrDefault(currentStart, 0.0);
                measuredProductionMap.put(currentStart, currentMeasuredValue + measuredKg);

                // Update per-agent measurements
                Map<Agent, List<Measurement>> agentMeasuredMap = globalAgentMeasuredMap.getOrDefault(currentStart, new HashMap<>());
                List<Measurement> measurements = agentMeasuredMap.getOrDefault(a, new ArrayList<>());
                String timestamp = sdf.format(new Date());
                measurements.add(new Measurement(timestamp, measuredKg));
                agentMeasuredMap.put(a, measurements);
                globalAgentMeasuredMap.put(currentStart, agentMeasuredMap);
            }
            Thread.sleep(1000L);
        }
    }
    
    private static Map<Agent, Double> extractCurrentXValues(
            Map<Agent, Map<Period, Double>> fixedXValues,
            Map<Agent, Map<Period, GRBVar>> xVar,
            Period currentStart) throws GRBException {

        Map<Agent, Double> currentXValues = new HashMap<>();

        for (Agent a : fixedXValues.keySet()) {
            double xVal = fixedXValues.containsKey(a) && fixedXValues.get(a).containsKey(currentStart)
                    ? fixedXValues.get(a).get(currentStart)
                    : xVar.get(a).get(currentStart).get(GRB.DoubleAttr.X);
            currentXValues.put(a, xVal);
        }
        return currentXValues;
    }

    /**
     * Processes the optimization results after the Gurobi solver has run.
     * - Stores the optimized values (x and y) for each agent and period.
     * - Computes the planned hydrogen production for each agent.
     * - Aggregates planned production for each period.
     * - Updates the measurement tracking to ensure correct comparison between planned and measured production.
     *
     * @param params        The optimization parameters.
     * @param model         The Gurobi model after optimization.
     * @param sortedPeriods List of periods in ascending order.
     * @param currentStart  The starting period for the current iteration.
     * @param agents        Set of agents involved in the optimization.
     * @param xVar          Map storing the optimization decision variable x (production level).
     * @param yVar          Map storing the optimization decision variable y (operational states).
     * @param fixedXValues  Map to store fixed x-values for previous periods.
     * @param fixedYValues  Map to store fixed y-values for previous periods.
     * @return              IterationResult containing the computed values for further analysis.
     * @throws GRBException If there is an error retrieving Gurobi optimization results.
     */
    private static IterationResult processOptimizationResults(
            Parameters params,
            GRBModel model,
            List<Period> sortedPeriods,
            Period currentStart,
            Set<Agent> agents,
            Map<Agent, Map<Period, GRBVar>> xVar,
            Map<Agent, Map<Period, Map<State, GRBVar>>> yVar,
            Map<Agent, Map<Period, Double>> fixedXValues,
            Map<Agent, Map<Period, Map<State, Double>>> fixedYValues) throws GRBException {

        // Initialize a new iteration result object
        IterationResult iterResult = new IterationResult();
        iterResult.iteration = currentStart.getT();

        // Check if the optimization found an optimal solution
        if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
            
            // 1️ Store optimized values for all periods >= currentStart
            for (Period t : sortedPeriods) {
                if (t.getT() >= currentStart.getT()) {
                    for (Agent a : agents) {
                        
                        // Retrieve the optimized production value (x)
                        double xVal = xVar.get(a).get(t).get(GRB.DoubleAttr.X);
                        fixedXValues.computeIfAbsent(a, k -> new HashMap<>()).put(t, xVal);

                        // Retrieve the optimized operational state values (y)
                        for (State s : State.values()) {
                            double yVal = yVar.get(a).get(t).get(s).get(GRB.DoubleAttr.X);
                            fixedYValues.computeIfAbsent(a, k -> new HashMap<>())
                                        .computeIfAbsent(t, k -> new HashMap<>())
                                        .put(s, yVal);
                        }
                    }
                }
            }

            // 2️ Process results for each period
            for (Period t : sortedPeriods) {
                Map<Agent, Double> xPerAgent = new HashMap<>();
                Map<Agent, String> yPerAgent = new HashMap<>();
                Map<Agent, Double> agentCalcMap = new HashMap<>();

                for (Agent a : agents) {
                    
                    // Retrieve the x-value (planned production level) for this agent
                    double xVal = fixedXValues.getOrDefault(a, new HashMap<>()).getOrDefault(t, 0.0);
                    xPerAgent.put(a, xVal);

                    // Identify the active state for this agent
                    String activeState = "None";
                    for (State s : State.values()) {
                        double yVal = fixedYValues.getOrDefault(a, new HashMap<>()).getOrDefault(t, new HashMap<>()).getOrDefault(s, 0.0);
                        if (yVal == 1.0) {
                            activeState = s.name();
                            break; // Exit loop once the active state is found
                        }
                    }
                    yPerAgent.put(a, activeState);

                    // Compute the planned hydrogen production for this agent
                    GRBVar prodVar = productionVarMap.get(a).get(t);
                    double agentCalc = prodVar.get(GRB.DoubleAttr.X) * params.intervalLengthSWO;
                    agentCalcMap.put(a, agentCalc);
                }

                // Store computed values in the iteration result object
                iterResult.xValues.put(t, xPerAgent);
                iterResult.yStates.put(t, yPerAgent);
                iterResult.agentCalculated.put(t, agentCalcMap);

                // 3️ Compute aggregated production across all agents
                double aggCalc = agentCalcMap.values().stream().mapToDouble(Double::doubleValue).sum();
                iterResult.aggregatedCalculated.put(t, aggCalc);
            }

            // 4️⃣ Register the current iteration as a measurement iteration
            if (!measurementIterationMap.containsKey(currentStart)) {
                measurementIterationMap.put(currentStart, currentStart.getT());
            }

            // 5️⃣ Store the total calculated hydrogen production for this period
            calculatedHydrogenProductionPerPeriod.put(currentStart, iterResult.aggregatedCalculated.getOrDefault(currentStart, 0.0));

            System.out.println("Iteration from period " + currentStart.getT() + " successful.");
        } else {
            // If no optimal solution was found, log a message
            System.out.println("No optimal solution found for period " + currentStart.getT());
        }

        return iterResult;
    }

    private static Map<Agent, String> extractCurrentYValues(
            Map<Agent, Map<Period, Map<State, Double>>> fixedYValues,
            Map<Agent, Map<Period, Map<State, GRBVar>>> yVar,
            Period currentStart) throws GRBException {

        Map<Agent, String> currentYValues = new HashMap<>();

        for (Agent a : fixedYValues.keySet()) {
            String state;
            if (fixedYValues.containsKey(a) && fixedYValues.get(a).containsKey(currentStart)) {
                state = getActiveState(fixedYValues.get(a).get(currentStart));
            } else {
                state = getActiveStateFromGrb(yVar.get(a).get(currentStart));
            }
            currentYValues.put(a, state);
        }
        return currentYValues;
    }


    private static String getActiveState(Map<State, Double> stateMap) {
        for (Map.Entry<State, Double> entry : stateMap.entrySet()) {
            if (entry.getValue() == 1.0) {
                return entry.getKey().name();
            }
        }
        return "N/A";
    }

    private static String getActiveStateFromGrb(Map<State, GRBVar> stateMap) throws GRBException {
        for (Map.Entry<State, GRBVar> entry : stateMap.entrySet()) {
            if (entry.getValue().get(GRB.DoubleAttr.X) == 1.0) {
                return entry.getKey().name();
            }
        }
        return "N/A";
    }

    private static ScheduledExecutorService startOPCUAPolling(final Set<Agent> agents) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            for (Agent a : agents) {
                double rate = readOPCUAMeasurement(a, 1.0);
                System.out.println("OPC-UA (" + OPC_UA_ADDRESS + ") - Agent " + a.a + " (" + OPC_UA_NODE_H2FlowRate + "): " + rate);
            }
        }, 0, 1, TimeUnit.SECONDS);
        return scheduler;
    }
    
    public static double readOPCUAMeasurement(Agent a, double intervalSeconds) {
        String endpointUrl = OPC_UA_ADDRESS;
        String H2FlowRate_V = OPC_UA_NODE_H2FlowRate; 

        try {
            // Erstelle einen OPC UA-Client und verbinde
            OpcUaClient client = OpcUaClient.create(endpointUrl);
            client.connect().get(); // blockierende Verbindung

            // Erstelle die NodeId im korrekten Format
            NodeId nodeId = NodeId.parse(H2FlowRate_V);

            // Lese den aktuellen Wert vom Knoten (blockierend)
            DataValue value = client.readValue(0.0, TimestampsToReturn.Both, nodeId).get();

            // Trenne die Verbindung
            client.disconnect().get();

            // Falls der gelesene Wert vom Typ Number ist, rechne um und liefere ihn zurück; sonst 0.0
            if (value.getValue() != null && value.getValue().getValue() instanceof Number) {
                double nm3PerLiter = ((Number) value.getValue().getValue()).doubleValue();
                // Umrechnung von Nm^3/h in kg/h für Wasserstoff:
                double kgPerHour = nm3PerLiter * 8.991521642124052e-5;
                return kgPerHour;
            }
        } catch (Exception e) {
            System.err.println("OPC UA Lesevorgang fehlgeschlagen: " + e.getMessage());
        }
        return 0.0;
    }

    
   
    
    
    /**
     * Exportiert eine Übersicht der Iterationsergebnisse in ein Excel-Dokument.
     * Format:
     *  - Erste Spalte: Iteration
     *  - Zweite Spalte: Periode
     *  - Für jeden Agenten: xValue, yValue, Calculated Production, Measured Production
     *  - Zum Schluss: Aggregierte Calculated und Aggregierte Measured (über alle Agenten)
     */
    private static void exportIterationOverviewToExcelAlternate(
            List<IterationResult> iterationResults,
            List<Period> sortedPeriods,
            Set<Agent> agents,
            String excelFilePath) throws IOException {

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Iteration Overview");

        // Erzeuge Header-Zeile
        Row headerRow = sheet.createRow(0);
        int col = 0;
        headerRow.createCell(col++).setCellValue("Iteration");
        headerRow.createCell(col++).setCellValue("Periode");

        // Sortiere Agenten für Konsistenz
        List<Agent> sortedAgents = new ArrayList<>(agents);
        sortedAgents.sort(Comparator.comparingInt(a -> a.a));
        for (Agent a : sortedAgents) {
            headerRow.createCell(col++).setCellValue("Agent " + a.a + " xValue");
            headerRow.createCell(col++).setCellValue("Agent " + a.a + " yValue");
            headerRow.createCell(col++).setCellValue("Agent " + a.a + " Calculated Prod");
            headerRow.createCell(col++).setCellValue("Agent " + a.a + " Measured Prod");
        }
        headerRow.createCell(col++).setCellValue("Agg Calculated");
        headerRow.createCell(col++).setCellValue("Agg Measured");

        // Datenzeilen: Jede Zeile entspricht einer Kombination aus Iteration und Periode.
        int rowIndex = 1;
        for (IterationResult ir : iterationResults) {
            for (Period p : sortedPeriods) {
                Row row = sheet.createRow(rowIndex++);
                int c = 0;
                row.createCell(c++).setCellValue(ir.iteration);
                row.createCell(c++).setCellValue(p.getT());
                for (Agent a : sortedAgents) {
                    double xVal = (ir.xValues.containsKey(p) && ir.xValues.get(p).containsKey(a))
                            ? ir.xValues.get(p).get(a) : 0.0;
                    String yVal = (ir.yStates.containsKey(p) && ir.yStates.get(p).containsKey(a))
                            ? ir.yStates.get(p).get(a) : "N/A";
                    double calcProd = (ir.agentCalculated.containsKey(p) && ir.agentCalculated.get(p).containsKey(a))
                            ? ir.agentCalculated.get(p).get(a) : 0.0;
                    // Hier: Falls für Periode p bereits in einer früheren oder gleichen Iteration gemessen wurde,
                    // dann die gemessenen Werte übernehmen, sonst 0.
                    double measuredProd = 0.0;
                    if (measurementIterationMap.containsKey(p) && measurementIterationMap.get(p) <= ir.iteration) {
                        Map<Agent, List<Measurement>> agentMeasurementsMap = globalAgentMeasuredMap.get(p);
                        if (agentMeasurementsMap != null) {
                            List<Measurement> measurements = agentMeasurementsMap.get(a);
                            if (measurements != null) {
                                for (Measurement m : measurements) {
                                    measuredProd += m.value;
                                }
                            }
                        }
                    }
                    row.createCell(c++).setCellValue(xVal);
                    row.createCell(c++).setCellValue(yVal);
                    row.createCell(c++).setCellValue(calcProd);
                    row.createCell(c++).setCellValue(measuredProd);
                }
                double aggCalc = ir.aggregatedCalculated.getOrDefault(p, 0.0);
                double aggMeasured = 0.0;
                if (measurementIterationMap.containsKey(p) && measurementIterationMap.get(p) <= ir.iteration) {
                    aggMeasured = measuredProductionMap.getOrDefault(p, 0.0);
                }
                row.createCell(c++).setCellValue(aggCalc);
                row.createCell(c++).setCellValue(aggMeasured);
            }
        }

        // Optional: AutoSize der Spalten
        for (int i = 0; i < col; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream fos = new FileOutputStream(excelFilePath)) {
            workbook.write(fos);
        } finally {
            workbook.close();
        }
        System.out.println("Iteration Overview exportiert: " + excelFilePath);
    }
    
    
    private static void exportOPCUAMeasurementsToExcel(String excelFilePath, List<IterationResult> iterationResults) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("OPC-UA Measurements");
        
        // Erzeuge Header-Zeile
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Period");
        headerRow.createCell(1).setCellValue("Agent");
        headerRow.createCell(2).setCellValue("Planned Production (kg)");
        headerRow.createCell(3).setCellValue("Measured Production (kg)");
        headerRow.createCell(4).setCellValue("Timestamp");
        headerRow.createCell(5).setCellValue("Deviation (Measured - Planned)");

        int rowIndex = 1;
        
        // Sortiere die Perioden anhand ihrer t-Werte
        List<Period> sortedPeriods = new ArrayList<>(globalAgentMeasuredMap.keySet());
        Collections.sort(sortedPeriods, Comparator.comparingInt(Period::getT));
        
        // Durchlaufe alle Perioden und schreibe pro Agent alle Messungen
        for (Period p : sortedPeriods) {
            Map<Agent, List<Measurement>> agentMeasurements = globalAgentMeasuredMap.get(p);
            if (agentMeasurements != null) {
                for (Map.Entry<Agent, List<Measurement>> entry : agentMeasurements.entrySet()) {
                    Agent a = entry.getKey();
                    List<Measurement> measurements = entry.getValue();
                    
                    // Finde die geplante Produktionsmenge aus der **letzten Iteration**
                    double plannedProduction = 0.0;
                    for (IterationResult result : iterationResults) {
                        if (result.agentCalculated.containsKey(p) && result.agentCalculated.get(p).containsKey(a)) {
                            plannedProduction = result.agentCalculated.get(p).get(a) / SIMULATION_INTERVAL_SECONDS;
                        }
                    }

                    if (measurements != null) {
                        for (Measurement m : measurements) {
                            double measuredProduction = m.value;
                            double deviation = measuredProduction - plannedProduction;
                            
                            Row row = sheet.createRow(rowIndex++);
                            row.createCell(0).setCellValue(p.getT());
                            row.createCell(1).setCellValue(a.a); // Agent-ID
                            row.createCell(2).setCellValue(plannedProduction);
                            row.createCell(3).setCellValue(measuredProduction);
                            row.createCell(4).setCellValue(m.timestamp);
                            row.createCell(5).setCellValue(deviation); // Abweichung zwischen Soll und Ist
                        }
                    }
                }
            }
        }
        
        // Optional: AutoSize der Spalten
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
        }
        
        try (FileOutputStream fos = new FileOutputStream(excelFilePath)) {
            workbook.write(fos);
        } finally {
            workbook.close();
        }
        
        System.out.println("OPC-UA Measurements exported to: " + excelFilePath);
    }
    
    /**
     * Baut für einen Agenten einen eigenen OPC-UA-Client auf und speichert ihn in der globalen Map.
     */
    private static void connectToOPCUA(Agent a) {
        try {
            if (!opcUaClients.containsKey(a)) {
                OpcUaClient client = OpcUaClient.create(OPC_UA_ADDRESS);
                client.connect().get(); // blockierend
                opcUaClients.put(a, client);
                System.out.println("OPC-UA: Client für Agent " + a.a + " verbunden zu " + OPC_UA_ADDRESS);
            } else {
                System.out.println("OPC-UA: Client für Agent " + a.a + " bereits verbunden.");
            }
        } catch (Exception e) {
            System.err.println("OPC-UA-Verbindungsfehler für Agent " + a.a + ": " + e.getMessage());
        }
    }

    /**
     * Schreibt die Optimierungsergebnisse (z.B. Produktionswerte) an den OPC-UA-Server.
     * Hierbei wird für jeden Agenten über den gegebenen Endpoint und NodeId der Wert geschrieben.
     */
    public static void writeOptimizationResults(Set<Agent> agents, String endpoint, String nodeIdString, Map<Agent, Double> results) {
        for (Agent a : agents) {
            try {
                OpcUaClient client = opcUaClients.get(a);
                if (client == null) {
                    System.err.println("Kein OPC-UA-Client für Agent " + a.a + " vorhanden.");
                    continue;
                }
                NodeId nodeId = NodeId.parse(nodeIdString);
                DataValue value = new DataValue(new Variant(results.getOrDefault(a, 0.0)));
                // Hier wird der Wert geschrieben. Der exakte Schreibvorgang kann je nach Setup variieren.
                client.writeValue(nodeId, value).get();
                System.out.println("OPC-UA: Schreibvorgang erfolgreich für Agent " + a.a + " an " + nodeIdString);
            } catch (Exception e) {
                System.err.println("OPC-UA Schreibvorgang fehlgeschlagen für Agent " + a.a + ": " + e.getMessage());
            }
        }
    }

    /**
     * Define decision variables.
     */
	private static void defineDecisionVariables(GRBModel model, Set<Agent> agents, Set<Period> periods,
			Map<Agent, Map<Period, GRBVar>> xVar, Map<Agent, Map<Period, Map<State, GRBVar>>> yVar)
			throws GRBException {
		// Für jeden Agenten initialisieren
		for (Agent a : agents) {
			xVar.put(a, new HashMap<>());
			yVar.put(a, new HashMap<>());
			productionVarMap.put(a, new HashMap<>());
			for (Period t : periods) {
				// x-Variable (Arbeitsanteil, 0 bis 1)
				GRBVar x = model.addVar(0, 1, 0, GRB.CONTINUOUS, "x_" + a.a + "_" + t.t);
				xVar.get(a).put(t, x);
				// y-Variablen (Status, binär)
				Map<State, GRBVar> yMap = new HashMap<>();
				for (State s : State.values()) {
					yMap.put(s, model.addVar(0, 1, 0, GRB.BINARY, "y_" + a.a + "_" + t.t + "_" + s));
				}
				yVar.get(a).put(t, yMap);
				// Produktionsvariable, die die Produktion (kg/h) darstellt – hier wird die stückweise lineare Beziehung modelliert.
				GRBVar prodVar = model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS, "prod_" + a.a + "_" + t.t);
				productionVarMap.get(a).put(t, prodVar);

				// Stückweise lineare Constraint: Verknüpfe x mit prodVar über die definierten Breakpoints und productionValues.
				model.addGenConstrPWL(x, prodVar, breakpoints, productionValues, "pwl_" + a.a + "_" + t.t);
			}
		}
	}

    /**
     * Define the objective function.
     */
    private static void defineObjectiveFunction(GRBModel model, Parameters params, Set<Agent> agents,
                                                  Set<Period> periods,
                                                  Map<Agent, Map<Period, GRBVar>> x,
                                                  Map<Agent, Map<Period, Map<State, GRBVar>>> y,
                                                  GRBLinExpr objective) throws GRBException {

        // Produktionskosten
        GRBLinExpr productionCostExpr = new GRBLinExpr();
        for (Agent a : agents) {
            for (Period t : periods) {
                Double Pel = params.powerElectrolyzer.get(a);
                Double CtE = params.electricityCost.get(t);
                if (Pel != null && CtE != null) {
                    productionCostExpr.addTerm(Pel * CtE * params.intervalLengthSWO, x.get(a).get(t));
                }
            }
        }
        objective.add(productionCostExpr);

        // Startup- und Standby-Kosten
        GRBLinExpr startupCostExpr = new GRBLinExpr();
        for (Agent a : agents) {
            double standbyCost = params.standbyCost.get(a);
            for (Period t : periods) {
                startupCostExpr.addTerm(params.intervalLengthSWO * params.startupCost.get(a),
                        y.get(a).get(t).get(State.STARTING));
                startupCostExpr.addTerm(params.intervalLengthSWO * standbyCost, y.get(a).get(t).get(State.STANDBY));
            }
        }
        objective.add(startupCostExpr);
    }

    /**
     * Define constraints without the demand constraint.
     */
    private static void defineConstraintsWithoutDemand(GRBModel model, Parameters params, Set<Agent> agents,
                                                         Set<Period> periods,
                                                         Map<Agent, Map<Period, GRBVar>> x,
                                                         Map<Agent, Map<Period, Map<State, GRBVar>>> y)
            throws GRBException {
        // 1. Initialzustand
        for (Agent a : agents) {
            Period firstPeriod = new Period(1);
            model.addConstr(y.get(a).get(firstPeriod).get(State.IDLE), GRB.EQUAL, 1.0,
                    "initialState_" + a.a + "_" + firstPeriod.t);
        }

        // 2. Betriebsgrenzen
        for (Agent a : agents) {
            for (Period t : periods) {
                GRBLinExpr minOperationExpr = new GRBLinExpr();
                minOperationExpr.addTerm(params.minOperation.get(a), y.get(a).get(t).get(State.PRODUCTION));
                model.addConstr(x.get(a).get(t), GRB.GREATER_EQUAL, minOperationExpr,
                        "minOperation_" + a.a + "_" + t.t);

                GRBLinExpr maxOperationExpr = new GRBLinExpr();
                maxOperationExpr.addTerm(params.maxOperation.get(a), y.get(a).get(t).get(State.PRODUCTION));
                model.addConstr(x.get(a).get(t), GRB.LESS_EQUAL, maxOperationExpr, "maxOperation_" + a.a + "_" + t.t);
            }
        }

        // 3. Zustandsexklusivität
        for (Agent a : agents) {
            for (Period t : periods) {
                GRBLinExpr statusSum = new GRBLinExpr();
                for (State s : State.values()) {
                    statusSum.addTerm(1.0, y.get(a).get(t).get(s));
                }
                model.addConstr(statusSum, GRB.EQUAL, 1.0, "stateExclusivity_" + a.a + "_" + t.t);
            }
        }

     // 4️ State Transitions & Persistence Constraints
        for (Agent a : agents) {
            for (Period t : periods) {
                if (t.t > 1) {
                    Period prevPeriod = new Period(t.t - 1);
                    int startingHoldingDuration = params.holdingDurations.get(a).get(State.STARTING);
                    Period startupPeriod = new Period(t.getT() - startingHoldingDuration);

                    // 1️⃣ Transition: IDLE → STARTING
                    GRBLinExpr startingExpr = new GRBLinExpr();
                    startingExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.IDLE));    // Can start if previously IDLE
                    startingExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.STARTING)); // Can remain in STARTING
                    model.addConstr(y.get(a).get(t).get(State.STARTING), GRB.LESS_EQUAL, startingExpr,
                            "stateTransitionStarting_" + a.a + "_" + t.t);

                    // 2️⃣ Transition: STARTING → PRODUCTION (Not from STANDBY)
                    GRBLinExpr productionExpr = new GRBLinExpr();
                    productionExpr.addTerm(1.0, y.get(a).get(startupPeriod).get(State.STARTING));  // Only if previously in STARTING
                    productionExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.PRODUCTION));   // Can remain in PRODUCTION
                    model.addConstr(y.get(a).get(t).get(State.PRODUCTION), GRB.LESS_EQUAL, productionExpr,
                            "stateTransitionProduction_" + a.a + "_" + t.t);

                    // 3️⃣ Transition: PRODUCTION → STANDBY (Or remain in STANDBY)
                    GRBLinExpr standbyExpr = new GRBLinExpr();
                    standbyExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.PRODUCTION));  // Can enter STANDBY from PRODUCTION
                    standbyExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.STANDBY));     // Can remain in STANDBY
                    model.addConstr(y.get(a).get(t).get(State.STANDBY), GRB.LESS_EQUAL, standbyExpr,
                            "stateTransitionStandby_" + a.a + "_" + t.t);

                    // 4️⃣ Transition: STANDBY → IDLE (Or remain in IDLE)
                    GRBLinExpr idleExpr = new GRBLinExpr();
                    idleExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.STANDBY));  // Can transition from STANDBY
                    idleExpr.addTerm(1.0, y.get(a).get(prevPeriod).get(State.IDLE));     // Can remain in IDLE
                    model.addConstr(y.get(a).get(t).get(State.IDLE), GRB.LESS_EQUAL, idleExpr,
                            "stateTransitionIdle_" + a.a + "_" + t.t);

                    // 5️⃣ Ensure that the agent **only stays in one state** at a time
                    GRBLinExpr statePersistence = new GRBLinExpr();
                    for (State s : State.values()) {
                        statePersistence.addTerm(1.0, y.get(a).get(t).get(s));  // Sum of all states in one period
                        statePersistence.addTerm(-1.0, y.get(a).get(prevPeriod).get(s)); // If previous state exists
                    }
                    model.addConstr(statePersistence, GRB.EQUAL, 0, "statePersistence_" + a.a + "_" + t.t);
                }
            }
        }


        // 5. Mindesthaltedauer
        for (Agent a : agents) {
            for (State s : State.values()) {
                Integer minDwellTime = params.holdingDurations.get(a).get(s);
                if (minDwellTime != null) {
                    for (Period t : periods) {
                        if (t.t >= minDwellTime && t.t >= 2) {
                            for (int tau = 1; tau <= minDwellTime - 1; tau++) {
                                if (t.t - tau >= 1) {
                                    Period prevPeriod = new Period(t.t - 1);
                                    Period futurePeriod = new Period(t.t - tau);
                                    GRBLinExpr holdingExpr = new GRBLinExpr();
                                    holdingExpr.addTerm(1.0, y.get(a).get(t).get(s));
                                    holdingExpr.addTerm(-1.0, y.get(a).get(prevPeriod).get(s));
                                    model.addConstr(holdingExpr, GRB.LESS_EQUAL, y.get(a).get(futurePeriod).get(s),
                                            "minHoldingDuration_" + a.a + "_" + t.t + "_" + s);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

	/**
	 * Configure the Gurobi solver.
	 */
	private static void configureGurobi(GRBModel model) throws GRBException {
		model.set(GRB.DoubleParam.MIPGap, 0.0001);
		model.set(GRB.DoubleParam.TimeLimit, 600); 
	}

	/**
	 * Load parameters from the Excel file.
	 */
	private static Parameters loadParameters(Workbook workbook) {
	    Sheet agentsSheet = workbook.getSheet("Electrolyzer");
	    Sheet periodsSheet = workbook.getSheet("Periods");
	    Sheet parametersSheet = workbook.getSheet("GlobalParameters");

	    // Laden der Agenten-Parameter
	    Map<Agent, Double> powerElectrolyzer = new HashMap<>();
	    Map<Agent, Double> minOperation = new HashMap<>();
	    Map<Agent, Double> maxOperation = new HashMap<>();
	    Map<Agent, Double> slope = new HashMap<>();
	    Map<Agent, Double> intercept = new HashMap<>();
	    Map<Agent, Integer> startupDuration = new HashMap<>();
	    Map<Agent, Double> startupCost = new HashMap<>();
	    Map<Agent, Double> standbyCost = new HashMap<>();
	    Set<Agent> agents = new HashSet<>();

	    // Mindestverweildauer für Zustände (Idle, Starting, Production, Standby)
	    Map<Agent, Map<State, Integer>> holdingDurations = new HashMap<>();

	    // Lesen der Agenten-Daten aus der Agenten-Tabelle
	    for (Row row : agentsSheet) {
	        if (row.getRowNum() == 0) continue;

	        Agent agent = new Agent((int) row.getCell(0).getNumericCellValue()); // Column A
	        powerElectrolyzer.put(agent, row.getCell(1).getNumericCellValue()); // Column B
	        minOperation.put(agent, row.getCell(2).getNumericCellValue()); // Column C
	        maxOperation.put(agent, row.getCell(3).getNumericCellValue()); // Column D
	        startupCost.put(agent, row.getCell(4).getNumericCellValue()); // Column E
	        standbyCost.put(agent, row.getCell(5).getNumericCellValue()); // Column F
	        slope.put(agent, row.getCell(6).getNumericCellValue()); // Column G
	        intercept.put(agent, row.getCell(7).getNumericCellValue()); // Column H

	        // Laden der Haltedauern (Idle, Starting, Production, Standby)
	        Map<State, Integer> agentHoldingDurations = new HashMap<>();
	        agentHoldingDurations.put(State.IDLE, (int) row.getCell(8).getNumericCellValue()); // Column I
	        agentHoldingDurations.put(State.STARTING, (int) row.getCell(9).getNumericCellValue()); // Column J
	        agentHoldingDurations.put(State.PRODUCTION, (int) row.getCell(10).getNumericCellValue()); // Column K
	        agentHoldingDurations.put(State.STANDBY, (int) row.getCell(11).getNumericCellValue()); // Column L

	        // Haltedauern in die Map einfügen
	        holdingDurations.put(agent, agentHoldingDurations);
	        agents.add(agent);
	    }

	    // Laden der periodenspezifischen Parameter, einschließlich Demand und erneuerbare Energien
	    Map<Period, Double> electricityPrice = new HashMap<>();
	    Map<Period, Double> availablePower = new HashMap<>();
	    Map<Period, Double> renewableEnergy = new HashMap<>(); // Neu: Erneuerbare Energien
	    Set<Period> periods = new HashSet<>();

	    // Lesen der Perioden-Daten aus der Perioden-Tabelle
	    for (Row row : periodsSheet) {
	        if (row.getRowNum() == 0) continue; // Kopfzeile überspringen

	        Period period = new Period((int) row.getCell(0).getNumericCellValue()); // Column A
	        electricityPrice.put(period, row.getCell(1).getNumericCellValue()); // Column B
	        availablePower.put(period, row.getCell(2).getNumericCellValue()); // Column C
	        renewableEnergy.put(period, row.getCell(2).getNumericCellValue()); // Column E (erneuerbare Energie)
	        periods.add(period);
	    }

	    // Global Parameter
	    double intervalLength = parametersSheet.getRow(1).getCell(1).getNumericCellValue();
	    double demandDeviationCost = parametersSheet.getRow(2).getCell(1).getNumericCellValue();
	    double demand = parametersSheet.getRow(3).getCell(1).getNumericCellValue();

	    // Rückgabe des Parameters-Objekts mit erneuerbarer Energie
	    return new Parameters(
	            startupCost,
	            standbyCost,
	            powerElectrolyzer,
	            electricityPrice,
	            minOperation,
	            maxOperation,
	            demand,
	            slope,
	            intercept,
	            availablePower,
	            intervalLength,
	            startupDuration,
	            demandDeviationCost,
	            holdingDurations,
	            periods,
	            renewableEnergy 
	    );
	}

}