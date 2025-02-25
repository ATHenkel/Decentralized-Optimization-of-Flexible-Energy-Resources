package DecentralizedOptimizationGurobi;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.apache.poi.ss.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;

public class DataExchange {
    Map<Integer, double[][]> X;                // X-Werte für jede Iteration
    Map<Integer, boolean[][][]> Y;             // Y-Werte für jede Iteration
    Map<Integer, double[][][]> S;              // S-Werte für jede Iteration
    Map<Integer, double[][][]> U;                // U-Werte für jede Iteration
    Map<Integer, double[][]> hydrogenProduction; // Wasserstoffproduktionsmenge für jede Iteration pro Agent
    Map<Integer, Double> iterationObjectiveValues = new HashMap<>();
    Map<Integer, Boolean> feasibilityMap = new HashMap<>();

    // Konstruktor
    public DataExchange() {
        // Initialisiere die HashMaps, um Daten für verschiedene Iterationen zu speichern
        this.X = new HashMap<>();
        this.Y = new HashMap<>();
        this.S = new HashMap<>();
        this.U = new HashMap<>();
        this.hydrogenProduction = new HashMap<>();
    }

    // Methode zum Initialisieren und Speichern von leeren Arrays für eine neue Iteration
    public void initializeIteration(int iteration, int numPer, int numAgents) {
        this.X.put(iteration, new double[numAgents][numPer]);
        this.Y.put(iteration, new boolean[numAgents][numPer][State.values().length]);
        this.S.put(iteration, new double[numAgents][numPer][2]);
        this.U.put(iteration, new double[numAgents][numPer][3]);
        this.hydrogenProduction.put(iteration, new double[numAgents][numPer]);
    }
    
    // Methode zum Speichern der Feasibility für eine Iteration
    public void saveFeasibilityForIteration(int iteration, boolean feasible) {
        feasibilityMap.put(iteration, feasible);
    }

    // Methode zum Abrufen der Feasibility für eine Iteration
    public boolean getFeasibilityForIteration(int iteration) {
        return feasibilityMap.getOrDefault(iteration, false);
    }
    
	 // Methode zum Speichern des Zielfunktionswerts für eine bestimmte Iteration
	    public void saveObjectiveValueForIteration(int iteration, double objectiveValue) {
	        iterationObjectiveValues.put(iteration, objectiveValue);
	    }

    
	 // Methode zum Abrufen des Zielfunktionswerts für eine bestimmte Iteration
	    public Double getObjectiveValueForIteration(int iteration) {
	        return iterationObjectiveValues.get(iteration);
	    }
	    
	 // Methode zum Abrufen des X-Wertes eines bestimmten Agenten und einer bestimmten Periode in einer bestimmten Iteration
	    public Double getXValueForAgentPeriod(int iteration, int agentIndex, int periodIndex) {
	        if (X.containsKey(iteration)) {
	            double[][] xValues = X.get(iteration);
	            if (agentIndex >= 0 && agentIndex < xValues.length && periodIndex >= 0 && periodIndex < xValues[agentIndex].length) {
	                return xValues[agentIndex][periodIndex]; // Gibt den X-Wert für den Agenten in der spezifischen Periode zurück
	            }
	        }
	        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
	    }
    
    // Methode zum Speichern der S-Werte für einen bestimmten Agenten und eine bestimmte Iteration
    public void saveSValuesForAgent(int iteration, int agentIndex, double[][] sValues) {
        // Stelle sicher, dass die Iteration existiert
        if (!S.containsKey(iteration)) {
            throw new IllegalArgumentException("Iteration " + iteration + " does not exist in the data.");
        }

        // Hole die S-Werte für die aktuelle Iteration
        double[][][] currentSValues = S.get(iteration);

        // Überprüfe, ob der agentIndex gültig ist
        if (agentIndex < 0 || agentIndex >= currentSValues.length) {
            throw new IndexOutOfBoundsException("Invalid agent index: " + agentIndex);
        }

        // Aktualisiere die S-Werte für den spezifischen Agenten
        for (int periodIndex = 0; periodIndex < sValues.length; periodIndex++) {
            currentSValues[agentIndex][periodIndex] = sValues[periodIndex];
        }

        // Setze die aktualisierten S-Werte zurück in die Map
        S.put(iteration, currentSValues);
    }
    
