package plugfest;

import java.util.Objects;

public class Period {
	int t;

	public Period(int t) {
		this.t = t;
	}

	// Getter method for 't'
	public int getT() {
		return t;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		Period period = (Period) obj;
		return t == period.t;
	}

	@Override
	public int hashCode() {
		return Objects.hash(t);
	}
}
