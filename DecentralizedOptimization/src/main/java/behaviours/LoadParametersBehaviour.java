package behaviours;

import jade.core.behaviours.OneShotBehaviour;
import models.*;
import org.apache.poi.ss.usermodel.*;

import java.util.*;

public class LoadParametersBehaviour extends OneShotBehaviour {

    private static final long serialVersionUID = 1L;
    private Workbook workbook;
    private ADMMDataModel dataModel; // Reference to the agent's ADMMDataModel
    private Parameters params;       // Loaded parameters are stored here

    // Constructor: Pass workbook and ADMMDataModel
    public LoadParametersBehaviour(Workbook workbook, ADMMDataModel dataModel) {
        this.workbook = workbook;
        this.dataModel = dataModel;
    }

    @Override
    public void action() {
        // Load parameters from Excel workbook
        params = loadParameters(workbook);
        
        // Insert loaded parameters into ADMMDataModel
        dataModel.setParameters(params); 
        
        // Optional output for verification
        System.out.println("Parameters successfully loaded and inserted into ADMMDataModel.");
    }

    /**
     * Method for loading parameters from Excel file
     */
    private static Parameters loadParameters(Workbook workbook) {
        Sheet agentsSheet = workbook.getSheet("Agent_heterogen");
        Sheet periodsSheet = workbook.getSheet("Periods");
        Sheet parametersSheet = workbook.getSheet("GlobalParameters");

        // Loading agent parameters
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

        // Minimum holding duration for states (Idle, Starting, Production, Standby)
        Map<Electrolyzer, Map<State, Integer>> holdingDurations = new HashMap<>();

        // Reading electrolyzer data from agent table
        for (Row row : agentsSheet) {
            if (row.getRowNum() == 0)
                continue;

            // Creating a new Electrolyzer object with all required parameters
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

            // Saving parameters in respective maps
            powerElectrolyzer.put(electrolyzer, electrolyzer.getPowerElectrolyzer());
            minOperation.put(electrolyzer, electrolyzer.getMinOperation());
            maxOperation.put(electrolyzer, electrolyzer.getMaxOperation());
            slope.put(electrolyzer, electrolyzer.getSlope());
            intercept.put(electrolyzer, electrolyzer.getIntercept());
            startupDuration.put(electrolyzer, electrolyzer.getStartupDuration());
            startupCost.put(electrolyzer, electrolyzer.getStartupCost());
            standbyCost.put(electrolyzer, electrolyzer.getStandbyCost());

            // Loading holding durations (Idle, Starting, Production, Standby)
            Map<State, Integer> electrolyzerHoldingDurations = new HashMap<>();
            electrolyzerHoldingDurations.put(State.IDLE, (int) row.getCell(8).getNumericCellValue());       // Column J
            electrolyzerHoldingDurations.put(State.STARTING, (int) row.getCell(9).getNumericCellValue());  // Column K
            electrolyzerHoldingDurations.put(State.PRODUCTION, (int) row.getCell(10).getNumericCellValue()); // Column L
            electrolyzerHoldingDurations.put(State.STANDBY, (int) row.getCell(11).getNumericCellValue());   // Column M
            rampRate.put(electrolyzer, row.getCell(12).getNumericCellValue());           // Column M

            // Insert holding durations into map
            holdingDurations.put(electrolyzer, electrolyzerHoldingDurations);
            electrolyzers.add(electrolyzer);
        }

        // Loading period-specific parameters, including demand
        Map<Period, Double> electricityPrice = new HashMap<>();
        Map<Period, Double> availablePower = new HashMap<>();
        Map<Period, Double> periodDemand = new HashMap<>();
        Map<Period, Double> purchasedGridEnergy = new HashMap<>();
        Map<Period, Double> totalElectroylzerPower = new HashMap<>();
        Set<Period> periods = new HashSet<>();

        // Reading period data from periods table
        for (Row row : periodsSheet) {
            if (row.getRowNum() == 0)
                continue;
            Period period = new Period((int) row.getCell(0).getNumericCellValue()); // Column A
            electricityPrice.put(period, row.getCell(1).getNumericCellValue());      // Column B
            availablePower.put(period, row.getCell(2).getNumericCellValue());        // Column C
            periodDemand.put(period, row.getCell(3).getNumericCellValue());          // Column D
            periods.add(period);                                        
        }

        // Global Parameters
        double intervalLength = parametersSheet.getRow(1).getCell(1).getNumericCellValue();
        double demandDeviationCost = parametersSheet.getRow(2).getCell(1).getNumericCellValue();

        // Return parameters including total number of electrolyzers
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
                rampRate, // Add rampRate as parameter
                electrolyzers,  
                periods,  
                electrolyzers.size(), 
                purchasedGridEnergy,
                totalElectroylzerPower
            );
    }
}
