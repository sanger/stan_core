package uk.ac.sanger.sccp.stan.model;

/**
 * @author dr6
 */
public enum MeasurementType {
    Thickness(MeasurementValueType.INT),
    Haematoxylin(MeasurementValueType.TIME),
    Eosin(MeasurementValueType.TIME),
    Blueing(MeasurementValueType.TIME);

    private final MeasurementValueType valueType;

    MeasurementType(MeasurementValueType valueType) {
        this.valueType = valueType;
    }

    public MeasurementValueType getValueType() {
        return this.valueType;
    }

    public static MeasurementType forName(String name) {
        if (name==null) {
            return null;
        }
        for (MeasurementType mt : values()) {
            if (mt.name().equalsIgnoreCase(name)) {
                return mt;
            }
        }
        return null;
    }
}
