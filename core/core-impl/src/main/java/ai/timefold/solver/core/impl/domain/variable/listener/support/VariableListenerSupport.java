package ai.timefold.solver.core.impl.domain.variable.listener.support;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.timefold.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.timefold.solver.core.impl.domain.variable.descriptor.ShadowVariableDescriptor;
import ai.timefold.solver.core.impl.domain.variable.descriptor.VariableDescriptor;
import ai.timefold.solver.core.impl.domain.variable.listener.SourcedVariableListener;
import ai.timefold.solver.core.impl.domain.variable.listener.support.violation.ShadowVariablesAssert;
import ai.timefold.solver.core.impl.domain.variable.supply.Demand;
import ai.timefold.solver.core.impl.domain.variable.supply.Supply;
import ai.timefold.solver.core.impl.domain.variable.supply.SupplyManager;
import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;

/**
 * This class is not thread-safe.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public final class VariableListenerSupport<Solution_> implements SupplyManager {

    public static <Solution_> VariableListenerSupport<Solution_> create(InnerScoreDirector<Solution_, ?> scoreDirector) {
        return new VariableListenerSupport<>(scoreDirector, new NotifiableRegistry<>(scoreDirector.getSolutionDescriptor()));
    }

    private final InnerScoreDirector<Solution_, ?> scoreDirector;
    private final NotifiableRegistry<Solution_> notifiableRegistry;
    private final Map<Demand<?>, SupplyWithDemandCount> supplyMap = new HashMap<>();

    private boolean notificationQueuesAreEmpty = true;
    private int nextGlobalOrder = 0;

    VariableListenerSupport(InnerScoreDirector<Solution_, ?> scoreDirector, NotifiableRegistry<Solution_> notifiableRegistry) {
        this.scoreDirector = scoreDirector;
        this.notifiableRegistry = notifiableRegistry;
    }

    public void linkVariableListeners() {
        scoreDirector.getSolutionDescriptor().getEntityDescriptors().stream()
                .map(EntityDescriptor::getDeclaredShadowVariableDescriptors)
                .flatMap(Collection::stream)
                .filter(ShadowVariableDescriptor::hasVariableListener)
                .sorted(Comparator.comparingInt(ShadowVariableDescriptor::getGlobalShadowOrder))
                .forEach(this::processShadowVariableDescriptor);
    }

    private void processShadowVariableDescriptor(ShadowVariableDescriptor<Solution_> shadowVariableDescriptor) {
        for (var listenerWithSources : shadowVariableDescriptor.buildVariableListeners(this)) {
            var variableListener = listenerWithSources.getVariableListener();
            if (variableListener instanceof Supply supply) {
                // Non-sourced variable listeners (ie. ones provided by the user) can never be a supply.
                var demand = shadowVariableDescriptor.getProvidedDemand();
                supplyMap.put(demand, new SupplyWithDemandCount(supply, 1L));
            }
            var globalOrder = shadowVariableDescriptor.getGlobalShadowOrder();
            notifiableRegistry.registerNotifiable(
                    listenerWithSources.getSourceVariableDescriptors(),
                    AbstractNotifiable.buildNotifiable(scoreDirector, variableListener, globalOrder));
            nextGlobalOrder = globalOrder + 1;
        }
    }

    @Override
    public <Supply_ extends Supply> Supply_ demand(Demand<Supply_> demand) {
        var supplyWithDemandCount = supplyMap.get(demand);
        if (supplyWithDemandCount == null) {
            var newSupplyWithDemandCount = new SupplyWithDemandCount(createSupply(demand), 1L);
            supplyMap.put(demand, newSupplyWithDemandCount);
            return (Supply_) newSupplyWithDemandCount.supply;
        } else {
            var supply = supplyWithDemandCount.supply;
            var newSupplyWithDemandCount = new SupplyWithDemandCount(supply, supplyWithDemandCount.demandCount + 1L);
            supplyMap.put(demand, newSupplyWithDemandCount);
            return (Supply_) supply;
        }
    }

    private Supply createSupply(Demand<?> demand) {
        var supply = demand.createExternalizedSupply(this);
        if (supply instanceof SourcedVariableListener) {
            var variableListener = (SourcedVariableListener<Solution_>) supply;
            // An external ScoreDirector can be created before the working solution is set
            if (scoreDirector.getWorkingSolution() != null) {
                variableListener.resetWorkingSolution(scoreDirector);
            }
            notifiableRegistry.registerNotifiable(
                    variableListener.getSourceVariableDescriptor(),
                    AbstractNotifiable.buildNotifiable(scoreDirector, variableListener, nextGlobalOrder++));
        }
        return supply;
    }

    @Override
    public <Supply_ extends Supply> boolean cancel(Demand<Supply_> demand) {
        var supplyWithDemandCount = supplyMap.get(demand);
        if (supplyWithDemandCount == null) {
            return false;
        }
        if (supplyWithDemandCount.demandCount == 1L) {
            supplyMap.remove(demand);
        } else {
            supplyMap.put(demand,
                    new SupplyWithDemandCount(supplyWithDemandCount.supply, supplyWithDemandCount.demandCount - 1L));
        }
        return true;
    }

    @Override
    public <Supply_ extends Supply> long getActiveCount(Demand<Supply_> demand) {
        var supplyAndDemandCounter = supplyMap.get(demand);
        if (supplyAndDemandCounter == null) {
            return 0L;
        } else {
            return supplyAndDemandCounter.demandCount;
        }
    }

    // ************************************************************************
    // Lifecycle methods
    // ************************************************************************

    public void resetWorkingSolution() {
        for (Notifiable notifiable : notifiableRegistry.getAll()) {
            notifiable.resetWorkingSolution();
        }
    }

    public void close() {
        for (Notifiable notifiable : notifiableRegistry.getAll()) {
            notifiable.closeVariableListener();
        }
    }

    public void beforeEntityAdded(EntityDescriptor<Solution_> entityDescriptor, Object entity) {
        Collection<EntityNotifiable<Solution_>> notifiables = notifiableRegistry.get(entityDescriptor);
        if (!notifiables.isEmpty()) {
            EntityNotification<Solution_> notification = Notification.entityAdded(entity);
            for (EntityNotifiable<Solution_> notifiable : notifiables) {
                notifiable.notifyBefore(notification);
            }
            notificationQueuesAreEmpty = false;
        }
    }

    public void beforeEntityRemoved(EntityDescriptor<Solution_> entityDescriptor, Object entity) {
        Collection<EntityNotifiable<Solution_>> notifiables = notifiableRegistry.get(entityDescriptor);
        if (!notifiables.isEmpty()) {
            EntityNotification<Solution_> notification = Notification.entityRemoved(entity);
            for (EntityNotifiable<Solution_> notifiable : notifiables) {
                notifiable.notifyBefore(notification);
            }
            notificationQueuesAreEmpty = false;
        }
    }

    public void beforeVariableChanged(VariableDescriptor<Solution_> variableDescriptor, Object entity) {
        Collection<VariableListenerNotifiable<Solution_>> notifiables = notifiableRegistry.get(variableDescriptor);
        if (!notifiables.isEmpty()) {
            BasicVariableNotification<Solution_> notification = Notification.variableChanged(entity);
            for (VariableListenerNotifiable<Solution_> notifiable : notifiables) {
                notifiable.notifyBefore(notification);
            }
            notificationQueuesAreEmpty = false;
        }
    }

    public void afterElementUnassigned(ListVariableDescriptor<Solution_> variableDescriptor, Object element) {
        Collection<ListVariableListenerNotifiable<Solution_>> notifiables = notifiableRegistry.get(variableDescriptor);
        if (!notifiables.isEmpty()) {
            ListVariableNotification<Solution_> notification = Notification.elementUnassigned(element);
            for (ListVariableListenerNotifiable<Solution_> notifiable : notifiables) {
                notifiable.notifyAfter(notification);
            }
            notificationQueuesAreEmpty = false;
        }
    }

    public void beforeListVariableChanged(ListVariableDescriptor<Solution_> variableDescriptor, Object entity, int fromIndex,
            int toIndex) {
        Collection<ListVariableListenerNotifiable<Solution_>> notifiables = notifiableRegistry.get(variableDescriptor);
        if (!notifiables.isEmpty()) {
            ListVariableNotification<Solution_> notification = Notification.listVariableChanged(entity, fromIndex, toIndex);
            for (ListVariableListenerNotifiable<Solution_> notifiable : notifiables) {
                notifiable.notifyBefore(notification);
            }
            notificationQueuesAreEmpty = false;
        }
    }

    public void afterListVariableChanged(ListVariableDescriptor<Solution_> variableDescriptor, Object entity, int fromIndex,
            int toIndex) {
        Collection<ListVariableListenerNotifiable<Solution_>> notifiables = notifiableRegistry.get(variableDescriptor);
        if (!notifiables.isEmpty()) {
            ListVariableNotification<Solution_> notification = Notification.listVariableChanged(entity, fromIndex, toIndex);
            for (ListVariableListenerNotifiable<Solution_> notifiable : notifiables) {
                notifiable.notifyAfter(notification);
            }
            notificationQueuesAreEmpty = false;
        }
    }

    public void triggerVariableListenersInNotificationQueues() {
        for (Notifiable notifiable : notifiableRegistry.getAll()) {
            notifiable.triggerAllNotifications();
        }
        notificationQueuesAreEmpty = true;
    }

    /**
     * @return null if there are no violations
     */
    public String createShadowVariablesViolationMessage() {
        Solution_ workingSolution = scoreDirector.getWorkingSolution();
        ShadowVariablesAssert snapshot =
                ShadowVariablesAssert.takeSnapshot(scoreDirector.getSolutionDescriptor(), workingSolution);

        forceTriggerAllVariableListeners(workingSolution);

        final int SHADOW_VARIABLE_VIOLATION_DISPLAY_LIMIT = 3;
        return snapshot.createShadowVariablesViolationMessage(SHADOW_VARIABLE_VIOLATION_DISPLAY_LIMIT);
    }

    /**
     * Triggers all variable listeners even though the notification queue is empty.
     *
     * <p>
     * To ensure each listener is triggered,
     * an artificial notification is created for each genuine variable without doing any change on the working solution.
     * If everything works correctly,
     * triggering listeners at this point must not change any shadow variables either.
     *
     * @param workingSolution working solution
     */
    public void forceTriggerAllVariableListeners(Solution_ workingSolution) {
        scoreDirector.getSolutionDescriptor().visitAllEntities(workingSolution, this::simulateGenuineVariableChange);
        triggerVariableListenersInNotificationQueues();
    }

    private void simulateGenuineVariableChange(Object entity) {
        var entityDescriptor = scoreDirector.getSolutionDescriptor()
                .findEntityDescriptorOrFail(entity.getClass());
        if (!entityDescriptor.isGenuine()) {
            return;
        }
        for (var variableDescriptor : entityDescriptor.getGenuineVariableDescriptorList()) {
            if (variableDescriptor.isListVariable()) {
                var listVariableDescriptor = (ListVariableDescriptor<Solution_>) variableDescriptor;
                int size = listVariableDescriptor.getValue(entity).size();
                beforeListVariableChanged(listVariableDescriptor, entity, 0, size);
                afterListVariableChanged(listVariableDescriptor, entity, 0, size);
            } else {
                // Triggering before...() is enough, as that will add the after...() call to the queue automatically.
                beforeVariableChanged(variableDescriptor, entity);
            }
        }
    }

    public void assertNotificationQueuesAreEmpty() {
        if (!notificationQueuesAreEmpty) {
            throw new IllegalStateException("The notificationQueues might not be empty (" + notificationQueuesAreEmpty
                    + ") so any shadow variables might be stale so score calculation is unreliable.\n"
                    + "Maybe a " + ScoreDirector.class.getSimpleName() + ".before*() method was called"
                    + " without calling " + ScoreDirector.class.getSimpleName() + ".triggerVariableListeners(),"
                    + " before calling " + ScoreDirector.class.getSimpleName() + ".calculateScore().");
        }
    }

    private record SupplyWithDemandCount(Supply supply, long demandCount) {
    }

}
