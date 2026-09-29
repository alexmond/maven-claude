package org.alexmond.maven.claude.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurefireReportsTest {

	@TempDir
	Path root;

	private Path reports;

	private SourceIndex index;

	@BeforeEach
	void setUp() throws IOException {
		Path main = this.root.resolve("src/main/java/demo");
		Path test = this.root.resolve("src/test/java/demo");
		Files.createDirectories(main);
		Files.createDirectories(test);
		Files.writeString(main.resolve("Calc.java"), "class Calc {}");
		Files.writeString(test.resolve("CalcTest.java"), "class CalcTest {}");
		this.reports = Files.createDirectories(this.root.resolve("target/surefire-reports"));
		try (var in = getClass().getResourceAsStream("/surefire/TEST-demo.CalcTest.xml")) {
			Files.copy(in, this.reports.resolve("TEST-demo.CalcTest.xml"));
		}
		this.index = new SourceIndex(List.of(this.root.resolve("src/main/java"), this.root.resolve("src/test/java")),
				this.root);
	}

	@Test
	void readsTotalsAndFailures() {
		SurefireReports.Results results = SurefireReports.read(this.reports, 0);
		assertEquals(3, results.tests());
		assertEquals(1, results.failures());
		assertEquals(1, results.errors());
		assertEquals(2, results.failed().size());
		assertTrue(results.failed().get(0).error());
		assertFalse(results.failed().get(1).error());
	}

	@Test
	void ignoresReportsOlderThanTheBuild() throws IOException {
		Path report = this.reports.resolve("TEST-demo.CalcTest.xml");
		Files.setLastModifiedTime(report, FileTime.fromMillis(1_000));
		assertEquals(0, SurefireReports.read(this.reports, 2_000).tests());
	}

	@Test
	void missingDirectoryReadsAsEmpty() {
		assertEquals(0, SurefireReports.read(this.root.resolve("nope"), 0).tests());
	}

	@Test
	void errorKeepsProjectFramesAndRootCause() {
		SurefireReports.Failure failure = SurefireReports.read(this.reports, 0).failed().get(0);
		Problem problem = SurefireReports.toProblem(failure, this.index, "demo");
		assertEquals(Problem.Kind.TEST, problem.kind());
		assertEquals("CalcTest.rejectsNegative", problem.title());
		assertEquals("src/test/java/demo/CalcTest.java:16", problem.location());
		assertEquals(List.of("IllegalStateException: negative input: -1",
				"  at Calc.helper(src/main/java/demo/Calc.java:11)", "  at Calc.add(src/main/java/demo/Calc.java:6)",
				"  at CalcTest.rejectsNegative(src/test/java/demo/CalcTest.java:16)",
				"  at Calc.helper(src/main/java/demo/Calc.java:10)", "Caused by: UncheckedIOException: disk full"),
				problem.detail());
	}

	@Test
	void assertionFailureDropsFrameworkFramesAndModulePrefix() {
		SurefireReports.Failure failure = SurefireReports.read(this.reports, 0).failed().get(1);
		Problem problem = SurefireReports.toProblem(failure, this.index, "demo");
		assertEquals("src/test/java/demo/CalcTest.java:11", problem.location());
		assertEquals(List.of("AssertionFailedError: expected: <4> but was: <5>",
				"  at CalcTest.addsNumbers(src/test/java/demo/CalcTest.java:11)"), problem.detail());
	}

	@Test
	void failureWithoutStackUsesTypeAndMessage() {
		SurefireReports.Failure failure = new SurefireReports.Failure("demo.CalcTest", "timesOut", true,
				"java.util.concurrent.TimeoutException", "took too long", "");
		Problem problem = SurefireReports.toProblem(failure, this.index, "demo");
		assertNull(problem.location());
		assertEquals(List.of("TimeoutException: took too long"), problem.detail());
	}

}
