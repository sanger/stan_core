package uk.ac.sanger.sccp.stan.request.stain;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;

/**
 * A request to perform a stain operation
 * @author dr6
 */
public class StainRequest {
    private String stainType;
    private List<String> barcodes;
    private List<TimeMeasurement> timeMeasurements;
    private String workNumber;

    public StainRequest() {}

    public StainRequest(String stainType, List<String> barcodes, List<TimeMeasurement> timeMeasurements) {
        this.stainType = stainType;
        this.barcodes = barcodes;
        this.timeMeasurements = timeMeasurements;
    }

    public String getStainType() {
        return this.stainType;
    }

    public void setStainType(String stainType) {
        this.stainType = stainType;
    }

    public List<String> getBarcodes() {
        return this.barcodes;
    }

    public void setBarcodes(List<String> barcodes) {
        this.barcodes = barcodes;
    }

    public List<TimeMeasurement> getTimeMeasurements() {
        return this.timeMeasurements;
    }

    public void setTimeMeasurements(List<TimeMeasurement> timeMeasurements) {
        this.timeMeasurements = timeMeasurements;
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StainRequest that = (StainRequest) o;
        return (Objects.equals(this.stainType, that.stainType)
                && Objects.equals(this.barcodes, that.barcodes)
                && Objects.equals(this.timeMeasurements, that.timeMeasurements)
                && Objects.equals(this.workNumber, that.workNumber));
    }

    @Override
    public int hashCode() {
        return Objects.hash(stainType, barcodes, timeMeasurements, workNumber);
    }

    @Override
    public String toString() {
        return describe(this)
                .add("stainType", stainType)
                .add("barcodes", barcodes)
                .add("timeMeasurements", timeMeasurements)
                .add("workNumber", workNumber)
                .toString();
    }
}
