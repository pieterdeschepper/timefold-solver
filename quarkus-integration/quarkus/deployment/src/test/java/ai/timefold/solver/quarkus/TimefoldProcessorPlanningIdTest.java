package ai.timefold.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.inject.Inject;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.quarkus.testdata.superclass.constraints.DummyConstraintProvider;
import ai.timefold.solver.quarkus.testdata.superclass.domain.TestdataEntity;
import ai.timefold.solver.quarkus.testdata.superclass.domain.TestdataSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class TimefoldProcessorPlanningIdTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.timefold.solver.termination.best-score-limit", "0")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage("ai.timefold.solver.quarkus.testdata.superclass.domain") // Cannot reference a non-public class.
                    .addClasses(DummyConstraintProvider.class));

    @Inject
    SolverFactory<TestdataSolution> solverFactory;

    @Test
    void buildSolver() {
        TestdataSolution problem = new TestdataSolution();
        problem.setValueList(IntStream.range(1, 3)
                .mapToObj(i -> "v" + i)
                .collect(Collectors.toList()));
        problem.setEntityList(IntStream.range(1, 3)
                .mapToObj(i -> new TestdataEntity(i))
                .collect(Collectors.toList()));

        TestdataSolution solution = solverFactory.buildSolver().solve(problem);
        assertNotNull(solution);
    }
}
