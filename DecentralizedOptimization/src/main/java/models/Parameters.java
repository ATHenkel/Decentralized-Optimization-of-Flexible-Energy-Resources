package models;

import java.util.Map;
import java.util.Set;


public class Parameters {
    public Map<Electrolyzer, Double> startupCost;
    public Map<Electrolyzer, Double> standbyCost;
    public Map<Electrolyzer, Double> powerElectrolyzer;
    public Map<Period, Double> electricityCost;
    public Map<Electrolyzer, Double> minOperation;
    public Map<Electrolyzer, Double> maxOperation;
    public Map<Period, Double> demand;
    public Map<Electrolyzer, Double> slope;
    public Map<Electrolyzer, Double> intercept;
    public Map<Period, Double> renewableEnergyForecast;
    public double intervalLengthSWO;
    public Map<Electrolyzer, Integer> startupDuration;
    public double demandDeviationCost;
    public Map<Electrolyzer, Map<State, Integer>> holdingDurations;
    public Map<Electrolyzer, Double> rampRate;
    private Set<Electrolyzer> electrolyzers;
    private Set<Period> periods;
    private int totalElectrolyzers;
    public Map<Period, Double> purchasedGridEnergy; 
    public Map<Period, Double> totalElectrolyzerEnergy; 
    
    public Parameters(
        Map<Electrolyzer, Double> startupCost, 
        Map<Electrolyzer, Double> standbyCost, 
        Map<Electrolyzer, Double> powerElectrolyzer, 
        Map<Period, Double> electricityCost, 
        Map<Electrolyzer, Double> minOperation, 
        Map<Electrolyzer, Double> maxOperation, 
        Map<Period, Double> demand, 
        Map<Electrolyzer, Double> slope, 
        Map<Electrolyzer, Double> intercept, 
        Map<Period, Double> renewableEnergyForecast, 
        double intervalLength, 
        Map<Electrolyzer, Integer> startupDuration, 
        double demandDeviationCost, 
        Map<Electrolyzer, Map<State, Integer>> holdingDurations, 
        Map<Electrolyzer, Double> rampRate,  // New ramp rate map
        Set<Electrolyzer> electrolyzers,        
        Set<Period> periods,  
        int totalElectrolyzers, 
        Map<Period, Double> purchasedGridEnergy,
        Map<Period, Double> totalElectroylzerEnergy
       
    ) {
        this.startupCost = startupCost;
        this.standbyCost = standbyCost;
        this.powerElectrolyzer = powerElectrolyzer;
        this.electricityCost = electricityCost;
        this.minOperation = minOperation;
        this.maxOperation = maxOperation;
        this.demand = demand;
        this.slope = slope;
        this.intercept = intercept;
        this.renewableEnergyForecast = renewableEnergyForecast;
        this.intervalLengthSWO = intervalLength;
        this.startupDuration = startupDuration;
        this.demandDeviationCost = demandDeviationCost;
        this.holdingDurations = holdingDurations;
        this.rampRate = rampRate; // Set ramp rate
        this.electrolyzers = electrolyzers;    
        this.periods = periods;
        this.totalElectrolyzers = totalElectrolyzers; 
        this.purchasedGridEnergy = purchasedGridEnergy;
        this.totalElectrolyzerEnergy = totalElectroylzerEnergy;
    }

    public Double getRampRate(Electrolyzer electrolyzer) {
        return rampRate.get(electrolyzer);
    }

    public void setRampRate(Electrolyzer electrolyzer, Double rate) {
        rampRate.put(electrolyzer, rate);
    }

    public int getTotalElectrolyzers() {
        return totalElectrolyzers;
    }

    public Set<Electrolyzer> getElectrolyzers() {
        return electrolyzers;
    }

    // Getter for periods
    public Set<Period> getPeriods() {
        return periods;
    }
    
    // Getter for period demand
    public Double getPeriodDemand(Period period) {
        return demand.get(period);
    }
    
    public Double getPurchasedEnergy(Period period) {
        return purchasedGridEnergy.get(period);
    }
    
    public Double getTotalElectrolyzerEnergy(Period period) {
        return totalElectrolyzerEnergy.get(period);
    }
    
    public Double getRenewableEnergy(Period period) {
        return renewableEnergyForecast.get(period);
    }
    
}
