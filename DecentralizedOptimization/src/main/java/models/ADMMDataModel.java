package models;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import jade.core.AID;

import org.apache.poi.ss.usermodel.*;

public class ADMMDataModel {
    Map<Integer, double[][]> xSWO;                // X-Werte für jede Iteration
    Map<Integer, double[][]> xRTO;                // X-Werte für jede Iteration
    Map<Integer, boolean[][][]> ySWO;             // Y-Werte für jede Iteration
    Map<Integer, double[][][]> sSWO;              // S-Werte für jede Iteration
    Map<Integer, double[][][]> sRTO;              // S-Werte für jede Iteration
    Map<Integer, double[][][]> uSWO;                // U-Werte für jede Iteration
    Map<Integer, double[][][]> uRTO;                // U-Werte für jede Iteration
    Map<Integer, double[][]> hydrogenProductionSWO; // Wasserstoffproduktionsmenge für jede Iteration pro Agent
    Map<Integer, Double> iterationObjectiveValuesSWO = new HashMap<>();
    Map<Integer, Boolean> feasibilityMapSWO = new HashMap<>();
    private Map<Integer, Double> energyBalanceResult;
    private Map<Integer, Double> dualVariableEnergyBalance;
    private double[][] fluctuatingRenewableEnergyMatrix;
    private int rtoStepsPerSWOPeriod =10;
    int maxIterations;
    boolean dualUpdateCompleted;
    
    
    public boolean isDualUpdateCompleted() {
		return dualUpdateCompleted;
	}

	public void setDualUpdateCompleted(boolean dualUpdateCompleted) {
		this.dualUpdateCompleted = dualUpdateCompleted;
	}

	public int getMaxIterations() {
		return maxIterations;
	}

	public void setMaxIterations(int maxIterations) {
		this.maxIterations = maxIterations;
	}

	public void setFluctuatingRenewableEnergyMatrix(double[][] matrix) {
        this.fluctuatingRenewableEnergyMatrix = matrix;
    }
    public double[][] getFluctuatingRenewableEnergyMatrix() {
        return fluctuatingRenewableEnergyMatrix;
    }
    
    public void resetXRTO() {
        xRTO = new HashMap<Integer, double[][]>();
    }

    private List<AID> phoneBook;
    
    private Map<Integer, double[]> xSWOResults = new HashMap<>();
    
    /**
     * Speichert den x-Wert für einen Agenten und eine bestimmte Periode.
     *
     * @param agentId   Die ID (oder der Index) des Elektrolyseurs.
     * @param period    Die Periode (z. B. 0 bis rtoStepsPerSWOPeriod - 1).
     * @param xValue    Der zu speichernde x-Wert.
     */
    public void saveXSWOResultForAgentPeriod(int agentId, int period, double xValue) {
        double[] results = xSWOResults.get(agentId);
        if (results == null) {
            // Angenommen, rtoStepsPerSWOPeriod ist bekannt (zum Beispiel 10)
            results = new double[99]; 
            xSWOResults.put(agentId, results);
        }
        results[period] = xValue;
    }
    
    /**
     * Liefert den x-Wert für einen Agenten in einer bestimmten Periode.
     *
     * @param agentId   Die ID (oder der Index) des Elektrolyseurs.
     * @param period    Die Periode.
     * @return          Der gespeicherte x-Wert, oder 0.0, falls nicht vorhanden.
     */
    public double getXSWOResultForAgentPeriod(int agentId, int period) {
        double[] results = xSWOResults.get(agentId);
        return (results != null) ? results[period] : 0.0;
    }


    /**
     * Liefert das Array aller x-Werte für einen Agenten.
     *
     * @param agentId   Die ID des Elektrolyseurs.
     * @return          Das Array der x-Werte, oder null, falls noch nicht gespeichert.
     */
    public double[] getXSWOResultsForAgent(int agentId) {
        return xSWOResults.get(agentId);
    }
    
    
    private Map<Integer, double[]> xRTOResults = new HashMap<>();    /**
     * Speichert den x-Wert für einen Agenten und eine bestimmte Periode.
     *
     * @param agentId   Die ID (oder der Index) des Elektrolyseurs.
     * @param period    Die Periode (z. B. 0 bis rtoStepsPerSWOPeriod - 1).
     * @param xValue    Der zu speichernde x-Wert.
     */
    public void saveXRTOResultForAgentPeriod(int agentId, int period, double xValue) {
        double[] results = xRTOResults.get(agentId);
        if (results == null) {
            // Angenommen, rtoStepsPerSWOPeriod ist bekannt (zum Beispiel 10)
            results = new double[10]; 
            xRTOResults.put(agentId, results);
        }
        results[period] = xValue;
    }
    
    /**
     * Liefert den x-Wert für einen Agenten in einer bestimmten Periode.
     *
     * @param agentId   Die ID (oder der Index) des Elektrolyseurs.
     * @param period    Die Periode.
     * @return          Der gespeicherte x-Wert, oder 0.0, falls nicht vorhanden.
     */
    public double getXRTOResultForAgentPeriod(int agentId, int period) {
        double[] results = xRTOResults.get(agentId);
        return (results != null) ? results[period] : 0.0;
    }


    /**
     * Liefert das Array aller x-Werte für einen Agenten.
     *
     * @param agentId   Die ID des Elektrolyseurs.
     * @return          Das Array der x-Werte, oder null, falls noch nicht gespeichert.
     */
    public double[] getXRTOResultsForAgent(int agentId) {
        return xRTOResults.get(agentId);
    }

    
    private long computationTime; 
    private long startComputationTime;
    private long startRTOComputationTime;
    private long finalRTOComputationTime;
	private Set<Period> assignedPeriods; 
    private Set<Electrolyzer> allElectrolyzers;
    private Map<Integer, Double> penaltyXvalues = new HashMap<>();
    private Map<Integer, Double> penaltyYvalues = new HashMap<>();
    private Map<Integer, Double> xObjectives; // Speicher für X-Objectives
    private Map<Integer, Double> yObjectives; // Speicher für Y-Objectives
    private final Map<Integer, Double> primalResiduals = new HashMap<>();
    private final Map<Integer, Double> dualResiduals = new HashMap<>();
    private int totalReceivedMessages = 0;    
    
    private Map<Integer, Integer> sentMessagesMap = new HashMap<>();
    private Map<Integer, Integer> receivedMessagesMap = new HashMap<>();
    private Map<Integer, Integer> receivedDualMessagesMap = new HashMap<>();
    private Map<Integer, Integer> receivedXMessagesMap = new HashMap<>();
    private Map<Integer, Long> xUpdateTimeMap = new HashMap<>();
    private Map<Integer, Long> yUpdateTimeMap = new HashMap<>();
    private Map<Integer, Long> dualUpdateTimeMap = new HashMap<>();
    private Map<Integer, Long> messageSendReceiveTimeMap = new HashMap<>();
    private Map<Integer, Long> sUpdateTimeMap = new HashMap<>();
    private Map<Integer, Long> startTimeMap = new HashMap<>();
    
    private Map<Integer, Map<Integer, Double>> rampPenalties = new HashMap<>();
    private Map<Integer, Map<Integer, Double>> demandDeviationPenalties = new HashMap<>();
        
    private Map<Integer, Map<Integer, Map<Integer, double[]>>> yResiduals;

    // Neue Variablen zur Speicherung der Parameter
    private Map<Electrolyzer, Double> powerElectrolyzer;
    private Map<Electrolyzer, Double> minOperation;
    private Map<Electrolyzer, Double> maxOperation;
    private Map<Electrolyzer, Double> slope;
    private Map<Electrolyzer, Double> intercept;
    private Map<Electrolyzer, Double> startupCost;
    private Map<Electrolyzer, Double> standbyCost;
    private Map<Electrolyzer, Integer> startupDuration;
    private Map<Electrolyzer, Map<State, Integer>> holdingDurations;
    private Map<Period, Double> electricityPrice;
    private Map<Period, Double> periodDemand;
    private Map<Period, Double> availablePower;
    private double intervalLengthSWO;
    private double demandDeviationCost;
    
    private int rtoStepsPerSWO = 10; 
    
    public long getStartRTOComputationTime() {
		return startRTOComputationTime;
	}

	public void setStartRTOComputationTime(long startRTOComputationTime) {
		this.startRTOComputationTime = startRTOComputationTime;
	}
	
	public long getFinalRTOComputationTime() {
		return finalRTOComputationTime;
	}

	public void setFinalRTOComputationTime(long endRTOComputationTime) {
		this.finalRTOComputationTime = endRTOComputationTime;
	}
    
    public int getRtoStepsPerSWO() {
    	rtoStepsPerSWO = 10;
		return rtoStepsPerSWO;
	}

	public void setRtoStepsPerSWO(int rtoStepsPerSWO) {
		this.rtoStepsPerSWO = rtoStepsPerSWO;
	}

	// Variablen zur Speicherung der Parameter
    private Parameters parameters;
    
    // Konstruktor
    public ADMMDataModel() {
        // Initialisiere die HashMaps, um Daten für verschiedene Iterationen zu speichern
        this.xSWO = new HashMap<>();
        this.xRTO = new HashMap<>();
        this.ySWO = new HashMap<>();
        this.sSWO = new HashMap<>();
        this.sRTO = new HashMap<>();
        this.uSWO = new HashMap<>();
        this.uRTO = new HashMap<>();
        this.hydrogenProductionSWO = new HashMap<>();
        xObjectives = new HashMap<>(); 
        yObjectives = new HashMap<>(); 
        yResiduals = new HashMap<>();
        this.dualVariableEnergyBalance = new HashMap<>();
        this.energyBalanceResult = new HashMap<>();    
    }
    
    
    // Getter für eine spezifische Iteration
    public double getEnergyBalanceResultForIteration(int iteration) {
        return energyBalanceResult.getOrDefault(iteration, 0.0); // Falls nicht vorhanden, Standardwert 0.0
    }

    // Setter für eine spezifische Iteration
    public void setEnergyResultForIteration(int iteration, double value) {
    	energyBalanceResult.put(iteration, value);
    }
    
    // Getter für eine spezifische Iteration
    public double getEnergyBalanceDualVariable(int iteration) {
        return dualVariableEnergyBalance.getOrDefault(iteration, 0.0); // Falls nicht vorhanden, Standardwert 0.0
    }

    public void setEnergyBalanceDualVariable(int iteration, double value) {
        dualVariableEnergyBalance.put(iteration, value);
    }
    
    // Residual-Werte speichern
    public void saveYResiduals(int iteration, int electrolyzerID, int periodIndex, double[] residualValues) {
        yResiduals.computeIfAbsent(iteration, k -> new HashMap<>())
                 .computeIfAbsent(electrolyzerID, k -> new HashMap<>())
                 .put(periodIndex, residualValues);
    }

    // Residual-Werte abrufen
    public double[] getYSWOResiduals(int iteration, int electrolyzerID, int periodIndex) {
        return yResiduals.getOrDefault(iteration, new HashMap<>())
                        .getOrDefault(electrolyzerID, new HashMap<>())
                        .getOrDefault(periodIndex, null);
    }

    // Alle Residual-Werte für eine Iteration abrufen
    public Map<Integer, Map<Integer, double[]>> getYResidualsForIteration(int iteration) {
        return yResiduals.getOrDefault(iteration, new HashMap<>());
    }

    // Prüfen, ob Residuals für eine Iteration gespeichert sind
    public boolean hasYResiduals(int iteration) {
        return yResiduals.containsKey(iteration);
    }

    public void savePrimalResidualForIteration(int iteration, double residual) {
        primalResiduals.put(iteration, residual);
    }

    public void saveDualResidualForIteration(int iteration, double residual) {
        dualResiduals.put(iteration, residual);
    }

    public double getPrimalResidualForIteration(int iteration) {
        return primalResiduals.getOrDefault(iteration, 0.0);
    }

