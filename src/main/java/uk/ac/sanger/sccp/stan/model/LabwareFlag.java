package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class LabwareFlag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    private Labware labware;

    private String description;
    @ManyToOne
    private User user;
    private Integer operationId;

    public LabwareFlag(Integer id, Labware labware, String description, User user, Integer operationId) {
        this.id = id;
        this.labware = labware;
        this.description = description;
        this.user = user;
        this.operationId = operationId;
    }

    public LabwareFlag() {}

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Labware getLabware() {
        return this.labware;
    }

    public void setLabware(Labware labware) {
        this.labware = labware;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabwareFlag that = (LabwareFlag) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.labware, that.labware)
                && Objects.equals(this.description, that.description)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.operationId, that.operationId));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : Objects.hash(labware, description));
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("id", id)
                .add("labware", labware==null ? null : labware.getBarcode())
                .addRepr("description", description)
                .add("user", user==null ? null : user.getUsername())
                .add("operationId", operationId)
                .toString();
    }
}
