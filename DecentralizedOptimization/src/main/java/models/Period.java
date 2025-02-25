package models;

import java.util.Objects;

public class Period {
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
