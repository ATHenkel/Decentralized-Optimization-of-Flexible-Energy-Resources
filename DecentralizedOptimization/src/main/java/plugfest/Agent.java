package plugfest;

import java.util.Objects;

public class Agent {
	int a;

	public Agent(int a) {
		this.a = a;
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
