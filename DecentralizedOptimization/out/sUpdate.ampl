# Slack Variable Updates
var s1 >= 0;
var s2 >= 0;

# Slack Variable Constraints
subject to Slack_Lower_Bound_1: x_min[e] * y[e,t,"production"] - x[e,t] + s1 = 0;
subject to Slack_Upper_Bound_2: x[e,t] - x_max[e] * y[e,t,"production"] + s2 = 0;

