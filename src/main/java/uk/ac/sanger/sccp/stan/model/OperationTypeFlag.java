package uk.ac.sanger.sccp.stan.model;

public enum OperationTypeFlag {
    IN_PLACE,
    SOURCE_IS_BLOCK,
    DISCARD_SOURCE,
    STAIN,
    ;

    public int bit() {
        return (1 << this.ordinal());
    }
}
