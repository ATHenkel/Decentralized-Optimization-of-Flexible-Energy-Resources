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
var y{E,T,S} binary;
var s1 >= 0;
var s2 >= 0;

# Constraints
subject to Ramp_Rate {e in E, t in T: ord(t) < card(T)}:
    abs(x[e,t+1] - x[e,t]) <= R[e] * (y[e,t,"production"] + y[e,t,"starting"] + y[e,t,"standby"]);
subject to Slack_Lower_Bound_1: x_min[e] * y[e,t,"production"] - x[e,t] + s1 = 0;
subject to Slack_Upper_Bound_2: x[e,t] - x_max[e] * y[e,t,"production"] + s2 = 0;
subject to State_Exclusivity {e in E, t in T}:
    sum {s in S} y[e,t,s] = 1;
subject to Min_Holding_Duration {e in E, s in S, t in T, tau in 1..min_dwell[e,s]-1}:
    y[e,t,s] - y[e,t-1,s] <= y[e,t+tau,s];
subject to Starting_Transition {e in E, t in T}:
    y[e,t,"starting"] <= y[e,t-1,"idle"] + y[e,t-1,"starting"];
subject to Idle_Transition {e in E, t in T}:
    y[e,t,"idle"] <= y[e,t-1,"production"] + y[e,t-1,"idle"] + y[e,t-1,"standby"];
subject to Standby_Transition {e in E, t in T}:
    y[e,t,"standby"] <= y[e,t-1,"production"] + y[e,t-1,"standby"] + y[e,t-startup_delay[e],"starting"];
subject to Production_Transition {e in E, t in T}:
    y[e,t,"production"] <= y[e,t-1,"production"] + y[e,t-1,"standby"] + y[e,t-startup_delay[e],"starting"];
subject to Transition_Consistency {e in E, t in T}:
    y[e,t,"production"] + y[e,t,"standby"] + y[e,t,"idle"] >= y[e,t-startup_delay[e],"starting"];