    public double getDualResidualForIteration(int iteration) {
        return dualResiduals.getOrDefault(iteration, 0.0);
    }
    
    
    public void saveYObjective(int iteration, double yObjectiveValue) {
        if (yObjectives == null) {
            yObjectives = new HashMap<>();
        }
        yObjectives.put(iteration, yObjectiveValue);
    }

    // Rufe das Y-Objective für eine bestimmte Iteration ab
    public Double getYObjectiveForIteration(int iteration) {
        return yObjectives.getOrDefault(iteration, 100000.0);  // Gibt null zurück, wenn kein Wert vorhanden ist
    }

    // Überprüfe, ob ein Y-Objective gespeichert ist
    public boolean hasYObjectiveForIteration(int iteration) {
        return yObjectives.containsKey(iteration);
    }
    
    // Speichere das X-Objective für eine bestimmte Iteration
    public void saveXObjective(int iteration, double xObjectiveValue) {
        xObjectives.put(iteration, xObjectiveValue);
    }

 // Rufe das X-Objective für eine bestimmte Iteration ab
    public Double getXObjectiveForIteration(int iteration) {
        return xObjectives.getOrDefault(iteration, 0.0); // Gibt 0.0 zurück, wenn kein Wert vorhanden ist
    }

    // Optionale Methode: Überprüfe, ob ein X-Objective für eine Iteration gespeichert ist
    public boolean hasXObjectiveForIteration(int iteration) {
        return xObjectives.containsKey(iteration);
    }
    
    public void saveXPenaltyForIteration(int iteration, double penaltyValue) {
        // Beispielhafte Speicherung in einer Map
        penaltyXvalues.put(iteration, penaltyValue);
    }

    public double getXPenaltyForIteration(int iteration) {
        return penaltyXvalues.getOrDefault(iteration, 0.0);
    }
    
    public void saveYPenaltyForIteration(int iteration, double penaltyValue) {
        // Beispielhafte Speicherung in einer Map
        penaltyYvalues.put(iteration, penaltyValue);
        System.out.println("Penalty value for iteration " + iteration + ": " + penaltyValue);
    }

    public double getYPenaltyForIteration(int iteration) {
        return penaltyYvalues.getOrDefault(iteration, 0.0);
    }

    // Getter für die gesamte Anzahl empfangener Nachrichten
    public int getTotalReceivedMessages() {
        return totalReceivedMessages;
    }

    // Setter für die gesamte Anzahl empfangener Nachrichten
    public void setTotalReceivedMessages(int receivedMessages) {
        this.totalReceivedMessages = receivedMessages;
    }
    
    public double getSSWOValueForAgentPeriod(int iteration, int agentIndex, int periodIndex, int sIndex) {
        // Überprüfen, ob die Iteration existiert
        if (!sSWO.containsKey(iteration)) {
            throw new IllegalArgumentException("Iteration " + iteration + " existiert nicht in den S-Werten.");
        }

        // Hole die S-Werte für die aktuelle Iteration
        double[][][] currentSValues = sSWO.get(iteration);

        // Überprüfen, ob Agenten-, Perioden- und S-Index gültig sind
        if (agentIndex < 0 || agentIndex >= currentSValues.length) {
            throw new IndexOutOfBoundsException("Ungültiger Agentenindex: " + agentIndex);
        }
        if (periodIndex < 0 || periodIndex >= currentSValues[agentIndex].length) {
            throw new IndexOutOfBoundsException("Ungültiger Periodenindex: " + periodIndex);
        }
        if (sIndex < 0 || sIndex >= currentSValues[agentIndex][periodIndex].length) {
            throw new IndexOutOfBoundsException("Ungültiger S-Index: " + sIndex);
        }

        // Rückgabe des spezifischen S-Wertes
        return currentSValues[agentIndex][periodIndex][sIndex];
    }
    
    public double getSRTOValueForAgentPeriod(int iteration, int agentIndex, int periodIndex, int sIndex) {
        // Überprüfen, ob die Iteration existiert
        if (!sRTO.containsKey(iteration)) {
            throw new IllegalArgumentException("Iteration " + iteration + " existiert nicht in den S-Werten.");
        }

        // Hole die S-Werte für die aktuelle Iteration
        double[][][] currentSValues = sRTO.get(iteration);

        // Überprüfen, ob Agenten-, Perioden- und S-Index gültig sind
        if (agentIndex < 0 || agentIndex >= currentSValues.length) {
            throw new IndexOutOfBoundsException("Ungültiger Agentenindex: " + agentIndex);
        }
        if (periodIndex < 0 || periodIndex >= currentSValues[agentIndex].length) {
            throw new IndexOutOfBoundsException("Ungültiger Periodenindex: " + periodIndex);
        }
        if (sIndex < 0 || sIndex >= currentSValues[agentIndex][periodIndex].length) {
            throw new IndexOutOfBoundsException("Ungültiger S-Index: " + sIndex);
        }

        // Rückgabe des spezifischen S-Wertes
        return currentSValues[agentIndex][periodIndex][sIndex];
    }
    
    public double getUValueForAgentPeriod(int iteration, int agentIndex, int periodIndex, int residualIndex) {
        // Überprüfen, ob die Iteration existiert
        if (!uSWO.containsKey(iteration)) {
            throw new IllegalArgumentException("Iteration " + iteration + " existiert nicht in den U-Werten.");
        }

        // Hole die U-Werte für die aktuelle Iteration
        double[][][] currentUValues = uSWO.get(iteration);

        // Überprüfen, ob Agenten-, Perioden- und Residualindex gültig sind
        if (agentIndex < 0 || agentIndex >= currentUValues.length) {
            throw new IndexOutOfBoundsException("Ungültiger Agentenindex: " + agentIndex);
        }
        if (periodIndex < 0 || periodIndex >= currentUValues[agentIndex].length) {
            throw new IndexOutOfBoundsException("Ungültiger Periodenindex: " + periodIndex);
        }
        if (residualIndex < 0 || residualIndex >= currentUValues[agentIndex][periodIndex].length) {
            throw new IndexOutOfBoundsException("Ungültiger Residualindex: " + residualIndex);
        }

        // Rückgabe des spezifischen U-Wertes
        return currentUValues[agentIndex][periodIndex][residualIndex];
    }

    // Methode zum Hinzufügen empfangener Nachrichten
    public void addReceivedMessages(int iteration, int receivedMessages) {
        // Aktualisiere die Gesamtzahl der empfangenen Nachrichten
        totalReceivedMessages += receivedMessages;
        // Speichere die Anzahl für die Iteration
        receivedMessagesMap.put(iteration, receivedMessagesMap.getOrDefault(iteration, 0) + receivedMessages);
    }

    public void setAllElectrolyzers(Set<Electrolyzer> allElectrolyzers) {
        this.allElectrolyzers = allElectrolyzers;
    }

    public Set<Electrolyzer> getAllElectrolyzers() {
        return allElectrolyzers;
    }
    
    // Setter methods for penalties
    public void saveRampPenalty(int iteration, int period, double penalty) {
        rampPenalties.putIfAbsent(iteration, new HashMap<>());
        rampPenalties.get(iteration).put(period, penalty);
    }

    public void saveDemandDeviationPenalty(int iteration, int period, double penalty) {
        demandDeviationPenalties.putIfAbsent(iteration, new HashMap<>());
        demandDeviationPenalties.get(iteration).put(period, penalty);
    }
    
    public int getSentMessagesForIteration(int iteration) {
        return sentMessagesMap.getOrDefault(iteration, 0);
    }

    public int getReceivedMessagesForIteration(int iteration) {
        return receivedMessagesMap.getOrDefault(iteration, 0);
    }

    public long getXUpdateTimeForIteration(int iteration) {
        return xUpdateTimeMap.getOrDefault(iteration, 0L);
    }

    public long getYUpdateTimeForIteration(int iteration) {
        return yUpdateTimeMap.getOrDefault(iteration, 0L);
    }

    public long getDualUpdateTimeForIteration(int iteration) {
        return dualUpdateTimeMap.getOrDefault(iteration, 0L);
    }

    public long getMessageSendReceiveTimeForIteration(int iteration) {
        return messageSendReceiveTimeMap.getOrDefault(iteration, 0L);
    }
    
    public long getReceivedXMessagePerIteration(int iteration) {
        return receivedXMessagesMap.getOrDefault(iteration, 0);
    }
    
    public long getReceivedDualMessagePerIteration(int iteration) {
        return receivedDualMessagesMap.getOrDefault(iteration, 99);
    }
    
    public long getSUpdateTimeForIteration(int iteration) {
        return sUpdateTimeMap.getOrDefault(iteration, 0L);
    }
    
    public void saveSentMessagesForIteration(int iteration, int sentMessages) {
        sentMessagesMap.put(iteration, sentMessages);
    }
    
    public void saveStartTime(int iteration, long startTime) {
        startTimeMap.put(iteration, startTime);
    }
    
    public long getStartTimeForIteration(int iteration) {
        return startTimeMap.getOrDefault(iteration, 0L);
    }

    public void saveReceivedMessagesForIteration(int iteration, int receivedMessages) {
        receivedMessagesMap.put(iteration, receivedMessages);
    }
    
    public void saveReceivedDualMessagesForIteration(int iteration, int receivedMessages) {
        receivedDualMessagesMap.put(iteration, receivedMessages);
    }
    
    public void saveReceivedXMessagesForIteration(int iteration, int receivedMessages) {
        receivedXMessagesMap.put(iteration, receivedMessages);
    }

    public void saveXUpdateTimeForIteration(int iteration, long xUpdateTime) {
        xUpdateTimeMap.put(iteration, xUpdateTime);
    }

    public void saveYUpdateTimeForIteration(int iteration, long yUpdateTime) {
        yUpdateTimeMap.put(iteration, yUpdateTime);
    }

    public void saveDualUpdateTimeForIteration(int iteration, long dualUpdateTime) {
        dualUpdateTimeMap.put(iteration, dualUpdateTime);
    }

    public void saveMessageSendReceiveTimeForIteration(int iteration, long messageSendReceiveTime) {
        messageSendReceiveTimeMap.put(iteration, messageSendReceiveTime);
    }
    
    public void saveSUpdateTimeForIteration(int iteration, long sUpdateTime) {
        sUpdateTimeMap.put(iteration, sUpdateTime);
    }

	 public void setAssignedPeriods(Set<Period> periods) {
	     this.assignedPeriods = periods;
	 }

	 public Set<Period> getAssignedPeriods() {
	     return this.assignedPeriods;
	 }

    public void setComputationTime(long computationTime) {
        this.computationTime = computationTime;
    }

    public long getComputationTime() {
        return computationTime;
    }
    
    public void setStartComputationTime(long startComputationTime) {
        this.startComputationTime = startComputationTime;
    }

    public long getStartComputationTime() {
        return startComputationTime;
    }
    
    private long startTimeXSWOUpdate;
    public void setStartXSWOUpdateComputationTime(long startComputationTime) {
        this.startTimeXSWOUpdate = startComputationTime;
    }

    public long getStartTimeXSWOComputationTime() {
        return startTimeXSWOUpdate;
    }
    
    public boolean getYSWOValueForAgentPeriod(int iteration, int agentIndex, int periodIndex, int stateIndex) {
        if (!ySWO.containsKey(iteration)) {
            throw new IllegalArgumentException("Die Iteration " + iteration + " existiert nicht.");
        }

        boolean[][][] yValues = ySWO.get(iteration);

        // Validierung der Indizes
        if (agentIndex < 0 || agentIndex >= yValues.length) {
            throw new IndexOutOfBoundsException("Ungültiger Agentenindex: " + agentIndex);
        }
        if (periodIndex < 0 || periodIndex >= yValues[agentIndex].length) {
            throw new IndexOutOfBoundsException("Ungültiger Periodenindex: " + periodIndex);
        }
        if (stateIndex < 0 || stateIndex >= yValues[agentIndex][periodIndex].length) {
            throw new IndexOutOfBoundsException("Ungültiger Zustandsindex: " + stateIndex);
        }

        return yValues[agentIndex][periodIndex][stateIndex];
    }

    
    // Phone book management
    public void addAID2PhoneBook(AID agentAID) {
        getPhoneBook().add(agentAID);
    }

