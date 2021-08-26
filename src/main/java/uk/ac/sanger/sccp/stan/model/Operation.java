package uk.ac.sanger.sccp.stan.model;

import org.hibernate.annotations.*;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.Entity;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
@DynamicInsert
public class Operation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    private OperationType operationType;

    @Generated(GenerationTime.INSERT)
    private LocalDateTime performed;

    @OneToMany
    @JoinColumn(name="operation_id")
    private List<Action> actions;

    @ManyToOne
    private User user;

    private Integer planOperationId;

    @ManyToOne
    @JoinTable(name="stain",
            joinColumns = @JoinColumn(name="operation_id"),
            inverseJoinColumns = @JoinColumn(name="stain_type_id"))
    private StainType stainType;

    public Operation() {}

    public Operation(Integer id, OperationType operationType, LocalDateTime performed, List<Action> actions, User user,
                     Integer planOperationId) {
        this.id = id;
        this.operationType = operationType;
        this.performed = performed;
        this.actions = actions;
        this.user = user;
        this.planOperationId = planOperationId;
    }

    public Operation(Integer id, OperationType operationType, LocalDateTime performed, List<Action> actions, User user) {
        this(id, operationType, performed, actions, user, null);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDateTime getPerformed() {
        return this.performed;
    }

    public void setPerformed(LocalDateTime performed) {
        this.performed = performed;
    }

    public OperationType getOperationType() {
        return this.operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public List<Action> getActions() {
        return this.actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Integer getPlanOperationId() {
        return this.planOperationId;
    }

    public void setPlanOperationId(Integer planOperationId) {
        this.planOperationId = planOperationId;
    }

    public StainType getStainType() {
        return this.stainType;
    }

    public void setStainType(StainType stainType) {
        this.stainType = stainType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Operation that = (Operation) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.performed, that.performed)
                && Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.user, that.user)
                && Objects.equals(this.actions, that.actions)
                && Objects.equals(this.planOperationId, that.planOperationId)
                && Objects.equals(this.stainType, that.stainType));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("id", id)
                .add("performed", performed)
                .add("operationType", operationType)
                .addIfNotNull("stainType", stainType)
                .toString();
    }
}
