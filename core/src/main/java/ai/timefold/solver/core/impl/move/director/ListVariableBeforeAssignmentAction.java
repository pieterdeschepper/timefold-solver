package ai.timefold.solver.core.impl.move.director;

import ai.timefold.solver.core.api.move.Rebaser;
import ai.timefold.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.timefold.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

record ListVariableBeforeAssignmentAction<Solution_>(Object element,
        ListVariableDescriptor<Solution_> variableDescriptor) implements ChangeAction<Solution_> {

    @Override
    public void undo(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
        scoreDirector.afterListVariableElementUnassigned(variableDescriptor, element);
    }

    @Override
    public ChangeAction<Solution_> rebase(Rebaser rebaser) {
        return new ListVariableBeforeAssignmentAction<>(rebaser.rebase(element), variableDescriptor);
    }

}