    // New method to save Y values for a specific agent in a specific iteration
    public void saveYValuesForAgent(int iteration, int agentIndex, boolean[][] yValues) {
        // Check if the Y map already contains the key for the specified iteration
        if (!Y.containsKey(iteration)) {
            // Initialize the array for the current iteration if it does not exist
            int numAgents = X.get(0).length;
            int numPeriods = X.get(0)[0].length;
            Y.put(iteration, new boolean[numAgents][numPeriods][State.values().length]);
        }

        // Get the Y values for the current iteration
        boolean[][][] iterationYValues = Y.get(iteration);

        // Update the Y values for the specific agent
        iterationYValues[agentIndex] = yValues;

        // Save the updated Y values back into the map
        Y.put(iteration, iterationYValues);
    }
    
    // Methode zum Speichern der X-Werte für einen bestimmten Agenten und eine bestimmte Iteration
    public void saveXValuesForAgent(int iteration, int agentIndex, double[] xValues) {
        if (X.containsKey(iteration)) {
            double[][] currentX = X.get(iteration);
            if (agentIndex >= 0 && agentIndex < currentX.length) {
                currentX[agentIndex] = xValues;
            }
        }
    }
    
 // Methode zum Speichern eines X-Wertes für eine bestimmte Iteration, Agenten und Periode
    public void saveXValueForPeriod(int iteration, int agentIndex, int periodIndex, double xValue) {
        if (!X.containsKey(iteration)) {
            // Initialisiere die Iteration, wenn sie noch nicht existiert
            int numAgents = X.get(0).length; // Anzahl der Agenten
            int numPeriods = X.get(0)[0].length; // Anzahl der Perioden
            X.put(iteration, new double[numAgents][numPeriods]);
        }

        // Speichere den X-Wert für den Agenten in der entsprechenden Periode
        X.get(iteration)[agentIndex][periodIndex] = xValue;
    }
    
 // Methode zum Speichern der HydrogenProduction für eine bestimmte Iteration, einen Agenten und eine Periode
    public void saveHydrogenProductionForPeriod(int iteration, int agentIndex, int periodIndex, double hydrogenProductionValue) {
        if (!hydrogenProduction.containsKey(iteration)) {
            // Initialisiere die Iteration, wenn sie noch nicht existiert
            int numAgents = hydrogenProduction.get(0).length; // Anzahl der Agenten
            int numPeriods = hydrogenProduction.get(0)[0].length; // Anzahl der Perioden
            hydrogenProduction.put(iteration, new double[numAgents][numPeriods]);
        }

        // Speichere den HydrogenProduction-Wert für den Agenten in der entsprechenden Periode
        hydrogenProduction.get(iteration)[agentIndex][periodIndex] = hydrogenProductionValue;
    }
    
 // Methode zum Speichern der X-Werte für eine bestimmte Iteration und einen Agenten
    public void saveXValuesForIteration(int iteration, int agentIndex, double[] xValuesForAgent) {
        if (!X.containsKey(iteration)) {
            // Initialisiere die Iteration, wenn sie noch nicht existiert
            int numAgents = X.get(0).length; // Anzahl der Agenten
            int numPeriods = xValuesForAgent.length; // Anzahl der optimierten Perioden
            X.put(iteration, new double[numAgents][numPeriods]);
        }

        // Speichere die X-Werte für den Agenten in der entsprechenden Iteration
        double[][] iterationXValues = X.get(iteration);
        System.arraycopy(xValuesForAgent, 0, iterationXValues[agentIndex], 0, xValuesForAgent.length);
    }

