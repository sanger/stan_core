package uk.ac.sanger.sccp.stan.service.releasefile;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.Objects;

/**
 * A helper data type used to return information about releases to be put into a file
 * @author dr6
 */
public class ReleaseEntry {
    private final Labware labware;
    private final Slot slot;
    private final Sample sample;
    private Integer lastSection;
    private String sourceBarcode;
    private String sectionThickness;
    private Address sourceAddress;
    private Address storageAddress;
    private String stainType;
    private String bondBarcode;
    private Integer coverage;

    public ReleaseEntry(Labware labware, Slot slot, Sample sample) {
        this(labware, slot, sample, null);
    }

    public ReleaseEntry(Labware labware, Slot slot, Sample sample, Address storageAddress) {
        this.labware = labware;
        this.slot = slot;
        this.sample = sample;
        this.storageAddress = storageAddress;
    }

    public Labware getLabware() {
        return this.labware;
    }

    public Slot getSlot() {
        return this.slot;
    }

    public Sample getSample() {
        return this.sample;
    }

    public Integer getLastSection() {
        return this.lastSection;
    }

    public void setLastSection(Integer lastSection) {
        this.lastSection = lastSection;
    }

    public void setSourceBarcode(String sourceBarcode) {
        this.sourceBarcode = sourceBarcode;
    }

    public String getSourceBarcode() {
        return this.sourceBarcode;
    }

    public String getSectionThickness() {
        return this.sectionThickness;
    }

    public void setSectionThickness(String sectionThickness) {
        this.sectionThickness = sectionThickness;
    }

    public Address getSourceAddress() {
        return this.sourceAddress;
    }

    public void setSourceAddress(Address sourceAddress) {
        this.sourceAddress = sourceAddress;
    }

    public Address getStorageAddress() {
        return this.storageAddress;
    }

    public void setStorageAddress(Address storageAddress) {
        this.storageAddress = storageAddress;
    }

    public String getStainType() {
        return this.stainType;
    }

    public void setStainType(String stainType) {
        this.stainType = stainType;
    }

    public String getBondBarcode() {
        return this.bondBarcode;
    }

    public void setBondBarcode(String bondBarcode) {
        this.bondBarcode = bondBarcode;
    }

    public Integer getCoverage() {
        return this.coverage;
    }

    public void setCoverage(Integer coverage) {
        this.coverage = coverage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseEntry that = (ReleaseEntry) o;
        return (Objects.equals(this.labware, that.labware)
                && Objects.equals(this.slot, that.slot)
                && Objects.equals(this.sample, that.sample)
                && Objects.equals(this.lastSection, that.lastSection)
                && Objects.equals(this.sourceBarcode, that.sourceBarcode)
                && Objects.equals(this.sourceAddress, that.sourceAddress)
                && Objects.equals(this.sectionThickness, that.sectionThickness)
                && Objects.equals(this.storageAddress, that.storageAddress)
                && Objects.equals(this.stainType, that.stainType)
                && Objects.equals(this.bondBarcode, that.bondBarcode)
                && Objects.equals(this.coverage, that.coverage));
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, sample);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ReleaseEntry")
                .add("labware", labware==null ? null : labware.getBarcode())
                .add("sample", sample==null ? null : sample.getId())
                .add("lastSection", lastSection)
                .add("sourceBarcode", sourceBarcode)
                .add("sourceAddress", sourceAddress)
                .add("sectionThickness", sectionThickness)
                .add("storageAddress", storageAddress)
                .add("stainType", stainType)
                .add("bondBarcode", bondBarcode)
                .add("coverage", coverage)
                .toString();
    }
}
