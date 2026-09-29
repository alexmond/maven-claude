package org.alexmond.maven.claude.output;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryRendererTest {

	@Test
	void rendersFailureWithProblemsAndFooter() {
		BuildReport report = new BuildReport(false, 41_230, 3, 1, 1, 1, new BuildReport.Tests(216, 2, 0, 3),
				List.of(new Problem(Problem.Kind.COMPILE, "core", "cannot find symbol", "core/src/A.java:88:17",
						List.of("symbol:   method total()")),
						new Problem(Problem.Kind.TEST, "core", "ATest.works", "core/src/ATest.java:42",
								List.of("AssertionFailedError: expected: <1> but was: <2>"))),
				null);
		String expected = """
				BUILD FAILED in 41.2s · 3 modules: 1 ok, 1 failed, 1 skipped · tests: 216 run, 2 failed, 3 skipped

				COMPILE  core/src/A.java:88:17
				         cannot find symbol
				         symbol:   method total()

				TEST     ATest.works  core/src/ATest.java:42
				         AssertionFailedError: expected: <1> but was: <2>

				Full log: target/claude/build.log · JSON: target/claude/build-report.json
				""";
		assertEquals(expected,
				SummaryRenderer.render(report, "target/claude/build.log", "target/claude/build-report.json", 20));
	}

	@Test
	void successIsOneLine() {
		BuildReport report = new BuildReport(true, 3_100, 1, 1, 0, 0, new BuildReport.Tests(12, 0, 0, 1), List.of(),
				null);
		assertEquals("BUILD OK in 3.1s · tests: 11 passed, 1 skipped\n",
				SummaryRenderer.render(report, null, null, 20));
	}

	@Test
	void multiModuleSuccessOmitsBreakdown() {
		BuildReport report = new BuildReport(true, 125_000, 4, 4, 0, 0, null, List.of(), "a note");
		assertEquals("BUILD OK in 2m 05s · 4 modules\n\na note\n", SummaryRenderer.render(report, null, null, 20));
	}

	@Test
	void collapsesProblemsBeyondTheLimit() {
		Problem problem = new Problem(Problem.Kind.BUILD, null, "boom", null, List.of());
		BuildReport report = new BuildReport(false, 10, 1, 0, 1, 0, new BuildReport.Tests(1, 0, 1, 0),
				List.of(problem, problem, problem), null);
		String summary = SummaryRenderer.render(report, null, null, 1);
		assertTrue(summary.startsWith("BUILD FAILED in 0.0s · tests: 1 run, 1 errored\n\nERROR    boom\n"), summary);
		assertTrue(summary.contains("... 2 more problems in the JSON report"), summary);
	}

}
