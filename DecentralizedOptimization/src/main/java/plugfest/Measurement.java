package plugfest;

public class Measurement {
    public String timestamp; // Beispielsweise im Format "yyyy-MM-dd HH:mm:ss"
    public double value;     // Gemessener Wert in kg

    public Measurement(String timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }
}