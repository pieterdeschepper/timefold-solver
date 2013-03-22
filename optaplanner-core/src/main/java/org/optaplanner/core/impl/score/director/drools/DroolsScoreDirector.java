/*
 * Copyright 2011 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.core.impl.score.director.drools;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.drools.core.ClassObjectFilter;
import org.drools.core.FactHandle;
import org.drools.core.RuleBase;
import org.drools.core.StatefulSession;
import org.drools.core.WorkingMemory;
import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.score.constraint.ConstraintOccurrence;
import org.optaplanner.core.impl.score.director.AbstractScoreDirector;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.core.api.score.holder.ScoreHolder;
import org.optaplanner.core.impl.solution.Solution;

/**
 * Drools implementation of {@link ScoreDirector}, which directs the Rule Engine to calculate the {@link Score}
 * of the {@link Solution} workingSolution.
 * @see ScoreDirector
 */
public class DroolsScoreDirector extends AbstractScoreDirector<DroolsScoreDirectorFactory> {

    public static final String GLOBAL_SCORE_HOLDER_KEY = "scoreHolder";

    protected StatefulSession workingMemory;
    protected ScoreHolder workingScoreHolder;

    public DroolsScoreDirector(DroolsScoreDirectorFactory scoreDirectorFactory) {
        super(scoreDirectorFactory);
    }

    protected RuleBase getRuleBase() {
        return scoreDirectorFactory.getRuleBase();
    }

    /**
     * @return never null
     */
    public WorkingMemory getWorkingMemory() {
        return workingMemory;
    }

    // ************************************************************************
    // Complex methods
    // ************************************************************************

    @Override
    public void setWorkingSolution(Solution workingSolution) {
        super.setWorkingSolution(workingSolution);
        resetWorkingMemory();
    }

    private void resetWorkingMemory() {
        if (workingMemory != null) {
            workingMemory.dispose();
        }
        workingMemory = getRuleBase().newStatefulSession();
        workingScoreHolder = getScoreDefinition().buildScoreHolder();
        workingMemory.setGlobal(GLOBAL_SCORE_HOLDER_KEY, workingScoreHolder);
        // TODO Adjust when uninitialized entities from getWorkingFacts get added automatically too (and call afterEntityAdded)
        Collection<Object> workingFacts = getWorkingFacts();
        for (Object fact : workingFacts) {
            workingMemory.insert(fact);
        }
    }

    public Collection<Object> getWorkingFacts() {
        return getSolutionDescriptor().getAllFacts(workingSolution);
    }

    // public void beforeEntityAdded(Object entity) // Do nothing

    @Override
    public void afterEntityAdded(Object entity) {
        super.afterEntityAdded(entity);
        if (entity == null) {
            throw new IllegalArgumentException("The entity (" + entity + ") cannot be added to the ScoreDirector.");
        }
        if (!getSolutionDescriptor().hasPlanningEntityDescriptor(entity.getClass())) {
            throw new IllegalArgumentException("The entity (" + entity + ") of class (" + entity.getClass()
                    + ") is not a configured @PlanningEntity.");
        }
        workingMemory.insert(entity);
    }

    // public void beforeAllVariablesChanged(Object entity) // Do nothing

    @Override
    public void afterAllVariablesChanged(Object entity) {
        super.afterAllVariablesChanged(entity);
        FactHandle factHandle = workingMemory.getFactHandle(entity);
        if (factHandle == null) {
            throw new IllegalArgumentException("The entity (" + entity + ") was never added to this ScoreDirector.");
        }
        workingMemory.update(factHandle, entity);
    }

    // public void beforeVariableChanged(Object entity, String variableName) // Do nothing

    @Override
    public void afterVariableChanged(Object entity, String variableName) {
        super.afterVariableChanged(entity, variableName);
        FactHandle factHandle = workingMemory.getFactHandle(entity);
        if (factHandle == null) {
            throw new IllegalArgumentException("The entity (" + entity + ") was never added to this ScoreDirector.");
        }
        workingMemory.update(factHandle, entity);
    }

    // public void beforeEntityRemoved(Object entity) // Do nothing

    @Override
    public void afterEntityRemoved(Object entity) {
        super.afterEntityRemoved(entity);
        FactHandle factHandle = workingMemory.getFactHandle(entity);
        if (factHandle == null) {
            throw new IllegalArgumentException("The entity (" + entity + ") was never added to this ScoreDirector.");
        }
        workingMemory.retract(factHandle);
    }

    // public void beforeProblemFactAdded(Object problemFact) // Do nothing

    @Override
    public void afterProblemFactAdded(Object problemFact) {
        super.afterProblemFactAdded(problemFact);
        workingMemory.insert(problemFact);
    }

    // public void beforeProblemFactChanged(Object problemFact) // Do nothing

    @Override
    public void afterProblemFactChanged(Object problemFact) {
        super.afterProblemFactChanged(problemFact);
        FactHandle factHandle = workingMemory.getFactHandle(problemFact);
        if (factHandle == null) {
            throw new IllegalArgumentException("The problemFact (" + problemFact
                    + ") was never added to this ScoreDirector.");
        }
        workingMemory.update(factHandle, problemFact);
    }

