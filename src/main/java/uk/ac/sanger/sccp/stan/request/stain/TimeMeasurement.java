package uk.ac.sanger.sccp.stan.request.stain;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A measurement of time in seconds, with the name of the measurement
 * @author dr6
 */
public class TimeMeasurement {
    private String name;
    private int seconds;

    public TimeMeasurement() {}

    public TimeMeasurement(String name, int seconds) {
        this.name = name;
        this.seconds = seconds;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSeconds() {
        return this.seconds;
    }

    public void setSeconds(int seconds) {
        this.seconds = seconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeMeasurement that = (TimeMeasurement) o;
        return (this.seconds == that.seconds
                && Objects.equals(this.name, that.name));
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, seconds);
    }

    @Override
    public String toString() {
        return String.format("(%s, %s)", repr(name), seconds);
    }
}