    // Methode zum Speichern der Wasserstoffproduktionsmenge für einen bestimmten Agenten und eine bestimmte Iteration
    public void saveHydrogenProductionForAgent(int iteration, int agentIndex, double[] productionValues) {
        if (hydrogenProduction.containsKey(iteration)) {
            double[][] currentProduction = hydrogenProduction.get(iteration);
            if (agentIndex >= 0 && agentIndex < currentProduction.length) {
                currentProduction[agentIndex] = productionValues;
            }
        }
    }
    
    public String getAllXValuesForIteration(int iteration) {
        // Überprüfen, ob die Iteration in der Map existiert
        if (!X.containsKey(iteration)) {
            return "Die angegebene Iteration " + iteration + " existiert nicht.";
        }

        // X-Werte für die angegebene Iteration abrufen
        double[][] xValuesForIteration = X.get(iteration);

        // StringBuilder zum Sammeln der Ausgabe
        StringBuilder result = new StringBuilder();
        result.append("X-Werte für Iteration ").append(iteration).append(":\n");

        // Iteriere über alle Agenten und Perioden und füge die Werte hinzu
        for (int agentIndex = 0; agentIndex < xValuesForIteration.length; agentIndex++) {
            result.append("Agent ").append(agentIndex + 1).append(":\n");  // +1 für menschenfreundliche Nummerierung
            for (int periodIndex = 0; periodIndex < xValuesForIteration[agentIndex].length; periodIndex++) {
                result.append("  Periode ").append(periodIndex + 1).append(": ");
                result.append(xValuesForIteration[agentIndex][periodIndex]).append("\n");
            }
        }

        // Rückgabe des gesammelten Strings
        return result.toString();
    }


    // Methode zum Speichern der Wasserstoffproduktionsmenge für eine bestimmte Iteration
    public void saveHydrogenProductionForIteration(int iteration, double[][] productionValues) {
        hydrogenProduction.put(iteration, productionValues);
    }

    // Methode zum Abrufen der Wasserstoffproduktionsmenge für eine bestimmte Iteration
    public double[][] getHydrogenProductionForIteration(int iteration) {
        return hydrogenProduction.get(iteration);
    }
    
    // Methode zum Abrufen der Wasserstoffproduktionsmenge für einen bestimmten Agenten und eine bestimmte Iteration
    public double[] getHydrogenProductionForAgent(int iteration, int agentIndex) {
        if (hydrogenProduction.containsKey(iteration)) {
            double[][] productionValues = hydrogenProduction.get(iteration);
            if (agentIndex >= 0 && agentIndex < productionValues.length) {
                return productionValues[agentIndex];
            }
        }
        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
    }
    
    // Methode, um alle Y-Werte auf "Production" für die erste Iteration zu setzen
    public void setYToIdleForFirstIteration() {
        int firstIteration = 0; // Assuming we are working with the first iteration

        // Retrieve the Y values for the first iteration
        boolean[][][] yValues = getYValuesForIteration(firstIteration);

        // Set all Y values to "Production" for the first iteration
        for (int i = 0; i < yValues.length; i++) {
            for (int j = 0; j < yValues[i].length; j++) {
                for (int k = 0; k < yValues[i][j].length; k++) {
                    yValues[i][j][k] = (k == State.IDLE.ordinal());
                }
            }
        }

        // Save the modified Y values back into the DataExchange class for the first iteration
        saveYValuesForIteration(firstIteration, yValues);
    }

