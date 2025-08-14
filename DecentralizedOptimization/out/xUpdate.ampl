# Sets
set E; # Electrolyzers
set T; # Time periods
set S := {"idle", "starting", "production", "standby"};

# Parameters
param P{E}; # Power capacity (kW)
param c_elec{T}; # Electricity price (EUR/kWh)
param c_start{E}; # Startup cost (EUR)
param c_standby{E}; # Standby cost (EUR)
param c_penalty; # Penalty cost (EUR/kg)
param alpha{E}; # Efficiency (kg/kWh)
param x_min{E}; # Minimum utilization
param x_max{E}; # Maximum utilization
param R{E}; # Ramp rate (kW/period)
param delta_t; # Period duration (hours)
param min_dwell{E,S}; # Minimum dwell time (periods)
param startup_delay{E}; # Startup delay (periods)
param D{T}; # Demand (kg)

# Decision Variables
var x{E,T} >= 0;

# Constraints
subject to Ramp_Rate {e in E, t in T: ord(t) < card(T)}:
    abs(x[e,t+1] - x[e,t]) <= R[e] * (y[e,t,"production"] + y[e,t,"starting"] + y[e,t,"standby"]);
subject to Slack_Lower_Bound_1: x_min[e] * y[e,t,"production"] - x[e,t] + s1 = 0;
subject to Slack_Upper_Bound_2: x[e,t] - x_max[e] * y[e,t,"production"] + s2 = 0;
