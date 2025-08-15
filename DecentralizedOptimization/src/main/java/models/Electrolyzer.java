package models;

public class Electrolyzer {
    private int id;
    private double powerElectrolyzer; // Maximum power of the electrolyzer
    private double minOperation; // Minimum operating power
    private double maxOperation; // Maximum operating power
    private double slope; // Slope of the production function
    private double intercept; // Intercept of the production function
    private int startupDuration; // Startup duration of the electrolyzer
    private double startupCost; // Startup costs
    private double standbyCost; // Costs in standby mode

    // Constructor
    public Electrolyzer(int id, double powerElectrolyzer, double minOperation, double maxOperation, double slope,
                        double intercept, int startupDuration, double startupCost, double standbyCost) {
        this.id = id;
        this.powerElectrolyzer = powerElectrolyzer;
        this.minOperation = minOperation;
        this.maxOperation = maxOperation;
        this.slope = slope;
        this.intercept = intercept;
        this.startupDuration = startupDuration;
        this.startupCost = startupCost;
        this.standbyCost = standbyCost;
    }

    // Getter methods for the parameters
    public int getId() {
        return id;
    }

    public double getPowerElectrolyzer() {
        return powerElectrolyzer;
    }

    public double getMinOperation() {
        return minOperation;
    }

    public double getMaxOperation() {
        return maxOperation;
    }

    public double getSlope() {
        return slope;
    }

    public double getIntercept() {
        return intercept;
    }

    public int getStartupDuration() {
        return startupDuration;
    }

    public double getStartupCost() {
        return startupCost;
    }

    public double getStandbyCost() {
        return standbyCost;
    }

    // Optional: Setter methods, if you want to change the parameters at runtime
    public void setPowerElectrolyzer(double powerElectrolyzer) {
        this.powerElectrolyzer = powerElectrolyzer;
    }

    public void setMinOperation(double minOperation) {
        this.minOperation = minOperation;
    }

    public void setMaxOperation(double maxOperation) {
        this.maxOperation = maxOperation;
    }

    public void setSlope(double slope) {
        this.slope = slope;
    }

    public void setIntercept(double intercept) {
        this.intercept = intercept;
    }

    public void setStartupDuration(int startupDuration) {
        this.startupDuration = startupDuration;
    }

    public void setStartupCost(double startupCost) {
        this.startupCost = startupCost;
    }

    public void setStandbyCost(double standbyCost) {
        this.standbyCost = standbyCost;
    }
}
