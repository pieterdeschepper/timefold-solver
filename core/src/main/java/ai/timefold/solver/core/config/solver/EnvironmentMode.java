package ai.timefold.solver.core.config.solver;

import java.util.Random;

import jakarta.xml.bind.annotation.XmlEnum;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;

/**
 * The environment mode also allows you to detect common bugs in your implementation.
 * <p>
 * Also, a {@link Solver} has a single {@link Random} instance.
 * Some optimization algorithms use the {@link Random} instance a lot more than others.
 * For example simulated annealing depends highly on random numbers,
 * while tabu search only depends on it to deal with score ties.
 * This environment mode influences the seed of that {@link Random} instance.
 */
@XmlEnum
public enum EnvironmentMode {
    /**
     * This mode turns on {@link #FULL_ASSERT} and enables variable tracking to fail-fast on a bug in a {@link Move}
     * implementation,
     * a constraint, the engine itself or something else at the highest performance cost.
     * <p>
     * Because it tracks genuine and shadow variables, it is able to report precisely what variables caused the corruption and
     * report any missed {@link ai.timefold.solver.core.api.domain.variable.VariableListener} events.
     * <p>
     * This mode is reproducible (see {@link #REPRODUCIBLE} mode).
     * <p>
     * This mode is intrusive because it calls the {@link InnerScoreDirector#calculateScore()} more frequently than a non assert
     * mode.
     * <p>
     * This mode is by far the slowest of all the modes.
     */
    TRACKED_FULL_ASSERT(true),

    /**
     * This mode turns on all assertions
     * to fail-fast on a bug in a {@link Move} implementation, a constraint, the engine itself or something else
     * at a horrible performance cost.
     * <p>
     * This mode is reproducible (see {@link #REPRODUCIBLE} mode).
     * <p>
     * This mode is intrusive because it calls the {@link InnerScoreDirector#calculateScore()} more frequently
     * than a non assert mode.
     * <p>
     * This mode is horribly slow.
     */
    FULL_ASSERT(true),
    /**
     * This mode turns on several assertions (but not all of them)
     * to fail-fast on a bug in a {@link Move} implementation, a constraint, the engine itself or something else
     * at an overwhelming performance cost.
     * <p>
     * This mode is reproducible (see {@link #REPRODUCIBLE} mode).
     * <p>
     * This mode is non-intrusive, unlike {@link #FULL_ASSERT} and {@link #FAST_ASSERT}.
     * <p>
     * This mode is horribly slow.
     */
    NON_INTRUSIVE_FULL_ASSERT(true),
    /**
     * This mode turns on several assertions (but not all of them)
     * to fail-fast on a bug in a {@link Move} implementation, a constraint rule, the engine itself or something else
     * at a reasonable performance cost (in development at least).
     * <p>
     * This mode is reproducible (see {@link #REPRODUCIBLE} mode).
     * <p>
     * This mode is intrusive because it calls the {@link InnerScoreDirector#calculateScore()} more frequently
     * than a non assert mode.
     * <p>
     * This mode is slow.
     */
    FAST_ASSERT(true),
    /**
     * The reproducible mode is the default mode because it is recommended during development.
     * In this mode, 2 runs on the same computer will execute the same code in the same order.
     * They will also yield the same result, except if they use a time based termination
     * and they have a sufficiently large difference in allocated CPU time.
     * This allows you to benchmark new optimizations (such as a new {@link Move} implementation) fairly
     * and reproduce bugs in your code reliably.
     * <p>
     * Warning: some code can disrupt reproducibility regardless of this mode. See the reference manual for more info.
     * <p>
     * In practice, this mode uses the default random seed,
     * and it also disables certain concurrency optimizations (such as work stealing).
     */
    REPRODUCIBLE(false),
    /**
     * The non reproducible mode is equally fast or slightly faster than {@link #REPRODUCIBLE}.
     * <p>
     * The random seed is different on every run, which makes it more robust against an unlucky random seed.
     * An unlucky random seed gives a bad result on a certain data set with a certain solver configuration.
     * Note that in most use cases the impact of the random seed is relatively low on the result.
     * An occasional bad result is far more likely to be caused by another issue (such as a score trap).
     * <p>
     * In multithreaded scenarios, this mode allows the use of work stealing and other non deterministic speed tricks.
     */
    NON_REPRODUCIBLE(false);

    private final boolean asserted;

    EnvironmentMode(boolean asserted) {
        this.asserted = asserted;
    }

    public boolean isAsserted() {
        return asserted;
    }

    public boolean isNonIntrusiveFullAsserted() {
        if (!isAsserted()) {
            return false;
        }
        return this != FAST_ASSERT;
    }

    public boolean isIntrusiveFastAsserted() {
        if (!isAsserted()) {
            return false;
        }
        return this != NON_INTRUSIVE_FULL_ASSERT;
    }

    public boolean isReproducible() {
        return this != NON_REPRODUCIBLE;
    }

    public boolean isTracking() {
        return this == TRACKED_FULL_ASSERT;
    }
}
