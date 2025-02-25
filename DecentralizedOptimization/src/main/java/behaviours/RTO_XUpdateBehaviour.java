package behaviours;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.gurobi.gurobi.*;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import models.ADMMDataModel;
import models.Electrolyzer;
import models.Parameters;
import models.Period;
import models.State;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class RTO_XUpdateBehaviour extends OneShotBehaviour {

    private static final long serialVersionUID = 1L;
    private GRBModel model;
    private Parameters params;
    private Set<Electrolyzer> electrolyzers;
    private Predicate<Electrolyzer> filterCriteria;
    private Period currentSWOPeriod;
    private int finalSWOIteration;
    private ADMMDataModel dataModel;
    private double rho;
    private int currentRTOIteration;

    private Map<Electrolyzer, Map<Integer, GRBVar>> xVarsRTO;
    private Map<Electrolyzer, Map<Integer, GRBVar>> residual1VarsRTO;
    private Map<Electrolyzer, Map<Integer, GRBVar>> residual2VarsRTO;
    private Map<Electrolyzer, Map<Integer, GRBVar>> residual3VarsRTO;
    
    // Anzahl der Zeitschritte pro SWO-Periode (z. B. 10)
    private int rtoStepsPerSWOPeriod = 10;
    
    // ------------------ ROLLING OPTIMIZATION ------------------
    // Dieses Feld gibt an, ab welcher Periode in der aktuellen Optimierung noch optimiert wird.
    // Zunächst werden alle Perioden optimiert (currentStartPeriod = 1). In jedem weiteren Durchlauf
    // wird dieser Wert erhöht, sodass bereits optimierte Perioden fixiert bleiben.
    private int currentStartPeriod;
    // -----------------------------------------------------------

    public RTO_XUpdateBehaviour(GRBModel model, Parameters params, Set<Electrolyzer> electrolyzers, 
            Period currentSWOPeriod, int finalSWOIteration, ADMMDataModel dataModel, double rho, 
            Predicate<Electrolyzer> filterCriteria, int RTOIteration, int currentStartPeriod) {
        this.model = model;
        this.params = params;
        this.electrolyzers = electrolyzers;
        this.currentSWOPeriod = currentSWOPeriod;
        this.finalSWOIteration = finalSWOIteration;
        this.dataModel = dataModel;
        this.rho = rho;
        this.currentRTOIteration = RTOIteration;
        this.filterCriteria = filterCriteria;
        this.currentStartPeriod = currentStartPeriod;
        initializeVariables();
    }

    private void initializeVariables() {
        Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);
        try {
            xVarsRTO = new HashMap<>();
            residual1VarsRTO = new HashMap<>();
            residual2VarsRTO = new HashMap<>();
            residual3VarsRTO = new HashMap<>();

            // Initialisiere Variablen für jeden Elektrolyseur und jede Periode
            for (Electrolyzer e : filteredElectrolyzers) {
                xVarsRTO.put(e, new HashMap<>());
                residual1VarsRTO.put(e, new HashMap<>());
                residual2VarsRTO.put(e, new HashMap<>());
                residual3VarsRTO.put(e, new HashMap<>());

                for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
                    xVarsRTO.get(e).put(t, model.addVar(0, 1, 0, GRB.CONTINUOUS, "x_" + e.getId() + "_" + t));
                    residual1VarsRTO.get(e).put(t, model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "residual_Constraint1_" + e.getId() + "_" + t));
                    residual2VarsRTO.get(e).put(t, model.addVar(0, Double.MAX_VALUE, 0, GRB.CONTINUOUS, "residual_Constraint2_" + e.getId() + "_" + t));
                }
            }

        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void action() {
        int nextIteration = currentRTOIteration + 1;
        try {
            model.update();
            optimizeX();
            saveResults(nextIteration);
            sendBundledXUpdateResults();
            
            if (currentStartPeriod < rtoStepsPerSWOPeriod) {
                currentStartPeriod++;
            }
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }
    
    private void optimizeX() throws GRBException {
        System.out.println("x-Update von " + myAgent.getLocalName() 
            + " in SWO-Iteration: " + finalSWOIteration 
            + ", SWO-Periode: " + currentSWOPeriod.getT() 
            + ", ab Startperiode: " + currentStartPeriod);
        
        Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);
        
        // ------------------------- FIXIERUNG -------------------------
        // Für alle Perioden, die bereits optimiert wurden (t < currentStartPeriod),
        // werden die x-Variablen fixiert, d.h. LB und UB werden auf den zuvor gespeicherten Wert gesetzt.
        for (Electrolyzer e : filteredElectrolyzers) {
            int agentID = e.getId() - 1;
            for (int t = 1; t < currentStartPeriod; t++) {
                // Hier rufen wir den bereits gespeicherten x-Wert (iterationsunabhängig) ab
                double fixedValue = dataModel.getXRTOResultForAgentPeriod(agentID, t - 1);
                GRBVar xVar = xVarsRTO.get(e).get(t);
                xVar.set(GRB.DoubleAttr.LB, fixedValue);
                xVar.set(GRB.DoubleAttr.UB, fixedValue);
            }
        }
        // -------------------------------------------------------------
        
        // Aufbau der Zielfunktion: Nur für Perioden ab currentStartPeriod optimieren.
        GRBLinExpr objective = new GRBLinExpr();
        for (Electrolyzer e : filteredElectrolyzers) {
            for (int t = currentStartPeriod; t <= rtoStepsPerSWOPeriod; t++) {
                double powerElectrolyzer = params.powerElectrolyzer.get(e);
                double intervalLengthRTO = (params.intervalLengthSWO / rtoStepsPerSWOPeriod);
                double efficiency = params.slope.get(e);
                objective.addTerm(-powerElectrolyzer * efficiency * intervalLengthRTO, xVarsRTO.get(e).get(t));
            }
        }
        
        // Aufbau des Penalty-Terms für die Constraints: Nur für t ab currentStartPeriod
        GRBQuadExpr quadraticPenalty = new GRBQuadExpr();
        for (Electrolyzer e : filteredElectrolyzers) {
            for (int t = currentStartPeriod; t <= rtoStepsPerSWOPeriod; t++) {
                int periodIndex = t - 1;
                boolean[][] ySWOValues = dataModel.getYSWOValuesForAgent(finalSWOIteration, e.getId() - 1);
                double[][] sRTOValues = dataModel.getSRTOValuesForAgent(currentRTOIteration, e.getId() - 1);
                double[][] uRTOValues = dataModel.getURTOValuesForAgent(currentRTOIteration, e.getId() - 1);
                double opMin = params.minOperation.get(e);
                double opMax = params.maxOperation.get(e);
                
                GRBVar xVar = xVarsRTO.get(e).get(t);
                double productionYValue = ySWOValues[currentSWOPeriod.getT()-1][State.PRODUCTION.ordinal()] ? 1.0 : 0.0;
                
                GRBLinExpr lowerBoundaryConstraint = new GRBLinExpr();
                lowerBoundaryConstraint.addConstant(opMin * productionYValue);
                lowerBoundaryConstraint.addTerm(-1, xVar);
                lowerBoundaryConstraint.addConstant(sRTOValues[periodIndex][0]);
                model.addConstr(residual1VarsRTO.get(e).get(t), GRB.GREATER_EQUAL, lowerBoundaryConstraint, 
                                "lowerBoundaryResidual_" + e.getId() + "_" + t);
    
                GRBLinExpr upperBoundaryConstraint = new GRBLinExpr();
                upperBoundaryConstraint.addTerm(1, xVar);
                upperBoundaryConstraint.addConstant(sRTOValues[periodIndex][1] - opMax * productionYValue);
                model.addConstr(residual2VarsRTO.get(e).get(t), GRB.GREATER_EQUAL, upperBoundaryConstraint, 
                                "upperBoundaryResidual_" + e.getId() + "_" + t);
    
                quadraticPenalty.addTerm(rho, residual1VarsRTO.get(e).get(t), residual1VarsRTO.get(e).get(t));
                quadraticPenalty.addTerm(rho, residual2VarsRTO.get(e).get(t), residual2VarsRTO.get(e).get(t));
            }
        }
        
        // Berechnung des dualen Energieterms: Hier summieren wir über die noch optimierten Perioden.
        double[][] renewableEnergyMatrix = dataModel.getFluctuatingRenewableEnergyMatrix();
        double totalRenewableEnergy = 0;
        for (int t = currentStartPeriod; t <= rtoStepsPerSWOPeriod; t++) {
            totalRenewableEnergy += renewableEnergyMatrix[t - 1][currentStartPeriod-1]; 
        }
        double purchasedGridEnergy = params.getPurchasedEnergy(currentSWOPeriod);
        GRBLinExpr dualEnergyTerm = new GRBLinExpr();
        double lambdaEnergy = dataModel.getEnergyBalanceDualVariable(currentRTOIteration);
        
        for (Electrolyzer e : filteredElectrolyzers) {
            for (int t = currentStartPeriod; t <= rtoStepsPerSWOPeriod; t++) {
                double powerElectrolyzer = params.powerElectrolyzer.get(e);
                double intervalLengthRTO = params.intervalLengthSWO / rtoStepsPerSWOPeriod;
                dualEnergyTerm.addTerm(powerElectrolyzer * intervalLengthRTO * lambdaEnergy, xVarsRTO.get(e).get(t));
            }
        }
        dualEnergyTerm.addConstant(-lambdaEnergy * (totalRenewableEnergy + purchasedGridEnergy));
        
        // Gesamte Zielfunktion (Objective + Penalty + dualer Energieterm)
        GRBQuadExpr objectiveWithPenalty = new GRBQuadExpr();
        objectiveWithPenalty.add(dualEnergyTerm);
        objectiveWithPenalty.add(objective);
        objectiveWithPenalty.add(quadraticPenalty);
    
        model.setObjective(objectiveWithPenalty, GRB.MINIMIZE);
        model.getEnv().set(GRB.DoubleParam.OptimalityTol, 1e-6);
        model.getEnv().set(GRB.IntParam.OutputFlag, 0);
        
        model.optimize();
        
        try {
            int status = model.get(GRB.IntAttr.Status);
            if (status == GRB.OPTIMAL) {
                // Bei optimaler Lösung werden die Ergebnisse gespeichert.
                saveResults(currentRTOIteration + 1);
                // Optional: Ergebnisse in eine Excel-Datei schreiben
                String optimizationResultsOutput = "RTO-Results_" + myAgent.getLocalName() + "_Run_"
                        + currentRTOIteration + ".xlsx";
                // writeResultsToExcel(model, params, dataModel, rtoStepsPerSWOPeriod, finalSWOIteration,
                //         currentSWOPeriod.getT(), optimizationResultsOutput);
            } else if (status == GRB.INFEASIBLE) {
                System.out.println("The model is infeasible.");
                model.write("infeasible_model.ilp");
            } else if (status == GRB.UNBOUNDED) {
                System.out.println("The model is unbounded.");
            } else {
                System.out.println("Optimization ended with status: " + status);
            }
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }
    
    private void saveResults(int nextIteration) throws GRBException {
        Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);
        // Speichern der x-Werte für alle Perioden (sowohl fixierte als auch optimierte)
        for (Electrolyzer e : filteredElectrolyzers) {
            int agentID = e.getId() - 1;
            for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
                double xValue = xVarsRTO.get(e).get(t).get(GRB.DoubleAttr.X);
                if (xValue <= 0.01) {
                    xValue = 0;
                }
                dataModel.saveXRTOValueForPeriod(nextIteration, agentID, t - 1, xValue);
            }
        }
    }
    
    private void sendBundledXUpdateResults() {
        Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        StringBuilder content = new StringBuilder();
        content.append("RTOxUpdateMessage;").append(currentRTOIteration).append(";");
    
        for (Electrolyzer e : filteredElectrolyzers) {
            for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
                double xValue = dataModel.getXRTOValueForAgentPeriod(currentRTOIteration + 1, e.getId() - 1, t - 1);
                double hydrogenProduction = 0;
                content.append(e.getId()).append(",").append(t).append(",")
                       .append(xValue).append(",").append(hydrogenProduction).append(";");
            }
        }
    
        msg.setContent(content.toString());
        for (AID recipient : dataModel.getPhoneBook()) {
            if (!recipient.equals(myAgent.getAID())) {
                msg.addReceiver(recipient);
            }
        }
       
        myAgent.send(msg);
        System.out.println(myAgent.getLocalName() + " send Message in Iteration " + currentRTOIteration);
        
    }
    
    public void writeResultsToExcel(GRBModel model, Parameters params, ADMMDataModel dataModel, 
            int rtoStepsPerSWOPeriod, int finalSWOIteration, int currentSWOPeriod, String fileName) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Optimization Results" + "Agent_" + myAgent.getLocalName());
    
        // Kopfzeile erstellen
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Periode");
        headerRow.createCell(1).setCellValue("Elektrolyzer-ID");
        headerRow.createCell(2).setCellValue("x-Wert (Leistung)");
        headerRow.createCell(3).setCellValue("Erneuerbare Energie (geschwankt)");
        headerRow.createCell(4).setCellValue("Production-State");
    
        int rowIndex = 1;
    
        try {
            for (int t = 1; t <= rtoStepsPerSWOPeriod; t++) {
                Set<Electrolyzer> filteredElectrolyzers = filterElectrolyzers(electrolyzers, filterCriteria);
                double renewableEnergyForPeriod = params.getRenewableEnergy(new Period(currentSWOPeriod)) / rtoStepsPerSWOPeriod;
                double fluctuatedRenewableEnergy = renewableEnergyForPeriod * (1.0 + (Math.random() * 0.4 - 0.2));
    
                for (Electrolyzer e : filteredElectrolyzers) {
                    Row row = sheet.createRow(rowIndex++);
                    double xValue = dataModel.getXRTOValueForAgentPeriod(currentRTOIteration + 1, e.getId() - 1, t - 1);
    
                    boolean[][] ySWOValues = dataModel.getYSWOValuesForAgent(finalSWOIteration, e.getId() - 1);
                    boolean[] productionState = ySWOValues[currentSWOPeriod - 1];
    
                    String activeProductionState = "None";
                    for (State state : State.values()) {
                        if (productionState[state.ordinal()]) {
                            activeProductionState = state.name();
                            break;
                        }
                    }
    
                    row.createCell(0).setCellValue(t);
                    row.createCell(1).setCellValue(e.getId());
                    row.createCell(2).setCellValue(xValue);
                    row.createCell(3).setCellValue(fluctuatedRenewableEnergy);
                    row.createCell(4).setCellValue(activeProductionState);
                }
            }
    
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                sheet.autoSizeColumn(i);
            }
    
            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                workbook.write(fos);
            }
    
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
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