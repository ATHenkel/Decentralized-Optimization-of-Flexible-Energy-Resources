package models;

public class Electrolyzer {
    private int id;
    private double powerElectrolyzer; // Maximale Leistung des Elektrolyseurs
    private double minOperation; // Minimale Betriebsleistung
    private double maxOperation; // Maximale Betriebsleistung
    private double slope; // Steigung der Produktionsfunktion
    private double intercept; // Achsenabschnitt der Produktionsfunktion
    private int startupDuration; // Startdauer des Elektrolyseurs
    private double startupCost; // Anlaufkosten
    private double standbyCost; // Kosten im Standby-Modus

    // Konstruktor
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

    // Getter-Methoden für die Parameter
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

    // Optional: Setter-Methoden, falls du die Parameter zur Laufzeit ändern möchtest
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
