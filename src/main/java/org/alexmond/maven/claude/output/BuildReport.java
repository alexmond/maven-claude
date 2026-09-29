package org.alexmond.maven.claude.output;

import java.util.List;

/**
 * Everything the summary and {@code build-report.json} say about one build.
 *
 * @param success whether Maven reported success
 * @param durationMillis wall time of the build
 * @param modules number of modules in the reactor
 * @param modulesOk modules built successfully
 * @param modulesFailed modules that failed
 * @param modulesSkipped modules not built (skipped after an earlier failure)
 * @param tests test totals across surefire and failsafe, or {@code null} when no tests
 * ran
 * @param problems what went wrong, in build order
 * @param note an optional remark about the report itself (e.g. why output was not
 * condensed), or {@code null}
 */
public record BuildReport(boolean success, long durationMillis, int modules, int modulesOk, int modulesFailed,
		int modulesSkipped, Tests tests, List<Problem> problems, String note) {

	public BuildReport {
		problems = List.copyOf(problems);
	}

	/**
	 * Test totals.
	 *
	 * @param run tests run
	 * @param failed assertion failures
	 * @param errors unexpected exceptions
	 * @param skipped skipped tests
	 */
	public record Tests(int run, int failed, int errors, int skipped) {

		public int passed() {
			return this.run - this.failed - this.errors - this.skipped;
		}

	}

}