    public void writeValuesToExcel_sorted(String filePath) {
        XSSFWorkbook workbook = new XSSFWorkbook();  // Neues Workbook erstellen
        XSSFSheet sheet = workbook.createSheet("Iteration Data");  // Neues Sheet erstellen

        // Erstelle die Headerzeile
        Row headerRow = sheet.createRow(0);
        int headerColumn = 0;
        headerRow.createCell(headerColumn++).setCellValue("Iteration");
        headerRow.createCell(headerColumn++).setCellValue("Objective Value");  // Neue Spalte für iterationObjectiveValue

        // Adding headers for each agent and period combination
        for (int agentIndex = 0; agentIndex < X.get(0).length; agentIndex++) {
            for (int periodIndex = 0; periodIndex < X.get(0)[agentIndex].length; periodIndex++) {
                String baseHeader = "A" + agentIndex + "_P" + (periodIndex + 1) + "_";  // Periodenindex um 1 erhöhen
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "X");
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "Y");
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "S1");
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "S2");
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "U1");
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "U2");
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "U3");
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "HydrogenProduction");
            }
        }

        // Erstelle ein Dezimalformat für 5 Dezimalstellen
        DataFormat format = workbook.createDataFormat();
        CellStyle decimalStyle = workbook.createCellStyle();
        decimalStyle.setDataFormat(format.getFormat("0.00000"));

        // Fülle die Zeilen mit den Werten
        int rowNum = 1;
        for (Integer iteration : X.keySet()) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;
            
            // Iterationsnummer in die erste Spalte schreiben
            row.createCell(colNum++).setCellValue(iteration);
            
            // iterationObjectiveValue in die zweite Spalte schreiben
            Double objectiveValue = iterationObjectiveValues.get(iteration);
            if (objectiveValue != null) {
                row.createCell(colNum++).setCellValue(objectiveValue);
            } else {
                row.createCell(colNum++).setCellValue("N/A");  // Falls kein Wert vorhanden ist
            }

            // Iteriere über die Agenten und Perioden, um die X, Y, S, U und HydrogenProduction zu schreiben
            for (int agentIndex = 0; agentIndex < X.get(iteration).length; agentIndex++) {
                for (int periodIndex = 0; periodIndex < X.get(iteration)[agentIndex].length; periodIndex++) {
                    // X-Wert
                    Cell cellX = row.createCell(colNum++);
                    cellX.setCellValue(X.get(iteration)[agentIndex][periodIndex]);
                    cellX.setCellStyle(decimalStyle);

                    // Aktiver Y-Zustand
                    String activeState = "None";
                    for (State state : State.values()) {
                        if (Y.get(iteration)[agentIndex][periodIndex][state.ordinal()]) {
                            activeState = state.name();
                            break;
                        }
                    }
                    Cell cellY = row.createCell(colNum++);
                    cellY.setCellValue(activeState);

                    // S-Werte
                    Cell cellS1 = row.createCell(colNum++);
                    cellS1.setCellValue(S.get(iteration)[agentIndex][periodIndex][0]);
                    cellS1.setCellStyle(decimalStyle);

                    Cell cellS2 = row.createCell(colNum++);
                    cellS2.setCellValue(S.get(iteration)[agentIndex][periodIndex][1]);
                    cellS2.setCellStyle(decimalStyle);

                    // U-Werte
                    for (int i = 0; i < 3; i++) {
                        Cell cellU = row.createCell(colNum++);
                        cellU.setCellValue(U.get(iteration)[agentIndex][periodIndex][i]);
                        cellU.setCellStyle(decimalStyle);
                    }

                    // Wasserstoffproduktionswert
                    Cell cellHydrogen = row.createCell(colNum++);
                    cellHydrogen.setCellValue(hydrogenProduction.get(iteration)[agentIndex][periodIndex]);
                    cellHydrogen.setCellStyle(decimalStyle);
                }
            }
        }

        // Schreibe die Arbeitsmappe in die Datei
        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

     // Getter-Methode für die X-Werte einer bestimmten Iteration
    public double[][] getXValuesForIteration(int iteration) {
        return X.get(iteration);
    }

    // Getter-Methode für die Y-Werte einer bestimmten Iteration
    public boolean[][][] getYValuesForIteration(int iteration) {
        return Y.get(iteration);
    }

    // Getter-Methode für die S-Werte einer bestimmten Iteration
    public double[][][] getSValuesForIteration(int iteration) {
        return S.get(iteration);
    }

    // Getter-Methode für die U-Werte einer bestimmten Iteration
    public double[][][] getUValuesForIteration(int iteration) {
        return U.get(iteration);
    }
    
 // Methode zum Speichern der X-Werte für eine bestimmte Iteration
    public void saveXValuesForIteration(int iteration, double[][] xValues) {
        X.put(iteration, xValues);
    }
    
 // Methode zum Speichern der Y-Werte für eine bestimmte Iteration
    public void saveYValuesForIteration(int iteration, boolean[][][] yValues) {
        Y.put(iteration, yValues);
    }
    
 // Methode zum Speichern der S-Werte für eine bestimmte Iteration
    public void saveSValuesForIteration(int iteration, double[][][] sValues) {
        S.put(iteration, sValues);
    }

 // Methode zum Speichern der U-Werte für eine bestimmte Iteration
    public void saveUValuesForIteration(int iteration, double[][][] uValues) {
        U.put(iteration, uValues);
    }
    
 // Methode zum Abrufen der X-Werte eines bestimmten Agenten für eine bestimmte Iteration
    public double[] getXValuesForAgent(int iteration, int agentIndex) {
        if (X.containsKey(iteration)) {
            double[][] xValues = X.get(iteration);
            if (agentIndex >= 0 && agentIndex < xValues.length) {
                return xValues[agentIndex]; // Gibt alle X-Werte für den Agenten in allen Perioden zurück
            }
        }
        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
    }

    // Methode zum Abrufen der Y-Werte eines bestimmten Agenten für eine bestimmte Iteration
    public boolean[][] getYValuesForAgent(int iteration, int agentIndex) {
        if (Y.containsKey(iteration)) {
            boolean[][][] yValues = Y.get(iteration);
            if (agentIndex >= 0 && agentIndex < yValues.length) {
                return yValues[agentIndex];
            }
        }
        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
    }

 // Methode zum Abrufen der S-Werte eines bestimmten Agenten für eine bestimmte Iteration
    public double[][] getSValuesForAgent(int iteration, int agentIndex) {
        if (S.containsKey(iteration)) {
            double[][][] sValues = S.get(iteration);
            if (agentIndex >= 0 && agentIndex < sValues.length) {
                return sValues[agentIndex];
            }
        }
        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
    }
    
 // Methode zum Abrufen der U-Werte eines bestimmten Agenten für eine bestimmte Iteration
    public double[][] getUValuesForAgent(int iteration, int agentIndex) {
        if (U.containsKey(iteration)) {
            double[][][] uValues = U.get(iteration);
            if (agentIndex >= 0 && agentIndex < uValues.length) {
                return uValues[agentIndex];
            }
        }
        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
    }
    
 // U's zurückschreiben
    public void saveUValuesForAgent(int iteration, int agentIndex, double[][] uValues) {
    	if (U.containsKey(iteration)) {
            double[][][] currentU = U.get(iteration);
            if (agentIndex >= 0 && agentIndex < currentU.length) {
                currentU[agentIndex] = uValues;
            }
        }
    }
    
    //ResidualIndex ist die dritte Dimension, also ob 0, 1 oder 2
    public void saveUValueForAgentPeriod(int iteration, int agentIndex, int periodIndex, int residualIndex, double uValue) {
        // Stelle sicher, dass die Iteration existiert
        if (!U.containsKey(iteration)) {
            throw new IllegalArgumentException("Iteration " + iteration + " does not exist in the data.");
        }

        // Hole die U-Werte für die aktuelle Iteration
        double[][][] currentUValues = U.get(iteration);

        // Überprüfe, ob der agentIndex und periodIndex gültig sind
        if (agentIndex < 0 || agentIndex >= currentUValues.length) {
            throw new IndexOutOfBoundsException("Invalid agent index: " + agentIndex);
        }
        if (periodIndex < 0 || periodIndex >= currentUValues[agentIndex].length) {
            throw new IndexOutOfBoundsException("Invalid period index: " + periodIndex);
        }
        if (residualIndex < 0 || residualIndex >= currentUValues[agentIndex][periodIndex].length) {
            throw new IndexOutOfBoundsException("Invalid residual index: " + residualIndex);
        }

        // Speichere den U-Wert für den spezifischen Agenten, Periode und Residual-Index
        currentUValues[agentIndex][periodIndex][residualIndex] = uValue;

        // Setze die aktualisierten U-Werte zurück in die Map
        U.put(iteration, currentUValues);
    }
}
