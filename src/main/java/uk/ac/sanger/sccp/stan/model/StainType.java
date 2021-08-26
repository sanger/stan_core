package uk.ac.sanger.sccp.stan.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.List;
import java.util.Objects;

/**
 * A type of stain
 * @author dr6
 */
@Entity
public class StainType implements HasEnabled {
    /** The measurements supported for the {@code H&E} stain type. */
    public static final List<String> H_AND_E_MEASUREMENTS = List.of("Haematoxylin", "Eosin", "Blueing");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private boolean enabled = true;

    public StainType() {}

    public StainType(Integer id, String name, boolean enabled) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
    }

    public StainType(Integer id, String name) {
        this(id, name, true);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @JsonIgnore
    public List<String> getMeasurementTypes() {
        if (name!=null && name.equalsIgnoreCase("H&E")) {
            return H_AND_E_MEASUREMENTS;
        }
        return List.of();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StainType that = (StainType) o;
        return (this.enabled == that.enabled
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : name!=null ? name.hashCode() : 0);
    }

    @Override
    public String toString() {
        return getName();
    }
}
