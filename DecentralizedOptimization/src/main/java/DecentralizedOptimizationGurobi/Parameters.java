package DecentralizedOptimizationGurobi;

import java.util.Map;


public class Parameters {
		public Map<Agent, Double> startupCost;
		public Map<Agent, Double> standbyCost;
	    public Map<Agent, Double> powerElectrolyzer;
	    public Map<Period, Double> electricityCost;
	    public Map<Agent, Double> minOperation;
	    public Map<Agent, Double> maxOperation;
	    public Map<Period, Double> demand;
	    public Map<Agent, Double> slope;
	    public Map<Agent, Double> intercept;
	    public Map<Agent, Double> rampRate;
	    public Map<Period, Double> availablePower;
	    public double intervalLength;
	    public Map<Agent, Integer> startupDuration;
	    public double demandDeviationCost;
	    public Map<Agent, Map<State, Integer>> holdingDurations;

	    public Parameters(Map<Agent, Double> startupCost, Map<Agent, Double> standbyCost, Map<Agent, Double> powerElectrolyzer,
                Map<Period, Double> electricityCost, Map<Agent, Double> minOperation, Map<Agent, Double> maxOperation,
                Map<Period, Double> demand, Map<Agent, Double> slope, Map<Agent, Double> intercept,
                Map<Period, Double> availablePower, double intervalLength, Map<Agent, Integer> startupDuration,
                double demandDeviationCost, Map<Agent, Map<State, Integer>> holdingDurations, Map<Agent, Double> rampRate) {
  
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
			  this.intervalLength = intervalLength;
			  this.startupDuration = startupDuration;
			  this.demandDeviationCost = demandDeviationCost;
			  this.holdingDurations = holdingDurations;
			  this.rampRate = rampRate; 
	    }


		public double getRampRate(Agent a) {
			 return rampRate.get(a);
		}
		
}