    public List<AID> getPhoneBook() {
        if (phoneBook == null) {
            phoneBook = new ArrayList<>();
        }
        return phoneBook;
    }

    public void setPhoneBook(List<AID> phoneBook) {
        this.phoneBook = phoneBook;
    }
    
    public void initializeAllIterations(int maxIterations, int numPer) {
        int totalElectrolyzers = this.parameters.getTotalElectrolyzers(); 
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            this.xSWO.put(iteration, new double[totalElectrolyzers][numPer]);
            this.xRTO.put(iteration, new double[totalElectrolyzers][rtoStepsPerSWO]);
            this.ySWO.put(iteration, new boolean[totalElectrolyzers][numPer][State.values().length]);
            this.sSWO.put(iteration, new double[totalElectrolyzers][numPer][2]);
            this.sRTO.put(iteration, new double[totalElectrolyzers][rtoStepsPerSWO][2]);
            this.uSWO.put(iteration, new double[totalElectrolyzers][numPer][3]);
            this.uRTO.put(iteration, new double[totalElectrolyzers][rtoStepsPerSWO][4]);
            this.hydrogenProductionSWO.put(iteration, new double[totalElectrolyzers][numPer]);

            // Initialisiere yResiduals für die aktuelle Iteration
            Map<Integer, Map<Integer, double[]>> electrolyzerResiduals = new HashMap<>();
            for (int electrolyzer = 0; electrolyzer < totalElectrolyzers; electrolyzer++) {
                Map<Integer, double[]> periodResiduals = new HashMap<>();
                for (int period = 0; period < numPer; period++) {
                    // Initialisiere ein Array für die 3 Residualwerte
                    periodResiduals.put(period, new double[3]);
                }
                electrolyzerResiduals.put(electrolyzer, periodResiduals);
            }
            this.yResiduals.put(iteration, electrolyzerResiduals);
        }
    }

    // Methode zum Speichern der Feasibility für eine Iteration
    public void saveFeasibilityForIteration(int iteration, boolean feasible) {
        feasibilityMapSWO.put(iteration, feasible);
    }

    // Methode zum Abrufen der Feasibility für eine Iteration
    public boolean getFeasibilityForIteration(int iteration) {
        return feasibilityMapSWO.getOrDefault(iteration, false);
    }
    
	 // Methode zum Speichern des Zielfunktionswerts für eine bestimmte Iteration
	    public void saveObjectiveValueForIteration(int iteration, double objectiveValue) {
	        iterationObjectiveValuesSWO.put(iteration, objectiveValue);
	    }

	 // Methode zum Abrufen des Zielfunktionswerts für eine bestimmte Iteration
	    public Double getObjectiveValueForIteration(int iteration) {
	        return iterationObjectiveValuesSWO.get(iteration);
	    }
	    
	    public Double getXSWOValueForAgentPeriod(int iteration, int agentIndex, int periodIndex) {
	        if (xSWO.containsKey(iteration)) {
	            double[][] xValues = xSWO.get(iteration);
	            if (agentIndex >= 0 && agentIndex < xValues.length && periodIndex >= 0 && periodIndex < xValues[agentIndex].length) {
	                return xValues[agentIndex][periodIndex]; // Gibt den X-Wert für den Agenten in der spezifischen Periode zurück
	            }
	        }
	        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
	    }
	    
	    public Double getXRTOValueForAgentPeriod(int iteration, int agentIndex, int periodIndex) {
	        if (xRTO.containsKey(iteration)) {
	            double[][] xValues = xRTO.get(iteration);
	            if (agentIndex >= 0 && agentIndex < xValues.length && periodIndex >= 0 && periodIndex < xValues[agentIndex].length) {
	                return xValues[agentIndex][periodIndex]; // Gibt den X-Wert für den Agenten in der spezifischen Periode zurück
	            }
	        }
	        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
	    }
    
	    public void saveSSWOValuesForAgent(int iteration, int agentIndex, double[][] sValues) {
	        // Ensure the iteration exists
	        if (!sSWO.containsKey(iteration)) {
	            throw new IllegalArgumentException("Iteration " + iteration + " does not exist in the data.");
	        }

	        // Retrieve S values for the current iteration
	        double[][][] currentSValues = sSWO.get(iteration);

	        // Validate agent index
	        if (agentIndex < 0 || agentIndex >= currentSValues.length) {
	            throw new IndexOutOfBoundsException("Invalid agent index: " + agentIndex);
	        }

	        // Update the S values for the specific agent and clone each period's array to avoid reference issues
	        for (int periodIndex = 0; periodIndex < sValues.length; periodIndex++) {
	            currentSValues[agentIndex][periodIndex] = sValues[periodIndex].clone();
	        }

	        // Set the updated S values back in the map
	        sSWO.put(iteration, currentSValues);
	    }

    
    // New method to save Y values for a specific agent in a specific iteration
    public void saveYSWOValuesForAgent(int iteration, int agentIndex, boolean[][] yValues) {
        // Check if the Y map already contains the key for the specified iteration
        if (!ySWO.containsKey(iteration)) {
            // Initialize the array for the current iteration if it does not exist
            int numAgents = xSWO.get(0).length;
            int numPeriods = xSWO.get(0)[0].length;
            ySWO.put(iteration, new boolean[numAgents][numPeriods][State.values().length]);
        }

        // Get the Y values for the current iteration
        boolean[][][] iterationYValues = ySWO.get(iteration);

        // Update the Y values for the specific agent
        iterationYValues[agentIndex] = yValues;

        // Save the updated Y values back into the map
        ySWO.put(iteration, iterationYValues);
    }
    
    public void saveXSWOValuesForAgent(int iteration, int agentIndex, double[] xValues) {
        if (xSWO.containsKey(iteration)) {
            double[][] currentX = xSWO.get(iteration);
            if (agentIndex >= 0 && agentIndex < currentX.length) {
                currentX[agentIndex] = xValues;
            }
        }
    }
    
    public void saveXRTOValuesForAgent(int iteration, int agentIndex, double[] xValues) {
        if (xRTO.containsKey(iteration)) {
            double[][] currentX = xRTO.get(iteration);
            if (agentIndex >= 0 && agentIndex < currentX.length) {
                currentX[agentIndex] = xValues;
            }
        }
    }
    
 // Methode zum Speichern eines X-Wertes für eine bestimmte Iteration, Agenten und Periode
    public void saveXSWOValueForPeriod(int iteration, int agentIndex, int periodIndex, double xValue) {
        // Initialisiere die Iteration, falls sie noch nicht existiert
        if (!xSWO.containsKey(iteration)) {
            int numAgents = xSWO.get(0).length; // Anzahl der Agenten
            int numPeriods = xSWO.get(0)[0].length; // Anzahl der Perioden
            xSWO.put(iteration, new double[numAgents][numPeriods]);
        }

        xSWO.get(iteration)[agentIndex][periodIndex] = xValue;
    }
    
    // Methode zum Speichern eines X-Wertes für eine bestimmte Iteration, Agenten und Periode
    public void saveXRTOValueForPeriod(int iteration, int agentIndex, int periodIndex, double xValue) {
        // Initialisiere die Iteration, falls sie noch nicht existiert
        if (!xRTO.containsKey(iteration)) {
            int numAgents = xRTO.get(0).length; // Anzahl der Agenten
            int numPeriods = xRTO.get(0)[0].length; // Anzahl der Perioden
            xRTO.put(iteration, new double[numAgents][numPeriods]);
        }

        xRTO.get(iteration)[agentIndex][periodIndex] = xValue;
    }
    
 // Method to save an S value for a specific iteration, agent, period, and index
    public void saveSSWOValueForPeriod(int iteration, int agentIndex, int periodIndex, int sIndex, double sValue) {
        // Initialize the iteration if it doesn't exist yet
        if (!sSWO.containsKey(iteration)) {
            int numAgents = sSWO.get(0).length; // Number of agents
            int numPeriods = sSWO.get(0)[0].length; // Number of periods
            sSWO.put(iteration, new double[numAgents][numPeriods][2]); // Assuming there are 2 S values (S1, S2) per period
        }

        // Save the S value for the given agent, period, and S index
        sSWO.get(iteration)[agentIndex][periodIndex][sIndex] = sValue;
    }
    
    public void saveSRTOValueForPeriod(int iteration, int agentIndex, int periodIndex, int sIndex, double sValue) {
        // Initialize the iteration if it doesn't exist yet
        if (!sRTO.containsKey(iteration)) {
            int numAgents = sRTO.get(0).length; // Number of agents
            int numPeriods = sRTO.get(0)[0].length; // Number of periods
            sRTO.put(iteration, new double[numAgents][numPeriods][2]); // Assuming there are 2 S values (S1, S2) per period
        }

        // Save the S value for the given agent, period, and S index
        sRTO.get(iteration)[agentIndex][periodIndex][sIndex] = sValue;
    }

    
 // Methode zum Speichern der HydrogenProduction für eine bestimmte Iteration, einen Agenten und eine Periode
    public void saveHydrogenSWOProductionForPeriod(int iteration, int agentIndex, int periodIndex, double hydrogenProductionValue) {
        if (!hydrogenProductionSWO.containsKey(iteration)) {
            // Initialisiere die Iteration, wenn sie noch nicht existiert
            int numAgents = hydrogenProductionSWO.get(0).length; // Anzahl der Agenten
            int numPeriods = hydrogenProductionSWO.get(0)[0].length; // Anzahl der Perioden
            hydrogenProductionSWO.put(iteration, new double[numAgents][numPeriods]);
        }

        // Speichere den HydrogenProduction-Wert für den Agenten in der entsprechenden Periode
        hydrogenProductionSWO.get(iteration)[agentIndex][periodIndex] = hydrogenProductionValue;
    }
    
 // Methode zum Speichern der X-Werte für eine bestimmte Iteration und einen Agenten
    public void saveXSWOValuesForIteration(int iteration, int agentIndex, double[] xValuesForAgent) {
        if (!xSWO.containsKey(iteration)) {
            // Initialisiere die Iteration, wenn sie noch nicht existiert
            int numAgents = xSWO.get(0).length; // Anzahl der Agenten
            int numPeriods = xValuesForAgent.length; // Anzahl der optimierten Perioden
            xSWO.put(iteration, new double[numAgents][numPeriods]);
        }

        // Speichere die X-Werte für den Agenten in der entsprechenden Iteration
        double[][] iterationXValues = xSWO.get(iteration);
        System.arraycopy(xValuesForAgent, 0, iterationXValues[agentIndex], 0, xValuesForAgent.length);
    }

    // Methode zum Speichern der Wasserstoffproduktionsmenge für einen bestimmten Agenten und eine bestimmte Iteration
    public void saveHydrogenProductionForAgent(int iteration, int agentIndex, double[] productionValues) {
        if (hydrogenProductionSWO.containsKey(iteration)) {
            double[][] currentProduction = hydrogenProductionSWO.get(iteration);
            if (agentIndex >= 0 && agentIndex < currentProduction.length) {
                currentProduction[agentIndex] = productionValues;
            }
        }
    }
    
    public String getAllXValuesForIteration(int iteration, String agentName) {
        // Überprüfen, ob die Iteration in der Map existiert
        if (!xSWO.containsKey(iteration)) {
            return "Die angegebene Iteration " + iteration + " existiert nicht.";
        }

        // X-Werte für die angegebene Iteration abrufen
        double[][] xValuesForIteration = xSWO.get(iteration);

        // StringBuilder zum Sammeln der Ausgabe
        StringBuilder result = new StringBuilder();
        result.append("X-Werte für Iteration ").append(iteration).append(" (Agent: ").append(agentName).append("):\n");

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
        hydrogenProductionSWO.put(iteration, productionValues);
    }

    // Methode zum Abrufen der Wasserstoffproduktionsmenge für eine bestimmte Iteration
    public double[][] getHydrogenProductionForIteration(int iteration) {
        return hydrogenProductionSWO.get(iteration);
    }
    
    // Methode zum Abrufen der Wasserstoffproduktionsmenge für einen bestimmten Agenten und eine bestimmte Iteration
    public double[] getHydrogenSWOProductionForAgent(int iteration, int agentIndex) {
        if (hydrogenProductionSWO.containsKey(iteration)) {
            double[][] productionValues = hydrogenProductionSWO.get(iteration);
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
        boolean[][][] yValues = getYSWOValuesForIteration(firstIteration);

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
        headerRow.createCell(headerColumn++).setCellValue("Objective Value");
        headerRow.createCell(headerColumn++).setCellValue("Penalty Value");  // Neue Spalte für penaltyValues

        // Adding headers for each agent and period combination
        for (int agentIndex = 0; agentIndex < 10; agentIndex++) {
            for (int periodIndex = 0; periodIndex < xSWO.get(0)[agentIndex].length; periodIndex++) {
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
        for (Integer iteration : xSWO.keySet()) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;

            // Iterationsnummer in die erste Spalte schreiben
            row.createCell(colNum++).setCellValue(iteration);

            // Objective-Wert in die zweite Spalte schreiben
            Double objectiveValue = iterationObjectiveValuesSWO.get(iteration);
            if (objectiveValue != null) {
                row.createCell(colNum++).setCellValue(objectiveValue);
            } else {
                row.createCell(colNum++).setCellValue("N/A");  // Falls kein Wert vorhanden ist
            }

            // Penalty-Wert in die dritte Spalte schreiben
            Double penaltyValue = penaltyXvalues.get(iteration); 
            if (penaltyValue != null) {
                row.createCell(colNum++).setCellValue(penaltyValue);
            } else {
                row.createCell(colNum++).setCellValue("N/A"); 
            }

            // Iteriere über die Agenten und Perioden, um die X, Y, S, U und HydrogenProduction zu schreiben
            for (int agentIndex = 0; agentIndex < 10; agentIndex++) {
                for (int periodIndex = 0; periodIndex < xSWO.get(iteration)[agentIndex].length; periodIndex++) {
                    // X-Wert
                    Cell cellX = row.createCell(colNum++);
                    cellX.setCellValue(xSWO.get(iteration)[agentIndex][periodIndex]);
                    cellX.setCellStyle(decimalStyle);

                    // Aktiver Y-Zustand
                    String activeState = "None";
                    for (State state : State.values()) {
                        if (ySWO.get(iteration)[agentIndex][periodIndex][state.ordinal()]) {
                            activeState = state.name();
                            break;
                        }
                    }
                    Cell cellY = row.createCell(colNum++);
                    cellY.setCellValue(activeState);

                    // S-Werte
                    Cell cellS1 = row.createCell(colNum++);
                    cellS1.setCellValue(sSWO.get(iteration)[agentIndex][periodIndex][0]);
                    cellS1.setCellStyle(decimalStyle);

                    Cell cellS2 = row.createCell(colNum++);
                    cellS2.setCellValue(sSWO.get(iteration)[agentIndex][periodIndex][1]);
                    cellS2.setCellStyle(decimalStyle);

                    // U-Werte
                    for (int i = 0; i < 3; i++) {
                        Cell cellU = row.createCell(colNum++);
                        cellU.setCellValue(uSWO.get(iteration)[agentIndex][periodIndex][i]);
                        cellU.setCellStyle(decimalStyle);
                    }

                    // Wasserstoffproduktionswert
                    Cell cellHydrogen = row.createCell(colNum++);
                    cellHydrogen.setCellValue(hydrogenProductionSWO.get(iteration)[agentIndex][periodIndex]);
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
    
    public void writeValuesToExcel_Short(String filePath) {
        XSSFWorkbook workbook = new XSSFWorkbook();  // Neues Workbook erstellen
        XSSFSheet sheet = workbook.createSheet("Iteration Data Short");  // Neues Sheet erstellen

        // Erstelle die Headerzeile
        Row headerRow = sheet.createRow(0);
        int headerColumn = 0;
        headerRow.createCell(headerColumn++).setCellValue("Iteration");

        // Adding headers for the first 30 agents and all periods
        for (int agentIndex = 0; agentIndex < 30; agentIndex++) {  // Nur die ersten 30 Elektrolyseure
            for (int periodIndex = 0; periodIndex < xSWO.get(0)[agentIndex].length; periodIndex++) {
                String baseHeader = "A" + agentIndex + "_P" + (periodIndex + 1) + "_";  // Periodenindex um 1 erhöhen
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "X");
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "Y");
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "S1");
                headerRow.createCell(headerColumn++).setCellValue(baseHeader + "S2");
            }
        }

        // Erstelle ein Dezimalformat für 5 Dezimalstellen
        DataFormat format = workbook.createDataFormat();
        CellStyle decimalStyle = workbook.createCellStyle();
        decimalStyle.setDataFormat(format.getFormat("0.00000"));

        // Fülle die Zeilen mit den Werten
        int rowNum = 1;
        for (Integer iteration : xSWO.keySet()) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;
            
            // Iterationsnummer in die erste Spalte schreiben
            row.createCell(colNum++).setCellValue(iteration);

            // Iteriere über die ersten 30 Agenten und Perioden, um die X, Y, und S Werte zu schreiben
            for (int agentIndex = 0; agentIndex < 30; agentIndex++) {  // Nur die ersten 30 Elektrolyseure
                for (int periodIndex = 0; periodIndex < xSWO.get(iteration)[agentIndex].length; periodIndex++) {
                    // X-Wert
                    Cell cellX = row.createCell(colNum++);
                    cellX.setCellValue(xSWO.get(iteration)[agentIndex][periodIndex]);
                    cellX.setCellStyle(decimalStyle);

                    // Aktiver Y-Zustand
                    String activeState = "None";
                    for (State state : State.values()) {
                        if (ySWO.get(iteration)[agentIndex][periodIndex][state.ordinal()]) {
                            activeState = state.name();
                            break;
                        }
                    }
                    Cell cellY = row.createCell(colNum++);
                    cellY.setCellValue(activeState);

                    // S-Werte
                    Cell cellS1 = row.createCell(colNum++);
                    cellS1.setCellValue(sSWO.get(iteration)[agentIndex][periodIndex][0]);
                    cellS1.setCellStyle(decimalStyle);

                    Cell cellS2 = row.createCell(colNum++);
                    cellS2.setCellValue(sSWO.get(iteration)[agentIndex][periodIndex][1]);
                    cellS2.setCellStyle(decimalStyle);
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
    
 // Method to print S1 and S2 values for each agent across all iterations
    public void printSValuesForAllIterations() {
        for (Integer iteration : sSWO.keySet()) {
            System.out.println("Iteration: " + iteration);
            double[][][] sValuesForIteration = sSWO.get(iteration);
            
            for (int agentIndex = 0; agentIndex < sValuesForIteration.length; agentIndex++) {
                System.out.println("  Agent " + (agentIndex + 1) + ":");
                
                for (int periodIndex = 0; periodIndex < sValuesForIteration[agentIndex].length; periodIndex++) {
                    double s1Value = sValuesForIteration[agentIndex][periodIndex][0];
                    double s2Value = sValuesForIteration[agentIndex][periodIndex][1];
                    
                    System.out.println("    Period " + (periodIndex + 1) + " - S1: " + s1Value + ", S2: " + s2Value);
                }
            }
        }
    }
    
    public double[][] getXSWOValuesForIteration(int iteration) {
        return xSWO.get(iteration);
    }
    
    public double[][] getXRTOValuesForIteration(int iteration) {
        return xRTO.get(iteration);
    }

    public boolean[][][] getYSWOValuesForIteration(int iteration) {
        if (!ySWO.containsKey(iteration)) {
            ySWO.put(iteration, new boolean[0][][]); // Initialisiert mit leeren Werten, falls nötig
        }
        return ySWO.get(iteration);
    }
    

    public double[][][] getSSWOValuesForIteration(int iteration) {
        return sSWO.get(iteration);
    }
    
    public double[][][] getSRTOValuesForIteration(int iteration) {
        return sRTO.get(iteration);
    }

    public double[][][] getUSWOValuesForIteration(int iteration) {
        return uSWO.get(iteration);
    }
    
    public double[][][] getURTOValuesForIteration(int iteration) {
        return uRTO.get(iteration);
    }
    
 // Methode zum Speichern der X-Werte für eine bestimmte Iteration
    public void saveXValuesForIteration(int iteration, double[][] xValues) {
        xSWO.put(iteration, xValues);
    }
    
 // Methode zum Speichern der Y-Werte für eine bestimmte Iteration
    public void saveYValuesForIteration(int iteration, boolean[][][] yValues) {
        ySWO.put(iteration, yValues);
    }
    
 // Methode zum Speichern der S-Werte für eine bestimmte Iteration
    public void saveSValuesForIteration(int iteration, double[][][] sValues) {
        sSWO.put(iteration, sValues);
    }

 // Methode zum Speichern der U-Werte für eine bestimmte Iteration
    public void saveUValuesForIteration(int iteration, double[][][] uValues) {
        uSWO.put(iteration, uValues);
    }
    
    public double[] getXSWOValuesForAgent(int iteration, int agentIndex) {
        if (xSWO.containsKey(iteration)) {
            double[][] xValues = xSWO.get(iteration);
            if (agentIndex >= 0 && agentIndex < xValues.length) {
                return xValues[agentIndex]; // Gibt alle X-Werte für den Agenten in allen Perioden zurück
            }
        }
        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
    }
    
    public double[] getXRTOValuesForAgent(int iteration, int agentIndex) {
        if (xRTO.containsKey(iteration)) {
            double[][] xValues = xRTO.get(iteration);
            if (agentIndex >= 0 && agentIndex < xValues.length) {
                return xValues[agentIndex]; // Gibt alle X-Werte für den Agenten in allen Perioden zurück
            }
        }
        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
    }

    public boolean[][] getYSWOValuesForAgent(int iteration, int agentIndex) {
        if (!ySWO.containsKey(iteration)) {
            // Wenn die Iteration noch nicht existiert, initialisiere die Y-Werte für diese Iteration
            int numAgents = xSWO.get(0).length; // Anzahl der Agenten
            int numPeriods = xSWO.get(0)[0].length; // Anzahl der Perioden
            ySWO.put(iteration, new boolean[numAgents][numPeriods][State.values().length]);
        }

        boolean[][][] yValues = ySWO.get(iteration);
        
        // Wenn der agentIndex gültig ist, gib die Y-Werte für den Agenten zurück
        if (agentIndex >= 0 && agentIndex < yValues.length) {
            return yValues[agentIndex];
        }
        
        return null; // Oder eine Exception werfen, wenn der Agentenindex ungültig ist
    }


    public double[][] getSSWOValuesForAgent(int iteration, int agentIndex) {
        if (sSWO.containsKey(iteration)) {
            double[][][] sValues = sSWO.get(iteration);
            if (agentIndex >= 0 && agentIndex < sValues.length) {
                return sValues[agentIndex];
            }
        }
        return null; 
    }
    
    
    public double[][] getSRTOValuesForAgent(int iteration, int agentIndex) {
        if (sRTO.containsKey(iteration)) {
            double[][][] sValues = sRTO.get(iteration);
            if (agentIndex >= 0 && agentIndex < sValues.length) {
                return sValues[agentIndex];
            }
        }
        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
    }
    
 // Methode zum Abrufen der U-Werte eines bestimmten Agenten für eine bestimmte Iteration
    public double[][] getUSWOValuesForAgent(int iteration, int agentIndex) {
        if (uSWO.containsKey(iteration)) {
            double[][][] uValues = uSWO.get(iteration);
            if (agentIndex >= 0 && agentIndex < uValues.length) {
                return uValues[agentIndex];
            }
        }
        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
    }
    
    // Methode zum Abrufen der U-Werte eines bestimmten Agenten für eine bestimmte Iteration
    public double[][] getURTOValuesForAgent(int iteration, int agentIndex) {
        if (uRTO.containsKey(iteration)) {
            double[][][] uValues = uRTO.get(iteration);
            if (agentIndex >= 0 && agentIndex < uValues.length) {
                return uValues[agentIndex];
            }
        }
        return null; // Oder eine Exception werfen, wenn die Werte nicht existieren
    }
    
    public void saveUSWOValuesForAgent(int iteration, int agentIndex, double[][] uValues) {
    	if (uSWO.containsKey(iteration)) {
            double[][][] currentU = uSWO.get(iteration);
            if (agentIndex >= 0 && agentIndex < currentU.length) {
                currentU[agentIndex] = uValues;
            }
        }
    }
    
    public void saveURTOValuesForAgent(int iteration, int agentIndex, double[][] uValues) {
    	if (uRTO.containsKey(iteration)) {
            double[][][] currentU = uRTO.get(iteration);
            if (agentIndex >= 0 && agentIndex < currentU.length) {
                currentU[agentIndex] = uValues;
            }
        }
    }
    
    
    public void saveUSWOValueForAgentPeriod(int iteration, int agentIndex, int periodIndex, int residualIndex, double uValue) {
        // Stelle sicher, dass die Iteration existiert
        if (!uSWO.containsKey(iteration)) {
            throw new IllegalArgumentException("Iteration " + iteration + " does not exist in the data.");
        }

        // Hole die U-Werte für die aktuelle Iteration
        double[][][] currentUValues = uSWO.get(iteration);

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
        uSWO.put(iteration, currentUValues);
    }
    
    public void saveURTOValueForAgentPeriod(int iteration, int agentIndex, int periodIndex, int residualIndex, double uValue) {
    	
        if (!uRTO.containsKey(iteration)) {
            throw new IllegalArgumentException("Iteration " + iteration + " does not exist in the data.");
        }
        // Hole die U-Werte für die aktuelle Iteration
        double[][][] currentUValues = uRTO.get(iteration);

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
        uRTO.put(iteration, currentUValues);
    }
    
    public void saveYSWOValuesForAgentPeriod(int iteration, int agentIndex, int periodIndex, boolean[] yValues) {
        // Sicherstellen, dass die Iteration in der Y-Struktur existiert
        if (!ySWO.containsKey(iteration)) {
            int numAgents = parameters.getTotalElectrolyzers(); // Annahme: Gesamtanzahl der Agenten aus den Parametern
            int numPeriods = parameters.getPeriods().size(); // Annahme: Anzahl der Perioden aus den Parametern
            ySWO.put(iteration, new boolean[numAgents][numPeriods][State.values().length]);
        }

        // Sicherstellen, dass der Agentenindex und der Periodenindex im gültigen Bereich liegen
        if (agentIndex < 0 || agentIndex >= ySWO.get(iteration).length) {
            throw new IndexOutOfBoundsException("Ungültiger Agentenindex: " + agentIndex);
        }
        if (periodIndex < 0 || periodIndex >= ySWO.get(iteration)[agentIndex].length) {
            throw new IndexOutOfBoundsException("Ungültiger Periodenindex: " + periodIndex);
        }

        // Speichern der Y-Werte für den angegebenen Agenten und die Periode in der aktuellen Iteration
        ySWO.get(iteration)[agentIndex][periodIndex] = yValues;
    }


    // Getter-Methoden für die Parameter (optional)
    public Map<Electrolyzer, Double> getPowerElectrolyzer() {
        return powerElectrolyzer;
    }

    public Map<Electrolyzer, Double> getMinOperation() {
        return minOperation;
    }

    public Map<Electrolyzer, Double> getMaxOperation() {
        return maxOperation;
    }

    public Map<Electrolyzer, Double> getSlope() {
        return slope;
    }

    public Map<Electrolyzer, Double> getIntercept() {
        return intercept;
    }

    public Map<Electrolyzer, Double> getStartupCost() {
        return startupCost;
    }
    public Map<Electrolyzer, Double> getStandbyCost() {
        return standbyCost;
    }

    public Map<Electrolyzer, Integer> getStartupDuration() {
        return startupDuration;
    }

    public Map<Electrolyzer, Map<State, Integer>> getHoldingDurations() {
        return holdingDurations;
    }

    public Map<Period, Double> getElectricityPrice() {
        return electricityPrice;
    }

    public Map<Period, Double> getPeriodDemand() {
        return periodDemand;
    }

    public Map<Period, Double> getAvailablePower() {
        return availablePower;
    }

    public double getIntervalLength() {
        return intervalLengthSWO;
    }

    public double getDemandDeviationCost() {
        return demandDeviationCost;
    }
    
    // Methode zum Setzen der Parameter
    public void setParameters(Parameters params) {
        this.parameters = params;
    }

    // Getter-Methoden
    public Set<Electrolyzer> getElectrolyzers() {
        return parameters.getElectrolyzers();
    }

    public Set<Period> getPeriods() {
        return parameters.getPeriods();
    }
    
 // Getter-Methode für die Parameters
    public Parameters getParameters() {
        return this.parameters;
    }
    
    public void exportFinalIterationResultsToExcel(int finalIteration, Set<Electrolyzer> electrolyzers, Set<Period> periods, Parameters params, String filePath) throws IOException {
        Workbook workbook = new XSSFWorkbook();

        // Sheet 1: Final Iteration Results
        Sheet resultSheet = workbook.createSheet("Final Iteration Results");
        Row headerRow = resultSheet.createRow(0);
        headerRow.createCell(0).setCellValue("Period");
        headerRow.createCell(1).setCellValue("Electrolyzer");
        headerRow.createCell(2).setCellValue("X Value");
        headerRow.createCell(3).setCellValue("Y State");
        headerRow.createCell(4).setCellValue("Hydrogen Production");
        headerRow.createCell(5).setCellValue("Period Demand");
        headerRow.createCell(6).setCellValue("Total Electrolyzer Power");
        headerRow.createCell(7).setCellValue("Renewable Energy");
        headerRow.createCell(8).setCellValue("Purchased Grid Power");
        
        int rowIndex = 1;
        double[][] xValues = this.getXSWOValuesForIteration(finalIteration);
        boolean[][][] yValues = this.getYSWOValuesForIteration(finalIteration);
        double[][] hydrogenProductionValues = this.getHydrogenProductionForIteration(finalIteration);

        // Fill result sheet with data for the final iteration
        for (Period period : periods) {
            int periodIndex = period.getT() - 1;
            double periodDemand = params.getPeriodDemand(period);
            double renewableEnergy = params.getRenewableEnergy(period);
            double totalElectrolyzerEnergy = params.getTotalElectrolyzerEnergy(period);
            double purchasedGridEnergy = params.getPurchasedEnergy(period);

            for (Electrolyzer electrolyzer : electrolyzers) {
                int electrolyzerIndex = electrolyzer.getId() - 1;
                Row row = resultSheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(period.getT());
                row.createCell(1).setCellValue(electrolyzer.getId());
                row.createCell(2).setCellValue(xValues[electrolyzerIndex][periodIndex]);

                String activeState = "None";
                for (State state : State.values()) {
                    if (yValues[electrolyzerIndex][periodIndex][state.ordinal()]) {
                        activeState = state.name();
                        break;
                    }
                }
                row.createCell(3).setCellValue(activeState);
                row.createCell(4).setCellValue(hydrogenProductionValues[electrolyzerIndex][periodIndex]);
                row.createCell(5).setCellValue(periodDemand);
                row.createCell(6).setCellValue(totalElectrolyzerEnergy);
                row.createCell(7).setCellValue(renewableEnergy);
                row.createCell(8).setCellValue(purchasedGridEnergy);
            }
        }

        // Objective value
        Row computationRow = resultSheet.createRow(rowIndex++);
        computationRow.createCell(0).setCellValue("Total Computation Time (ns)");
        computationRow.createCell(1).setCellValue(computationTime);

        Row objectiveRow = resultSheet.createRow(rowIndex++);
        objectiveRow.createCell(0).setCellValue("Objective Value");
        objectiveRow.createCell(1).setCellValue(iterationObjectiveValuesSWO.getOrDefault(finalIteration, 0.0));

        // Sheet 2: Iteration Data
        Sheet iterationSheet = workbook.createSheet("Iteration Data");
        Row iterHeaderRow = iterationSheet.createRow(0);
        iterHeaderRow.createCell(0).setCellValue("Iteration");
        iterHeaderRow.createCell(1).setCellValue("Objective Value");
        iterHeaderRow.createCell(2).setCellValue("X Objective");
        iterHeaderRow.createCell(3).setCellValue("Y Objective");
        iterHeaderRow.createCell(4).setCellValue("Sent Messages");
        iterHeaderRow.createCell(5).setCellValue("Received Dual Messages");
        iterHeaderRow.createCell(6).setCellValue("X Update Time (ns)");
        iterHeaderRow.createCell(7).setCellValue("Y Update Time (ns)");
        iterHeaderRow.createCell(8).setCellValue("Dual Update Time (ns)");
        iterHeaderRow.createCell(9).setCellValue("S Update Time (ns)");
        iterHeaderRow.createCell(10).setCellValue("Update Duration in Iteration (ns)");
        iterHeaderRow.createCell(11).setCellValue("Primal Residual");
        iterHeaderRow.createCell(12).setCellValue("Dual Residual");

        // Add per-iteration data
        int iterRowIndex = 1;
        for (int i = 0; i <= finalIteration; i++) {
            Row iterRow = iterationSheet.createRow(iterRowIndex++);
            iterRow.createCell(0).setCellValue(i);
            iterRow.createCell(1).setCellValue(iterationObjectiveValuesSWO.getOrDefault(i, 0.0));
            iterRow.createCell(2).setCellValue(getXObjectiveForIteration(i));
            iterRow.createCell(3).setCellValue(getYObjectiveForIteration(i));
            iterRow.createCell(4).setCellValue(getSentMessagesForIteration(i));
            iterRow.createCell(5).setCellValue(getReceivedDualMessagePerIteration(i));
            iterRow.createCell(6).setCellValue(getXUpdateTimeForIteration(i));
            iterRow.createCell(7).setCellValue(getYUpdateTimeForIteration(i));
            iterRow.createCell(8).setCellValue(getDualUpdateTimeForIteration(i));
            iterRow.createCell(9).setCellValue(getSUpdateTimeForIteration(i));
            iterRow.createCell(10).setCellValue(getXUpdateTimeForIteration(i) + getYUpdateTimeForIteration(i) + getDualUpdateTimeForIteration(i) + getSUpdateTimeForIteration(i));
            iterRow.createCell(11).setCellValue(getPrimalResidualForIteration(i)); // Primal Residual
            iterRow.createCell(12).setCellValue(getDualResidualForIteration(i));  // Dual Residual
        }

        // Save to file
        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        } finally {
            workbook.close();
        }

        System.out.println("Results successfully written to Excel file: " + filePath);
    }
    
    public void exportSlackVariablesToExcel(String filePath) {
        Workbook workbook = new XSSFWorkbook(); // Neues Excel-Workbook erstellen

        // Alle Iterationen sortieren
        List<Integer> iterations = new ArrayList<>(sSWO.keySet());
        Collections.sort(iterations);

        // Agenten und Perioden ermitteln
        int numAgents = sSWO.get(iterations.get(0)).length; // Anzahl der Agenten basierend auf der ersten Iteration
        int numPeriods = sSWO.get(iterations.get(0))[0].length; // Anzahl der Perioden basierend auf dem ersten Agenten
        int columnsPerAgent = numPeriods * 2; // S1 und S2 pro Periode
        int maxColumnsPerSheet = 16384; // Maximale Spaltenanzahl pro Excel-Arbeitsblatt
        int maxAgentsPerSheet = maxColumnsPerSheet / columnsPerAgent; // Maximale Agentenanzahl pro Blatt

        int sheetCounter = 1;
        for (int agentStartIndex = 0; agentStartIndex < numAgents; agentStartIndex += maxAgentsPerSheet) {
            // Agenten für das aktuelle Blatt bestimmen
            int agentEndIndex = Math.min(agentStartIndex + maxAgentsPerSheet, numAgents);
            Sheet sheet = workbook.createSheet("Slack Variables " + sheetCounter++);

            // Erstelle die Headerzeile
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Iteration");
            int columnIndex = 1;

            // Spaltenheader setzen
            for (int agentIndex = agentStartIndex; agentIndex < agentEndIndex; agentIndex++) {
                for (int periodIndex = 0; periodIndex < numPeriods; periodIndex++) {
                    headerRow.createCell(columnIndex++).setCellValue("S1_Agent" + (agentIndex + 1) + "_Period" + (periodIndex + 1));
                    headerRow.createCell(columnIndex++).setCellValue("S2_Agent" + (agentIndex + 1) + "_Period" + (periodIndex + 1));
                }
            }

            // Fülle die Zeilen mit den Slack-Werten
            int rowIndex = 1;
            for (Integer iteration : iterations) {
                Row row = sheet.createRow(rowIndex++);
                int colIndex = 0;

                // Iteration in die erste Spalte schreiben
                row.createCell(colIndex++).setCellValue(iteration);

                // Werte für die Agenten im aktuellen Blatt abrufen und einfügen
                for (int agentIndex = agentStartIndex; agentIndex < agentEndIndex; agentIndex++) {
                    double[][] sValues = getSSWOValuesForAgent(iteration, agentIndex);

                    for (int periodIndex = 0; periodIndex < numPeriods; periodIndex++) {
                        // S1 und S2-Werte der aktuellen Periode in die Tabelle schreiben
                        row.createCell(colIndex++).setCellValue(sValues[periodIndex][0]);
                        row.createCell(colIndex++).setCellValue(sValues[periodIndex][1]);
                    }
                }
            }
        }

        // Excel-Datei speichern
        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
            System.out.println("Slack variables successfully exported to Excel file: " + filePath);
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
    
    public void writeValuesToExcel_Distributed(String filePath) {
        XSSFWorkbook workbook = new XSSFWorkbook();  // Neues Workbook erstellen

        int maxColumnsPerSheet = 16384;  // Excel-Beschränkung
        int maxAgentsPerSheet = maxColumnsPerSheet / xSWO.get(0)[0].length / 11;  // 11 Spalten pro Agent und Periode (inkl. Residuals)

        int totalAgents = xSWO.get(0).length;
        int sheetCounter = 1;

        // Verteile Elektrolyseure auf mehrere Blätter
        for (int agentStartIndex = 0; agentStartIndex < totalAgents; agentStartIndex += maxAgentsPerSheet) {
            int agentEndIndex = Math.min(agentStartIndex + maxAgentsPerSheet, totalAgents);
            XSSFSheet sheet = workbook.createSheet("Iteration Data " + sheetCounter++);

            // Erstelle die Headerzeile
            Row headerRow = sheet.createRow(0);
            int headerColumn = 0;
            headerRow.createCell(headerColumn++).setCellValue("Iteration");

            for (int agentIndex = agentStartIndex; agentIndex < agentEndIndex; agentIndex++) {
                for (int periodIndex = 0; periodIndex < xSWO.get(0)[agentIndex].length; periodIndex++) {
                    String baseHeader = "A" + agentIndex + "_P" + (periodIndex + 1) + "_";  // Periodenindex um 1 erhöhen
                    headerRow.createCell(headerColumn++).setCellValue(baseHeader + "X");
                    headerRow.createCell(headerColumn++).setCellValue(baseHeader + "Y");
                    headerRow.createCell(headerColumn++).setCellValue(baseHeader + "S1");
                    headerRow.createCell(headerColumn++).setCellValue(baseHeader + "S2");
                    headerRow.createCell(headerColumn++).setCellValue(baseHeader + "U1");
                    headerRow.createCell(headerColumn++).setCellValue(baseHeader + "U2");
                    headerRow.createCell(headerColumn++).setCellValue(baseHeader + "HydrogenProduction");
                    headerRow.createCell(headerColumn++).setCellValue(baseHeader + "Residual1");
                    headerRow.createCell(headerColumn++).setCellValue(baseHeader + "Residual2");
                    headerRow.createCell(headerColumn++).setCellValue(baseHeader + "yResidual");
                }
            }

            // Erstelle ein Dezimalformat für 5 Dezimalstellen
            DataFormat format = workbook.createDataFormat();
            CellStyle decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(format.getFormat("0.00000"));

            // Fülle die Zeilen mit den Werten
            int rowNum = 1;
            for (Integer iteration : xSWO.keySet()) {
                Row row = sheet.createRow(rowNum++);
                int colNum = 0;

                // Iterationsnummer in die erste Spalte schreiben
                row.createCell(colNum++).setCellValue(iteration);

                // Iteriere über die aktuellen Agenten und Perioden, um die X, Y, S, U, HydrogenProduction und Residuals zu schreiben
                for (int agentIndex = agentStartIndex; agentIndex < agentEndIndex; agentIndex++) {
                    for (int periodIndex = 0; periodIndex < xSWO.get(iteration)[agentIndex].length; periodIndex++) {
                        // X-Wert
                        Cell cellX = row.createCell(colNum++);
                        cellX.setCellValue(xSWO.get(iteration)[agentIndex][periodIndex]);
                        cellX.setCellStyle(decimalStyle);

                        // Aktiver Y-Zustand
                        String activeState = "None";
                        for (State state : State.values()) {
                            if (ySWO.get(iteration)[agentIndex][periodIndex][state.ordinal()]) {
                                activeState = state.name();
                                break;
                            }
                        }
                        Cell cellY = row.createCell(colNum++);
                        cellY.setCellValue(activeState);

                        // S-Werte
                        Cell cellS1 = row.createCell(colNum++);
                        cellS1.setCellValue(sSWO.get(iteration)[agentIndex][periodIndex][0]);
                        cellS1.setCellStyle(decimalStyle);

                        Cell cellS2 = row.createCell(colNum++);
                        cellS2.setCellValue(sSWO.get(iteration)[agentIndex][periodIndex][1]);
                        cellS2.setCellStyle(decimalStyle);

                        // U-Werte
                        for (int i = 0; i < 2; i++) {
                            Cell cellU = row.createCell(colNum++);
                            cellU.setCellValue(uSWO.get(iteration)[agentIndex][periodIndex][i]);
                            cellU.setCellStyle(decimalStyle);
                        }

                        // Wasserstoffproduktionswert
                        Cell cellHydrogen = row.createCell(colNum++);
                        cellHydrogen.setCellValue(hydrogenProductionSWO.get(iteration)[agentIndex][periodIndex]);
                        cellHydrogen.setCellStyle(decimalStyle);

                        // Residuals (Residual1, Residual2, yResidual)
                        double[] residuals = getYSWOResiduals(iteration, agentIndex, periodIndex);
                        if (residuals != null) {
                            for (double residual : residuals) {
                                Cell cellResidual = row.createCell(colNum++);
                                cellResidual.setCellValue(residual);
                                cellResidual.setCellStyle(decimalStyle);
                            }
                        } else {
                            // Falls keine Residuals vorhanden sind, leere Zellen einfügen
                            for (int i = 0; i < 3; i++) {
                                row.createCell(colNum++).setCellValue("");
                            }
                        }
                    }
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
    
	public void writeValuesToExcel_Distributed_RTO(String filePath) {
		XSSFWorkbook workbook = new XSSFWorkbook(); // Neues Workbook erstellen

		int maxColumnsPerSheet = 16384; // Excel-Beschränkung
		int maxAgentsPerSheet = maxColumnsPerSheet / xRTO.get(0)[0].length / 7; // 11 Spalten pro Agent und Periode
																					// (inkl. Residuals)
		int totalAgents = xRTO.get(0).length;
		int sheetCounter = 1;

		// Verteile Elektrolyseure auf mehrere Blätter
		for (int agentStartIndex = 0; agentStartIndex < totalAgents; agentStartIndex += maxAgentsPerSheet) {
			int agentEndIndex = Math.min(agentStartIndex + maxAgentsPerSheet, totalAgents);
			XSSFSheet sheet = workbook.createSheet("Iteration Data " + sheetCounter++);

			// Erstelle die Headerzeile
			Row headerRow = sheet.createRow(0);
			int headerColumn = 0;
			headerRow.createCell(headerColumn++).setCellValue("Iteration");

			for (int agentIndex = agentStartIndex; agentIndex < agentEndIndex; agentIndex++) {
				for (int periodIndex = 0; periodIndex < rtoStepsPerSWO; periodIndex++) { // 10 Perioden
					String baseHeader = "A" + (agentIndex+1) + "_P" + (periodIndex + 1) + "_"; // Periodenindex um 1 erhöhen
					headerRow.createCell(headerColumn++).setCellValue(baseHeader + "X");
					headerRow.createCell(headerColumn++).setCellValue(baseHeader + "Y");
					headerRow.createCell(headerColumn++).setCellValue(baseHeader + "S1");
					headerRow.createCell(headerColumn++).setCellValue(baseHeader + "S2");
					headerRow.createCell(headerColumn++).setCellValue(baseHeader + "U1");
					headerRow.createCell(headerColumn++).setCellValue(baseHeader + "U2");
				}
			}

			// Erstelle ein Dezimalformat für 5 Dezimalstellen
			DataFormat format = workbook.createDataFormat();
			CellStyle decimalStyle = workbook.createCellStyle();
			decimalStyle.setDataFormat(format.getFormat("0.00000"));

			// Fülle die Zeilen mit den Werten
			int rowNum = 1;
			for (Integer iteration : xRTO.keySet()) {
				Row row = sheet.createRow(rowNum++);
				int colNum = 0;

				// Iterationsnummer in die erste Spalte schreiben
				row.createCell(colNum++).setCellValue(iteration);

				// Iteriere über die aktuellen Agenten und Perioden, um die X, Y, S, U,
				for (int agentIndex = agentStartIndex; agentIndex < agentEndIndex; agentIndex++) {
					for (int periodIndex = 0; periodIndex < rtoStepsPerSWO; periodIndex++) { // 10 Perioden
						// X-Wert
						Cell cellX = row.createCell(colNum++);
						cellX.setCellValue(xRTO.get(iteration)[agentIndex][periodIndex]);
						cellX.setCellStyle(decimalStyle);

						// Aktiver Y-Zustand
						String activeState = "None";
						for (State state : State.values()) {
							if (ySWO.get(1)[agentIndex][6-1][state.ordinal()]) {
								activeState = state.name();
								break;
							}
						}
						Cell cellY = row.createCell(colNum++);
						cellY.setCellValue(activeState);

						// S-Werte
						Cell cellS1 = row.createCell(colNum++);
						cellS1.setCellValue(sRTO.get(iteration)[agentIndex][periodIndex][0]);
						cellS1.setCellStyle(decimalStyle);

						Cell cellS2 = row.createCell(colNum++);
						cellS2.setCellValue(sRTO.get(iteration)[agentIndex][periodIndex][1]);
						cellS2.setCellStyle(decimalStyle);

						// U-Werte
						for (int i = 0; i < 2; i++) {
							Cell cellU = row.createCell(colNum++);
							cellU.setCellValue(uRTO.get(iteration)[agentIndex][periodIndex][i]);
							cellU.setCellStyle(decimalStyle);
						}

					}
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
	
	
    public void writeRTOValuesToExcel(ADMMDataModel dataModel, int rtoStepsPerSWO, String filePath, int finalSWOIteration, int currentSWOPeriod) {
        // Neues Workbook erstellen
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Optimization Results");

        // Kopfzeile erstellen
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Iteration");
        headerRow.createCell(1).setCellValue("Agent ID");
        headerRow.createCell(2).setCellValue("Period");
        headerRow.createCell(3).setCellValue("x-Wert (Leistung)");
        headerRow.createCell(4).setCellValue("y-Wert (State)");
        headerRow.createCell(5).setCellValue("S-Wert 1");
        headerRow.createCell(6).setCellValue("S-Wert 2");
        headerRow.createCell(7).setCellValue("U-Wert 1");
        headerRow.createCell(8).setCellValue("U-Wert 2");
        headerRow.createCell(9).setCellValue("EnergyBalance");
        headerRow.createCell(10).setCellValue("U-Wert 3");

        int rowNum = 1; // Zeilennummer für die Werte

        // Iteriere über alle Iterationen in xRTO
        for (Integer iteration : dataModel.getXRTO().keySet()) {
        	
//            if (iteration == 0) {
//                continue;  // Iteration 0 überspringen
//            }

            // Iteriere über alle Agenten
            for (int agentIndex = 0; agentIndex < dataModel.getXRTO().get(iteration).length; agentIndex++) {

                // Iteriere über alle Perioden (RTO-Schritte)
                for (int periodIndex = 0; periodIndex < rtoStepsPerSWO; periodIndex++) {
                    Row row = sheet.createRow(rowNum++);

                    // Iterationsnummer, Agenten-ID und Periode
                    row.createCell(0).setCellValue(iteration);
                    row.createCell(1).setCellValue(agentIndex + 1); // Agenten-ID (beginnend bei 1)
                    row.createCell(2).setCellValue(periodIndex + 1); // Periode (beginnend bei 1)

                    // x-Wert
                    double xValue = dataModel.getXRTO().get(iteration)[agentIndex][periodIndex];
                    row.createCell(3).setCellValue(xValue);

                    // y-Wert (State)
                    String activeState = "None";
                    boolean[][][] yValues = dataModel.getYSWO().get(finalSWOIteration);
                    for (State state : State.values()) {
                        if (yValues[agentIndex][currentSWOPeriod-1][state.ordinal()]) { 
                            activeState = state.name();
                            break;
                        }
                    }
                    row.createCell(4).setCellValue(activeState);

                    // s-Werte
                    double s1Value = dataModel.getSRTO().get(iteration)[agentIndex][periodIndex][0];
                    double s2Value = dataModel.getSRTO().get(iteration)[agentIndex][periodIndex][1];
                    row.createCell(5).setCellValue(s1Value);
                    row.createCell(6).setCellValue(s2Value);

                    // u-Werte
                    double u1Value = dataModel.getURTO().get(iteration)[agentIndex][periodIndex][0];
                    double u2Value = dataModel.getURTO().get(iteration)[agentIndex][periodIndex][1];
                    double u3Value = dataModel.getURTO().get(iteration)[agentIndex][periodIndex][2];
                    double u4Value = dataModel.getURTO().get(iteration)[agentIndex][periodIndex][3];
                    row.createCell(7).setCellValue(u1Value);
                    row.createCell(8).setCellValue(u2Value);
                    row.createCell(9).setCellValue(u3Value);
                    row.createCell(10).setCellValue(u4Value);
                }
            }
        }

        // Schreibe die Arbeitsmappe in die Datei
        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
            System.out.println("Ergebnisse wurden in die Datei " + filePath + " geschrieben.");
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
    
    // Getter für xRTO
    public Map<Integer, double[][]> getXRTO() {
        return xRTO;
    }

    // Setter für xRTO (optional, falls du die Daten später setzen musst)
    public void setXRTO(Map<Integer, double[][]> xRTO) {
        this.xRTO = xRTO;
    }

    // Getter für ySWO
    public Map<Integer, boolean[][][]> getYSWO() {
        return ySWO;
    }

    // Setter für ySWO (optional, falls du die Daten später setzen musst)
    public void setYSWO(Map<Integer, boolean[][][]> ySWO) {
        this.ySWO = ySWO;
    }

    // Getter für sRTO
    public Map<Integer, double[][][]> getSRTO() {
        return sRTO;
    }

    // Setter für sRTO (optional, falls du die Daten später setzen musst)
    public void setSRTO(Map<Integer, double[][][]> sRTO) {
        this.sRTO = sRTO;
    }

    // Getter für uRTO
    public Map<Integer, double[][][]> getURTO() {
        return uRTO;
    }

    // Setter für uRTO (optional, falls du die Daten später setzen musst)
    public void setURTO(Map<Integer, double[][][]> uRTO) {
        this.uRTO = uRTO;
    }
    
    public double[][] calculateFluctuatingRenewableEnergy(int rtoStepsPerSWOPeriod, double renewableEnergySWO, long seed) {
        double[][] renewableEnergyMatrix = new double[rtoStepsPerSWOPeriod][rtoStepsPerSWOPeriod];

        // Grundwert pro RTO-Periode berechnen
        double baseEnergyPerRTO = renewableEnergySWO / rtoStepsPerSWOPeriod;
        Random random = new Random(seed); // Fixierter Seed für reproduzierbare Zufallszahlen

        // Erste Spalte: Werte berechnen mit einem Faktor zwischen 1.0 und 1.1 (Mittelwert ca. 1.05)
        for (int row = 0; row < rtoStepsPerSWOPeriod; row++) {
            double fluctuationFactor = 1.0 + (random.nextDouble() * 0.1);
            renewableEnergyMatrix[row][0] = baseEnergyPerRTO * fluctuationFactor;
        }

        // Weitere Spalten: Für jede Spalte werden die ersten (col+1) Werte aus der vorherigen Spalte übernommen.
        // Für die restlichen Zeilen wird ein neuer Wert berechnet. Hier wird der Zufallsfaktor so gewählt,
        // dass er im Mittel etwas über 1 liegt. Beispielsweise: Faktor = 1.0 + (-0.05 + random*0.2)
        // Das ergibt einen Bereich von [0.95, 1.15] mit Mittelwert 1.0+(-0.05+0.1)=1.05.
        for (int col = 1; col < rtoStepsPerSWOPeriod; col++) {
            // Übernehme die ersten (col + 1) Werte aus der vorherigen Spalte
            for (int row = 0; row <= col; row++) {
                renewableEnergyMatrix[row][col] = renewableEnergyMatrix[row][col - 1];
            }
            // Berechne neue Werte für die restlichen Zeilen
            for (int row = col + 1; row < rtoStepsPerSWOPeriod; row++) {
                double fluctuationFactor = 1.0 + (-0.05 + (random.nextDouble() * 0.2));
                renewableEnergyMatrix[row][col] = baseEnergyPerRTO * fluctuationFactor;
            }
        }

        return renewableEnergyMatrix;
    }

    
//    public double[][] calculateFluctuatingRenewableEnergy(int rtoStepsPerSWOPeriod, double renewableEnergySWO, long seed) {
//        // Erstelle eine Matrix mit Dimension [rtoStepsPerSWOPeriod][rtoStepsPerSWOPeriod]
//        double[][] renewableEnergyMatrix = new double[rtoStepsPerSWOPeriod][rtoStepsPerSWOPeriod];
//
//        // Berechne den Basiswert pro RTO-Periode
//        double baseEnergyPerRTO = renewableEnergySWO / rtoStepsPerSWOPeriod;
//        Random random = new Random(seed); // Fixierter Seed für reproduzierbare Zufallszahlen
//
//        // Für jede Spalte (RTO-Periode) werden alle Zeilen (z. B. die verschiedenen Unterperioden) belegt.
//        // Hier wird sichergestellt, dass für die ersten 5 Zeilen (bei 10 Perioden) Werte unter baseEnergyPerRTO
//        // und für die letzten 5 Werte darüber liegen.
//        for (int col = 0; col < rtoStepsPerSWOPeriod; col++) {
//            for (int row = 0; row < rtoStepsPerSWOPeriod; row++) {
//                double fluctuationFactor;
//                if (row < rtoStepsPerSWOPeriod / 2) {
//                    // Für die ersten 5 Perioden: Zufallsfaktor zwischen 0.8 und 0.99
//                    fluctuationFactor = 0.8 + (random.nextDouble() * 0.19);
//                } else {
//                    // Für die letzten 5 Perioden: Zufallsfaktor zwischen 1.01 und 1.2
//                    fluctuationFactor = 1.00 + (random.nextDouble() * 0.19);
//                }
//                renewableEnergyMatrix[row][col] = baseEnergyPerRTO * fluctuationFactor;
//            }
//        }
//
//        return renewableEnergyMatrix;
//    }

    public void exportRenewableEnergyMatrixToExcel(String filePath) {
        double[][] matrix = this.getFluctuatingRenewableEnergyMatrix();

        if (matrix == null) {
            System.err.println("Fehler: Die erneuerbare Energie-Matrix wurde nicht berechnet.");
            return;
        }

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Renewable Energy");

        // Erstellen der Excel-Tabelle
        for (int rowIdx = 0; rowIdx < matrix.length; rowIdx++) {
            Row row = sheet.createRow(rowIdx);
            for (int colIdx = 0; colIdx < matrix[rowIdx].length; colIdx++) {
                Cell cell = row.createCell(colIdx);
                cell.setCellValue(matrix[rowIdx][colIdx]);
            }
        }

        // Datei speichern
        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
            System.out.println("Excel-Datei erfolgreich erstellt: " + filePath);
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
    
    public void exportXRTOResultsToExcel(String filePath, int iterationsUntilConvergence, double energyResidual, long computationTime) {
        // Erstelle ein neues Excel-Arbeitsbuch und ein Arbeitsblatt
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("xRTO Results");

        // Erstelle die Kopfzeile
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Elektrolyseur ID");
        headerRow.createCell(1).setCellValue("Periode");
        headerRow.createCell(2).setCellValue("x-Wert");

        int rowIndex = 1;
        // Iteriere über alle Einträge in der Map: Key = Elektrolyseur-ID (0-basiert), Value = Array der x-Werte
        for (Map.Entry<Integer, double[]> entry : xRTOResults.entrySet()) {
            int agentId = entry.getKey();
            double[] xValues = entry.getValue();
            for (int period = 0; period < xValues.length; period++) {
                Row row = sheet.createRow(rowIndex++);
                // Da der Schlüssel 0-basiert ist, addiere 1 für die originale Elektrolyseur-ID
                row.createCell(0).setCellValue(agentId + 1);
                row.createCell(1).setCellValue(period +1 );
                row.createCell(2).setCellValue(xValues[period]);
            }
        }
        
        // Füge eine zusätzliche Zeile hinzu, in der die Anzahl der benötigten Iterationen geschrieben wird
        Row convRow = sheet.createRow(rowIndex++);
        convRow.createCell(0).setCellValue("Benötigte Iterationen bis Konvergenz:");
        convRow.createCell(1).setCellValue(iterationsUntilConvergence);
        
        Row convRow2 = sheet.createRow(rowIndex++);
        convRow2.createCell(0).setCellValue("Energieresidual:");
        convRow2.createCell(1).setCellValue(energyResidual);
        
        Row convRow3 = sheet.createRow(rowIndex++);
        convRow3.createCell(0).setCellValue("Computation Time (ns):");
        convRow3.createCell(1).setCellValue(computationTime);

//        // Passe die Spaltenbreite automatisch an
//        for (int i = 0; i < 3; i++) {
//            sheet.autoSizeColumn(i);
//        }

        // Schreibe das Arbeitsbuch in die angegebene Datei
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
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

      // ... existing code ...
public void exportAllVariablesPerElectrolyzerToExcel(int maxIteration, Set<Electrolyzer> electrolyzers, 
Set<Period> periods, String baseFilePath) throws IOException {

String filePath = baseFilePath + "_All_Electrolyzers.xlsx";

try (Workbook workbook = new XSSFWorkbook()) {

// Erstelle ein Sheet für alle Variablen aller Elektrolyseure
Sheet mainSheet = workbook.createSheet("All Variables All Electrolyzers");

// Erstelle Header-Zeile
Row headerRow = mainSheet.createRow(0);
headerRow.createCell(0).setCellValue("Iteration");
headerRow.createCell(1).setCellValue("Period");
headerRow.createCell(2).setCellValue("Electrolyzer_ID");
headerRow.createCell(3).setCellValue("X_Value");
headerRow.createCell(4).setCellValue("Y_IDLE");
headerRow.createCell(5).setCellValue("Y_STARTING");
headerRow.createCell(6).setCellValue("Y_PRODUCTION");
headerRow.createCell(7).setCellValue("Y_STANDBY");
headerRow.createCell(8).setCellValue("S_1");
headerRow.createCell(9).setCellValue("S_2");
headerRow.createCell(10).setCellValue("U_1");
headerRow.createCell(11).setCellValue("U_2");
headerRow.createCell(12).setCellValue("U_3");
headerRow.createCell(13).setCellValue("Active_State");

int rowIndex = 1;

// Iteriere über alle Iterationen
for (int iteration = 0; iteration <= maxIteration; iteration++) {

// Hole alle Variablen für diese Iteration
double[][] xValues = getXSWOValuesForIteration(iteration);
boolean[][][] yValues = getYSWOValuesForIteration(iteration);
double[][][] sValues = getSSWOValuesForIteration(iteration);
double[][][] uValues = getUSWOValuesForIteration(iteration);

// Prüfe ob Daten für diese Iteration vorhanden sind
if (xValues == null || yValues == null || sValues == null || uValues == null) {
continue; // Überspringe diese Iteration, wenn keine Daten vorhanden
}

// Iteriere über alle Elektrolyseure
for (Electrolyzer electrolyzer : electrolyzers) {
int electrolyzerId = electrolyzer.getId();
int agentIndex = electrolyzerId - 1;

// Prüfe ob der Agent-Index gültig ist
if (agentIndex >= xValues.length || agentIndex >= yValues.length || 
agentIndex >= sValues.length || agentIndex >= uValues.length) {
continue;
}

// Iteriere über alle Perioden
for (Period period : periods) {
int periodIndex = period.getT() - 1;

// Prüfe ob der Perioden-Index gültig ist
if (periodIndex >= xValues[agentIndex].length || 
periodIndex >= yValues[agentIndex].length ||
periodIndex >= sValues[agentIndex].length || 
periodIndex >= uValues[agentIndex].length) {
continue;
}

Row row = mainSheet.createRow(rowIndex++);

// Iteration
row.createCell(0).setCellValue(iteration);

// Periode
row.createCell(1).setCellValue(period.getT());

// Elektrolyseur ID
row.createCell(2).setCellValue(electrolyzerId);

// X-Wert
double xValue = xValues[agentIndex][periodIndex];
row.createCell(3).setCellValue(xValue);

// Y-Werte (Zustände)
boolean[] yState = yValues[agentIndex][periodIndex];
row.createCell(4).setCellValue(yState[State.IDLE.ordinal()] ? 1 : 0);
row.createCell(5).setCellValue(yState[State.STARTING.ordinal()] ? 1 : 0);
row.createCell(6).setCellValue(yState[State.PRODUCTION.ordinal()] ? 1 : 0);
row.createCell(7).setCellValue(yState[State.STANDBY.ordinal()] ? 1 : 0);

// S-Werte (Slack-Variablen)
double[] sState = sValues[agentIndex][periodIndex];
row.createCell(8).setCellValue(sState[0]); // S_1
row.createCell(9).setCellValue(sState[1]); // S_2

// U-Werte (Dual-Variablen)
double[] uState = uValues[agentIndex][periodIndex];
row.createCell(10).setCellValue(uState[0]); // U_1
row.createCell(11).setCellValue(uState[1]); // U_2
row.createCell(12).setCellValue(uState[2]); // U_3

// Aktiver Zustand (String-Repräsentation)
String activeState = "None";
for (State state : State.values()) {
if (yState[state.ordinal()]) {
activeState = state.name();
break;
}
}
row.createCell(13).setCellValue(activeState);
}
}
}

// Erstelle ein Sheet für Zusammenfassung pro Iteration
Sheet summarySheet = workbook.createSheet("Summary per Iteration");

// Header für Zusammenfassung
Row summaryHeaderRow = summarySheet.createRow(0);
summaryHeaderRow.createCell(0).setCellValue("Iteration");
summaryHeaderRow.createCell(1).setCellValue("Total_X_Sum");
summaryHeaderRow.createCell(2).setCellValue("Avg_X_Value");
summaryHeaderRow.createCell(3).setCellValue("Total_Production_Periods");
summaryHeaderRow.createCell(4).setCellValue("Total_Idle_Periods");
summaryHeaderRow.createCell(5).setCellValue("Total_Starting_Periods");
summaryHeaderRow.createCell(6).setCellValue("Total_Standby_Periods");
summaryHeaderRow.createCell(7).setCellValue("Total_S_Sum");
summaryHeaderRow.createCell(8).setCellValue("Total_U_Sum");

int summaryRowIndex = 1;

// Erstelle Zusammenfassung pro Iteration
for (int iteration = 0; iteration <= maxIteration; iteration++) {
double[][] xValues = getXSWOValuesForIteration(iteration);
boolean[][][] yValues = getYSWOValuesForIteration(iteration);
double[][][] sValues = getSSWOValuesForIteration(iteration);
double[][][] uValues = getUSWOValuesForIteration(iteration);

if (xValues == null || yValues == null || sValues == null || uValues == null) {
continue;
}

Row summaryRow = summarySheet.createRow(summaryRowIndex++);

// Iteration
summaryRow.createCell(0).setCellValue(iteration);

// Berechne Statistiken über alle Elektrolyseure
double totalXSum = 0.0;
int totalProductionPeriods = 0;
int totalIdlePeriods = 0;
int totalStartingPeriods = 0;
int totalStandbyPeriods = 0;
double totalSSum = 0.0;
double totalUSum = 0.0;
int totalValidPeriods = 0;

for (Electrolyzer electrolyzer : electrolyzers) {
int agentIndex = electrolyzer.getId() - 1;

if (agentIndex >= xValues.length || agentIndex >= yValues.length || 
agentIndex >= sValues.length || agentIndex >= uValues.length) {
continue;
}

for (Period period : periods) {
int periodIndex = period.getT() - 1;

if (periodIndex < xValues[agentIndex].length && 
periodIndex < yValues[agentIndex].length &&
periodIndex < sValues[agentIndex].length && 
periodIndex < uValues[agentIndex].length) {

// X-Werte
double xValue = xValues[agentIndex][periodIndex];
totalXSum += xValue;

// Y-Werte (Zustände zählen)
boolean[] yState = yValues[agentIndex][periodIndex];
if (yState[State.PRODUCTION.ordinal()]) totalProductionPeriods++;
if (yState[State.IDLE.ordinal()]) totalIdlePeriods++;
if (yState[State.STARTING.ordinal()]) totalStartingPeriods++;
if (yState[State.STANDBY.ordinal()]) totalStandbyPeriods++;

// S-Werte
double[] sState = sValues[agentIndex][periodIndex];
totalSSum += Math.abs(sState[0]) + Math.abs(sState[1]);

// U-Werte
double[] uState = uValues[agentIndex][periodIndex];
totalUSum += Math.abs(uState[0]) + Math.abs(uState[1]) + Math.abs(uState[2]);

totalValidPeriods++;
}
}
}

// Fülle Zusammenfassungszeile
summaryRow.createCell(1).setCellValue(totalXSum);
summaryRow.createCell(2).setCellValue(totalValidPeriods > 0 ? totalXSum / totalValidPeriods : 0.0);
summaryRow.createCell(3).setCellValue(totalProductionPeriods);
summaryRow.createCell(4).setCellValue(totalIdlePeriods);
summaryRow.createCell(5).setCellValue(totalStartingPeriods);
summaryRow.createCell(6).setCellValue(totalStandbyPeriods);
summaryRow.createCell(7).setCellValue(totalSSum);
summaryRow.createCell(8).setCellValue(totalUSum);
}

// Auto-resize Spalten
for (int i = 0; i < 14; i++) {
mainSheet.autoSizeColumn(i);
}
for (int i = 0; i < 9; i++) {
summarySheet.autoSizeColumn(i);
}

// Speichere die Datei
try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
workbook.write(fileOut);
}

System.out.println("Alle Variablen für alle Elektrolyseure erfolgreich exportiert nach: " + filePath);

} catch (Exception e) {
System.err.println("Fehler beim Exportieren der Variablen: " + e.getMessage());
e.printStackTrace();
}
}

    
    public void exportXRTOResultsWithYToExcel(String filePath, int finalSWOIteration, int currentSWOPeriod, int rtoStepsPerSWO) {
        // Erstelle ein neues Excel-Arbeitsbuch und ein Arbeitsblatt
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("xRTO & y Results");

        // Erstelle die Kopfzeile
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Iteration");
        headerRow.createCell(1).setCellValue("Elektrolyseur ID");
        headerRow.createCell(2).setCellValue("Periode");
        headerRow.createCell(3).setCellValue("x-Wert");
        headerRow.createCell(4).setCellValue("y-Wert (State)");

        int rowNum = 1; // Zeilennummer für die Werte

        // Hole die gesamte xRTO-Datenstruktur: Map<Integer, double[][]>
        // Schlüssel: Iteration, Wert: double[agentIndex][periode]
        Map<Integer, double[][]> xRTO = getXRTO();

        // Hole die ySWO-Datenstruktur: Map<Integer, boolean[][][]>
        // Schlüssel: Iteration, Wert: boolean[agentIndex][periode][State.ordinal()]
        Map<Integer, boolean[][][]> ySWO = getYSWO();

        // Iteriere über alle Iterationen in xRTO
        for (Integer iteration : xRTO.keySet()) {
            double[][] xValuesForIteration = xRTO.get(iteration);
            // Für jeden Agenten (Elektrolyseur)
            for (int agentIndex = 0; agentIndex < xValuesForIteration.length; agentIndex++) {
                // Iteriere über alle Perioden (RTO-Schritte)
                for (int periodIndex = 0; periodIndex < rtoStepsPerSWO; periodIndex++) {
                    Row row = sheet.createRow(rowNum++);

                    // Schreibe Iterationsnummer, Agenten-ID (1-basiert) und Periode (1-basiert)
                    row.createCell(0).setCellValue(iteration);
                    row.createCell(1).setCellValue(agentIndex + 1);
                    row.createCell(2).setCellValue(periodIndex + 1);

                    // x-Wert
                    double xValue = xValuesForIteration[agentIndex][periodIndex];
                    row.createCell(3).setCellValue(xValue);

                    // y-Wert (State)
                    // Wir nehmen die y-Daten aus der finalSWOIteration aus ySWO.
                    // Dabei gehen wir davon aus, dass ySWO.get(finalSWOIteration) ein Array vom Typ boolean[agentIndex][periode][State.ordinal()] liefert.
                    String activeState = "None";
                    boolean[][][] yValuesForIteration = ySWO.get(finalSWOIteration);
                    if (yValuesForIteration != null && agentIndex < yValuesForIteration.length &&
                        (currentSWOPeriod - 1) < yValuesForIteration[agentIndex].length) {
                        boolean[] stateFlags = yValuesForIteration[agentIndex][currentSWOPeriod - 1];
                        // Bestimme den aktiven Zustand: Es wird der erste Zustand, der true ist, verwendet.
                        for (State state : State.values()) {
                            if (stateFlags[state.ordinal()]) {
                                activeState = state.name();
                                break;
                            }
                        }
                    }
                    row.createCell(4).setCellValue(activeState);
                }
            }
        }

//        // Passe die Spaltenbreiten automatisch an
//        for (int i = 0; i < 5; i++) {
//            sheet.autoSizeColumn(i);
//        }

        // Schreibe das Workbook in die Datei
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
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



}
