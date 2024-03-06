package ai.timefold.solver.examples.vehiclerouting.domain;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.entity.PlanningPinToIndex;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import ai.timefold.solver.examples.common.domain.AbstractPersistable;
import ai.timefold.solver.examples.common.persistence.jackson.JacksonUniqueIdGenerator;
import ai.timefold.solver.examples.vehiclerouting.domain.location.Location;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;

@PlanningEntity
@JsonIdentityInfo(generator = JacksonUniqueIdGenerator.class)
public class Vehicle extends AbstractPersistable implements LocationAware {

    protected int capacity;
    protected Depot depot;

    @PlanningListVariable
    protected List<Customer> customers = new ArrayList<>();

    @PlanningPin
    public boolean pinned;

    @PlanningPinToIndex
    public int pinnedToIndex;

    public Vehicle() {
    }

    public Vehicle(long id, int capacity, Depot depot) {
        super(id);
        this.capacity = capacity;
        this.depot = depot;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public Depot getDepot() {
        return depot;
    }

    public void setDepot(Depot depot) {
        this.depot = depot;
    }

    public List<Customer> getCustomers() {
        return customers;
    }

    public void setCustomers(List<Customer> customers) {
        this.customers = customers;
    }

    public boolean isPinned() {
        return pinned;
    }

    public int getPinnedToIndex() {
        return pinnedToIndex;
    }

    // ************************************************************************
    // Complex methods
    // ************************************************************************

    @Override
    @JsonIgnore
    public Location getLocation() {
        return depot.getLocation();
    }

    @Override
    public String toString() {
        Location location = getLocation();
        if (location.getName() == null) {
            return super.toString();
        }
        return location.getName() + "/" + super.toString();
    }
}
