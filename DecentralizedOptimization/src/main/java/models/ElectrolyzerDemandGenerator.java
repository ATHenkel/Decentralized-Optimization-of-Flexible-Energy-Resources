package models;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

public class ElectrolyzerDemandGenerator {

    public static void main(String[] args) {
        // Parameter: Number of electrolyzers and slope
        int numberOfElectrolyzers = 100; // Example: 100 electrolyzers
        double slope = 0.8; // Example: Slope value

        generateExcelSheet(numberOfElectrolyzers, slope);
    }

    public static void generateExcelSheet(int numberOfElectrolyzers, double slope) {
        // Number of periods
        int numberOfPeriods = 96;
        
        // Create Excel workbook and the first sheet
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet1 = workbook.createSheet("Electrolyzer Demand");

        // Create header for the first sheet
        Row headerRow1 = sheet1.createRow(0);
        headerRow1.createCell(0).setCellValue("Electrolyzer_ID");
        for (int period = 1; period <= numberOfPeriods; period++) {
            headerRow1.createCell(period).setCellValue("Period_" + period);
        }

        // Generate random demand and state and insert into the first sheet
        Random random = new Random();
        double[][] demands = new double[numberOfElectrolyzers][numberOfPeriods];

        for (int i = 0; i < numberOfElectrolyzers; i++) {
            Row row = sheet1.createRow(i + 1);
            row.createCell(0).setCellValue("Electrolyzer_" + (i + 1));

            for (int period = 0; period < numberOfPeriods; period++) {
                boolean isOn = random.nextDouble() > 0.5;
                double demand = isOn ? (0.2 + (0.8 - 0.2) * random.nextDouble()) * slope : 0.0;
                demands[i][period] = demand;
                row.createCell(period + 1).setCellValue(demand);
            }
        }

        // Create the second sheet for calculating total demand per period
        Sheet sheet2 = workbook.createSheet("Total Demand and Prices");
        Row headerRow2 = sheet2.createRow(0);
        headerRow2.createCell(0).setCellValue("Period");
        headerRow2.createCell(1).setCellValue("Total Demand");
        headerRow2.createCell(2).setCellValue("Electricity Price");

        for (int period = 0; period < numberOfPeriods; period++) {
            Row row = sheet2.createRow(period + 1);
            row.createCell(0).setCellValue("Period_" + (period + 1));

            // Calculate total demand per period
            double totalDemand = 0;
            for (int i = 0; i < numberOfElectrolyzers; i++) {
                totalDemand += demands[i][period];
            }
            row.createCell(1).setCellValue(totalDemand);

            // Generate a random electricity price (e.g., between 50 and 100 Euro/MWh)
            double electricityPrice = 20 + (100 - 50) * random.nextDouble();
            row.createCell(2).setCellValue(electricityPrice);
        }

        // Save Excel file
        try (FileOutputStream fileOut = new FileOutputStream("ElectrolyzerDemand.xlsx")) {
            workbook.write(fileOut);
            workbook.close();
            System.out.println("Excel file 'ElectrolyzerDemand.xlsx' was successfully created.");
        } catch (IOException e) {
            System.err.println("Error creating Excel file: " + e.getMessage());
        }
    }
}


