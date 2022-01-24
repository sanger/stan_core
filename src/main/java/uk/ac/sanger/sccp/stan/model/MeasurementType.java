package uk.ac.sanger.sccp.stan.model;

/**
 * @author dr6
 */
public enum MeasurementType {
    Thickness(MeasurementValueType.INT, "μm"),
    Haematoxylin(MeasurementValueType.TIME),
    Eosin(MeasurementValueType.TIME),
    Blueing(MeasurementValueType.TIME),
    Concentration(MeasurementValueType.DECIMAL, "ng/μL"),
    Permabilisation_time(MeasurementValueType.TIME),
    Selected_time(MeasurementValueType.TIME),
    DV200(MeasurementValueType.DECIMAL, "%"),
    ;

    private final MeasurementValueType valueType;
    private final String unit;

    MeasurementType(MeasurementValueType valueType, String unit) {
        this.valueType = valueType;
        this.unit = unit;
    }

    MeasurementType(MeasurementValueType valueType) {
        this(valueType, null);
    }

    public MeasurementValueType getValueType() {
        return this.valueType;
    }

    public String getUnit() {
        return this.unit;
    }

    public static MeasurementType forName(String name) {
        if (name!=null) {
            name = name.replace(' ','_');
            for (MeasurementType mt : values()) {
                if (mt.name().equalsIgnoreCase(name)) {
                    return mt;
                }
            }
        }
        return null;
    }
}
