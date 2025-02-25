package behaviours;

import jade.core.behaviours.OneShotBehaviour;
import models.*;
import org.apache.poi.ss.usermodel.*;

import java.util.*;

public class LoadParametersBehaviour extends OneShotBehaviour {

    private static final long serialVersionUID = 1L;
    private Workbook workbook;
    private ADMMDataModel dataModel; // Verweis auf das ADMMDataModel des Agenten
    private Parameters params;       // Geladene Parameter werden hier gespeichert

    // Konstruktor: Übergabe des Workbooks und des ADMMDataModel
    public LoadParametersBehaviour(Workbook workbook, ADMMDataModel dataModel) {
        this.workbook = workbook;
        this.dataModel = dataModel;
    }

    @Override
    public void action() {
        // Lade die Parameter aus dem Excel-Workbook
        params = loadParameters(workbook);
        
        // Füge die geladenen Parameter ins ADMMDataModel ein
        dataModel.setParameters(params); 
        
        // Optionale Ausgabe zur Überprüfung
        System.out.println("Parameter erfolgreich geladen und in das ADMMDataModel eingefügt.");
    }

    /**
     * Methode zum Laden der Parameter aus der Excel-Datei
     */
    private static Parameters loadParameters(Workbook workbook) {
        Sheet agentsSheet = workbook.getSheet("Agent");
        Sheet periodsSheet = workbook.getSheet("Periods");
        Sheet parametersSheet = workbook.getSheet("GlobalParameters");

        // Laden der Agenten-Parameter
        Map<Electrolyzer, Double> powerElectrolyzer = new HashMap<>();
        Map<Electrolyzer, Double> minOperation = new HashMap<>();
        Map<Electrolyzer, Double> maxOperation = new HashMap<>();
        Map<Electrolyzer, Double> slope = new HashMap<>();
        Map<Electrolyzer, Double> intercept = new HashMap<>();
        Map<Electrolyzer, Integer> startupDuration = new HashMap<>();
        Map<Electrolyzer, Double> startupCost = new HashMap<>();
        Map<Electrolyzer, Double> standbyCost = new HashMap<>();
        Map<Electrolyzer, Double> rampRate = new HashMap<>();
        Set<Electrolyzer> electrolyzers = new HashSet<>();

        // Mindestverweildauer für Zustände (Idle, Starting, Production, Standby)
        Map<Electrolyzer, Map<State, Integer>> holdingDurations = new HashMap<>();

        // Lesen der Elektrolyseur-Daten aus der Agenten-Tabelle
        for (Row row : agentsSheet) {
            if (row.getRowNum() == 0)
                continue;

            // Erstellen eines neuen Electrolyzer-Objekts mit allen erforderlichen Parametern
            Electrolyzer electrolyzer = new Electrolyzer(
                (int) row.getCell(0).getNumericCellValue(),    // Column A: ID
                row.getCell(1).getNumericCellValue(),          // Column B: powerElectrolyzer
                row.getCell(2).getNumericCellValue(),          // Column C: minOperation
                row.getCell(3).getNumericCellValue(),          // Column D: maxOperation
                row.getCell(6).getNumericCellValue(),          // Column G: slope
                row.getCell(7).getNumericCellValue(),          // Column H: intercept
                (int) row.getCell(1).getNumericCellValue(),    // Column I: startupDuration
                row.getCell(4).getNumericCellValue(),          // Column E: startupCost
                row.getCell(5).getNumericCellValue()           // Column F: standbyCost
            );

            // Speichern der Parameter in den jeweiligen Maps
            powerElectrolyzer.put(electrolyzer, electrolyzer.getPowerElectrolyzer());
            minOperation.put(electrolyzer, electrolyzer.getMinOperation());
            maxOperation.put(electrolyzer, electrolyzer.getMaxOperation());
            slope.put(electrolyzer, electrolyzer.getSlope());
            intercept.put(electrolyzer, electrolyzer.getIntercept());
            startupDuration.put(electrolyzer, electrolyzer.getStartupDuration());
            startupCost.put(electrolyzer, electrolyzer.getStartupCost());
            standbyCost.put(electrolyzer, electrolyzer.getStandbyCost());

            // Laden der Haltedauern (Idle, Starting, Production, Standby)
            Map<State, Integer> electrolyzerHoldingDurations = new HashMap<>();
            electrolyzerHoldingDurations.put(State.IDLE, (int) row.getCell(8).getNumericCellValue());       // Column J
            electrolyzerHoldingDurations.put(State.STARTING, (int) row.getCell(9).getNumericCellValue());  // Column K
            electrolyzerHoldingDurations.put(State.PRODUCTION, (int) row.getCell(10).getNumericCellValue()); // Column L
            electrolyzerHoldingDurations.put(State.STANDBY, (int) row.getCell(11).getNumericCellValue());   // Column M
            rampRate.put(electrolyzer, row.getCell(12).getNumericCellValue());           // Column M

            // Haltedauern in die Map einfügen
            holdingDurations.put(electrolyzer, electrolyzerHoldingDurations);
            electrolyzers.add(electrolyzer);
        }

        // Laden der periodenspezifischen Parameter, einschließlich Demand
        Map<Period, Double> electricityPrice = new HashMap<>();
        Map<Period, Double> availablePower = new HashMap<>();
        Map<Period, Double> periodDemand = new HashMap<>();
        Map<Period, Double> purchasedGridEnergy = new HashMap<>();
        Map<Period, Double> totalElectroylzerPower = new HashMap<>();
        Set<Period> periods = new HashSet<>();

        // Lesen der Perioden-Daten aus der Perioden-Tabelle
        for (Row row : periodsSheet) {
            if (row.getRowNum() == 0)
                continue;
            Period period = new Period((int) row.getCell(0).getNumericCellValue()); // Column A
            electricityPrice.put(period, row.getCell(1).getNumericCellValue());      // Column B
            availablePower.put(period, row.getCell(2).getNumericCellValue());        // Column C
            periodDemand.put(period, row.getCell(3).getNumericCellValue());          // Column D
            periods.add(period);                                        
        }

        // Global Parameter
        double intervalLength = parametersSheet.getRow(1).getCell(1).getNumericCellValue();
        double demandDeviationCost = parametersSheet.getRow(2).getCell(1).getNumericCellValue();

        // Rückgabe der Parameter einschließlich der Gesamtanzahl der Elektrolyseure
        return new Parameters(
                startupCost, 
                standbyCost, 
                powerElectrolyzer, 
                electricityPrice, 
                minOperation, 
                maxOperation, 
                periodDemand, 
                slope, 
                intercept, 
                availablePower, 
                intervalLength, 
                startupDuration, 
                demandDeviationCost, 
                holdingDurations, 
                rampRate,  // Füge rampRate als Parameter hinzu
                electrolyzers,  
                periods,  
                electrolyzers.size(), 
                purchasedGridEnergy,
                totalElectroylzerPower
            );
    }
}
