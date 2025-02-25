package plugfest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Parameters {

    Map<Agent, Double> startupCost;
    Map<Agent, Double> standbyCost;
    Map<Agent, Double> powerElectrolyzer;
    Map<Period, Double> electricityCost;
    Map<Agent, Double> minOperation;
    Map<Agent, Double> maxOperation;
    double demand;  // Demand ist jetzt ein globaler Wert
    Map<Agent, Double> slope;
    Map<Agent, Double> intercept;
    Map<Agent, Double> rampUpRate;
    Map<Agent, Double> rampDownRate;
    Map<Period, Double> availablePower;
    double intervalLengthSWO;
    Map<Agent, Integer> startupDuration;
    double demandDeviationCost;
    Map<Agent, Map<State, Integer>> holdingDurations;
    Set<Period> periods;

    public Map<Period, Double> renewableEnergy; // Erneuerbare Energie
    public Map<Period, Map<Agent, Double>> swoXVarResults; // Ergebnisse der SWO
    public Map<Period, Map<Agent, Map<State, Double>>> swoYVarResults; // Zustandsbezogene SWO-Ergebnisse
    public Map<Period, Double> swoGridEnergyResults; // Netzenergie-Ergebnisse aus der SWO
    public Map<Period, Map<Integer, Double>> fluctuatingRenewableEnergy; // Fluktuierende erneuerbare Energien

    public int rtoStepsPerSWOStep = 10; // Anzahl der RTO-Schritte pro SWO-Schritt

    // Vollständiger Konstruktor
    public Parameters(Map<Agent, Double> startupCost, Map<Agent, Double> standbyCost,
                      Map<Agent, Double> powerElectrolyzer, Map<Period, Double> electricityCost,
                      Map<Agent, Double> minOperation, Map<Agent, Double> maxOperation,
                      double demand,  // globaler Demand
                      Map<Agent, Double> slope, Map<Agent, Double> intercept,
                      Map<Period, Double> availablePower, double intervalLength,
                      Map<Agent, Integer> startupDuration, double demandDeviationCost,
                      Map<Agent, Map<State, Integer>> holdingDurations, Set<Period> periods,
                      Map<Period, Double> renewableEnergy,
                      Map<Period, Map<Agent, Double>> swoResults,
                      Map<Period, Double> netEnergyResults,
                      Map<Period, Map<Integer, Double>> fluctuatingRenewableEnergy,
                      int rtoStepsPerSWOStep) {

        this.startupCost = startupCost;
        this.standbyCost = standbyCost;
        this.powerElectrolyzer = powerElectrolyzer;
        this.electricityCost = electricityCost;
        this.minOperation = minOperation;
        this.maxOperation = maxOperation;
        this.demand = demand;
        this.slope = slope;
        this.intercept = intercept;
        this.availablePower = availablePower;
        this.intervalLengthSWO = intervalLength;
        this.startupDuration = startupDuration;
        this.demandDeviationCost = demandDeviationCost;
        this.holdingDurations = holdingDurations;
        this.periods = periods;
        this.renewableEnergy = renewableEnergy;
        this.swoXVarResults = swoResults;
        this.swoGridEnergyResults = netEnergyResults;
        this.fluctuatingRenewableEnergy = fluctuatingRenewableEnergy;
        this.rtoStepsPerSWOStep = rtoStepsPerSWOStep;
    }

    // Standardkonstruktor mit Standardwerten für optionale Parameter
    public Parameters(Map<Agent, Double> startupCost, Map<Agent, Double> standbyCost,
                      Map<Agent, Double> powerElectrolyzer, Map<Period, Double> electricityCost,
                      Map<Agent, Double> minOperation, Map<Agent, Double> maxOperation,
                      double demand,  // globaler Demand
                      Map<Agent, Double> slope, Map<Agent, Double> intercept,
                      Map<Period, Double> availablePower, double intervalLength,
                      Map<Agent, Integer> startupDuration, double demandDeviationCost,
                      Map<Agent, Map<State, Integer>> holdingDurations, Set<Period> periods,
                      Map<Period, Double> renewableEnergy) {

        this(startupCost, standbyCost, powerElectrolyzer, electricityCost,
             minOperation, maxOperation, demand, slope, intercept, availablePower,
             intervalLength, startupDuration, demandDeviationCost, holdingDurations,
             periods, renewableEnergy, new HashMap<>(), new HashMap<>(), new HashMap<>(), 10);
    }
}
