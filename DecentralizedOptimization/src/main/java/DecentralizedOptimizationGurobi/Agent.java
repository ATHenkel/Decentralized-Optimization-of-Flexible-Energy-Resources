package DecentralizedOptimizationGurobi;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBModel;

class Point {
	double x;
	double y;

	public Point(double x, double y) {
		this.x = x;
		this.y = y;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	@Override
	public String toString() {
		return "Point{" + "x=" + x + ", y=" + y + '}';
	}
}

class Period {
    final int t;

    // Constructor with validation
    public Period(int t) {
        if (t <= 0) {
            throw new IllegalArgumentException("Period number must be positive.");
        }
        this.t = t;
    }

    // Getter method for 't'
    public int getT() {
        return t;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Period period = (Period) obj;
        return t == period.t;
    }

    @Override
    public int hashCode() {
        return Objects.hash(t);
    }

    // Optional: toString method for better debugging
    @Override
    public String toString() {
        return "Period{" + "t=" + t + '}';
    }
}

public class Agent {
	public int a; // Agent ID

	// Agent-specific data for optimization
	private Map<Period, Double> xValues; // x values for each period
	private Map<Period, Boolean[]> yValues; // y values for each period
	private Map<Period, Double[]> sValues; // s values for each period
	private double[] uValues; // dual values for each period

	// Constructor
	public Agent(int a) {
		this.a = a;
		this.xValues = new HashMap<>();
		this.yValues = new HashMap<>();
		this.sValues = new HashMap<>();
		// Initialize uValues with the number of periods (assuming a fixed size)
		this.uValues = new double[0]; // Adjust size later based on the actual number of periods
	}

	public int getId() {
		return a;
	}

	// Agent-specific x-update method
	public void optimizeX(GRBModel model, Parameters params, Set<Period> periods, Set<Agent> agents, int iteration,
	                      DataExchange dataExchange, double rho)
	                      throws GRBException, IOException {
	    xUpdate xUpdate = new xUpdate();
	    xUpdate.optimizeX(model, params, agents, periods, iteration, dataExchange, rho);
	}

	// Agent-specific y-update method
	public void optimizeY(GRBModel model, Parameters params, Set<Period> periods, int iteration,
	                      DataExchange dataExchange, double rho)
	                      throws GRBException {
	    yUpdate yUpdate = new yUpdate();
	    yUpdate.optimizeY(model, params, this, periods, iteration, dataExchange, rho);
	}

	// Agent-specific s-update method
	public void optimizeS(GRBModel model, Parameters params, Set<Period> periods, int iteration,
	                      DataExchange dataExchange, double rho)
	                      throws GRBException {
	    sUpdate sUpdate = new sUpdate();
	    sUpdate.optimizeS(model, params, this, periods, iteration, dataExchange, rho);
	}

	// Agent-specific dual update method
	public void updateU(GRBModel model, Parameters params, Set<Period> periods, int iteration,
	                    DataExchange dataExchange, double rho)
	                    throws GRBException {
	    dualUpdate dualUpdate = new dualUpdate();
	    dualUpdate.optimizeU(model, params, this, periods, iteration, dataExchange, rho);
	}

	
	// Getter methods for optimization variables
	public Map<Period, Double> getXValues() {
		return xValues;
	}

	public Map<Period, Boolean[]> getYValues() {
		return yValues;
	}

	public Map<Period, Double[]> getSValues() {
		return sValues;
	}

	public double[] getUValues() {
		return uValues;
	}

	// Setter methods for optimization variables
	public void setXValues(Map<Period, Double> xValues) {
		this.xValues = xValues;
	}

	public void setYValues(Map<Period, Boolean[]> yValues) {
		this.yValues = yValues;
	}

	public void setSValues(Map<Period, Double[]> sValues) {
		this.sValues = sValues;
	}

	public void setUValues(double[] uValues) {
		this.uValues = uValues;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		Agent agent = (Agent) obj;
		return a == agent.a;
	}

	@Override
	public int hashCode() {
		return Objects.hash(a);
	}
}

enum State {
	IDLE, STARTING, PRODUCTION, STANDBY
}