    // public void beforeProblemFactRemoved(Object problemFact) // Do nothing

    @Override
    public void afterProblemFactRemoved(Object problemFact) {
        super.afterProblemFactRemoved(problemFact);
        FactHandle factHandle = workingMemory.getFactHandle(problemFact);
        if (factHandle == null) {
            throw new IllegalArgumentException("The problemFact (" + problemFact
                    + ") was never added to this ScoreDirector.");
        }
        workingMemory.retract(factHandle);
    }

    public Score calculateScore() {
        workingMemory.fireAllRules();
        Score score = workingScoreHolder.extractScore();
        setCalculatedScore(score);
        return score;
    }

    @Override
    public AbstractScoreDirector clone() {
        // TODO experiment with serializing the WorkingMemory to clone it and its entities but not its other facts.
        // See drools-compiler's test SerializationHelper.getSerialisedStatefulKnowledgeSession(...)
        // and use an identity FactFactory that:
        // - returns the reference for a non-@PlanningEntity fact
        // - returns a clone for a @PlanningEntity fact (Pitfall: chained planning entities)
        // Note: currently that will break incremental score calculation, but future drools versions might fix that
        return super.clone();
    }

    protected String buildScoreCorruptionAnalysis(ScoreDirector uncorruptedScoreDirector) {
        if (!(uncorruptedScoreDirector instanceof DroolsScoreDirector)) {
            return "Unable to analyze: the uncorruptedScoreDirector class (" + uncorruptedScoreDirector.getClass()
                    + ") is not an instance of the scoreDirector class (" + DroolsScoreDirector.class + ").";
        }
        DroolsScoreDirector uncorruptedDroolsScoreDirector = (DroolsScoreDirector) uncorruptedScoreDirector;
        Set<ConstraintOccurrence> workingConstraintOccurrenceSet = new LinkedHashSet<ConstraintOccurrence>();
        Iterator<ConstraintOccurrence> workingIt = (Iterator<ConstraintOccurrence>)
                workingMemory.iterateObjects(
                new ClassObjectFilter(ConstraintOccurrence.class));
        while (workingIt.hasNext()) {
            workingConstraintOccurrenceSet.add(workingIt.next());
        }
        Set<ConstraintOccurrence> uncorruptedConstraintOccurrenceSet = new LinkedHashSet<ConstraintOccurrence>();
        Iterator<ConstraintOccurrence> uncorruptedIt = (Iterator<ConstraintOccurrence>)
                uncorruptedDroolsScoreDirector.getWorkingMemory().iterateObjects(
                        new ClassObjectFilter(ConstraintOccurrence.class));
        while (uncorruptedIt.hasNext()) {
            uncorruptedConstraintOccurrenceSet.add(uncorruptedIt.next());
        };
        Set<Object> excessSet = new LinkedHashSet<Object>(workingConstraintOccurrenceSet);
        excessSet.removeAll(uncorruptedConstraintOccurrenceSet);
        Set<Object> lackingSet = new LinkedHashSet<Object>(uncorruptedConstraintOccurrenceSet);
        lackingSet.removeAll(workingConstraintOccurrenceSet);

        int CONSTRAINT_OCCURRENCE_DISPLAY_LIMIT = 10;
        StringBuilder analysis = new StringBuilder();
        if (!excessSet.isEmpty()) {
            analysis.append("  The workingMemory has ").append(excessSet.size())
                    .append(" ConstraintOccurrence(s) in excess:\n");
            int count = 0;
            for (Object o : excessSet) {
                if (count >= CONSTRAINT_OCCURRENCE_DISPLAY_LIMIT) {
                    analysis.append("    ... ").append(excessSet.size() - CONSTRAINT_OCCURRENCE_DISPLAY_LIMIT)
                            .append(" more\n");
                    break;
                }
                analysis.append("    ").append(o.toString()).append("\n");
                count++;
            }
        }
        if (!lackingSet.isEmpty()) {
            analysis.append("  The workingMemory has ").append(lackingSet.size())
                    .append(" ConstraintOccurrence(s) lacking:\n");
            int count = 0;
            for (Object o : lackingSet) {
                if (count >= CONSTRAINT_OCCURRENCE_DISPLAY_LIMIT) {
                    analysis.append("    ... ").append(lackingSet.size() - CONSTRAINT_OCCURRENCE_DISPLAY_LIMIT)
                            .append(" more\n");
                    break;
                }
                analysis.append("    ").append(o.toString()).append("\n");
                count++;
            }
        }
        if (excessSet.isEmpty() && lackingSet.isEmpty()) {
            analysis.append("  Check the score rules. No ConstraintOccurrence(s) in excess or lacking." +
                    "  Possibly some logically inserted score rules do not extend ConstraintOccurrence.\n" +
                    "  Consider making them extend ConstraintOccurrence" +
                    " or just reuse the build-in ConstraintOccurrence implementations.");

        } else {
            analysis.append("  Check the score rules who created those ConstraintOccurrences." +
                    " Verify that each ConstraintOccurrence's causes and weight is correct.");
        }
        return analysis.toString();
    }

    @Override
    public void dispose() {
        if (workingMemory != null) {
            workingMemory.dispose();
            workingMemory = null;
        }
    }

}
