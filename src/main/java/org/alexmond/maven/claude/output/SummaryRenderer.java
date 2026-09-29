package org.alexmond.maven.claude.output;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders a {@link BuildReport} as the few lines an agent reads instead of the Maven log.
 */
public final class SummaryRenderer {

	private static final String INDENT = "         ";

	private SummaryRenderer() {
	}

	/**
	 * Renders the summary.
	 * @param report the build report
	 * @param logPath where the full log was written (relative to the working dir), or
	 * {@code null}
	 * @param jsonPath where the JSON report was written, or {@code null}
	 * @param maxProblems how many problems to print before collapsing the rest
	 * @return the summary text, ending with a line separator
	 */
	public static String render(BuildReport report, String logPath, String jsonPath, int maxProblems) {
		StringBuilder out = new StringBuilder();
		out.append(headline(report)).append('\n');
		List<Problem> problems = report.problems();
		int shown = Math.min(problems.size(), maxProblems);
		for (int i = 0; i < shown; i++) {
			out.append('\n');
			appendProblem(out, problems.get(i));
		}
		if (problems.size() > shown) {
			out.append('\n')
				.append("... ")
				.append(problems.size() - shown)
				.append(" more problems in the JSON report\n");
		}
		List<String> footer = new ArrayList<>();
		if (logPath != null) {
			footer.add("Full log: " + logPath);
		}
		if (jsonPath != null) {
			footer.add("JSON: " + jsonPath);
		}
		if (report.note() != null) {
			footer.add(report.note());
		}
		if (!footer.isEmpty()) {
			out.append('\n').append(String.join(" · ", footer)).append('\n');
		}
		return out.toString();
	}

	static String headline(BuildReport report) {
		List<String> parts = new ArrayList<>();
		parts.add((report.success() ? "BUILD OK" : "BUILD FAILED") + " in " + duration(report.durationMillis()));
		if (report.modules() > 1) {
			StringBuilder modules = new StringBuilder().append(report.modules()).append(" modules");
			if (report.modulesFailed() > 0 || report.modulesSkipped() > 0) {
				modules.append(": ").append(report.modulesOk()).append(" ok");
				if (report.modulesFailed() > 0) {
					modules.append(", ").append(report.modulesFailed()).append(" failed");
				}
				if (report.modulesSkipped() > 0) {
					modules.append(", ").append(report.modulesSkipped()).append(" skipped");
				}
			}
			parts.add(modules.toString());
		}
		BuildReport.Tests tests = report.tests();
		if (tests != null && tests.run() > 0) {
			StringBuilder line = new StringBuilder("tests: ");
			if (tests.failed() == 0 && tests.errors() == 0) {
				line.append(tests.passed()).append(" passed");
			}
			else {
				line.append(tests.run()).append(" run");
				if (tests.failed() > 0) {
					line.append(", ").append(tests.failed()).append(" failed");
				}
				if (tests.errors() > 0) {
					line.append(", ").append(tests.errors()).append(" errored");
				}
			}
			if (tests.skipped() > 0) {
				line.append(", ").append(tests.skipped()).append(" skipped");
			}
			parts.add(line.toString());
		}
		return String.join(" · ", parts);
	}

	private static void appendProblem(StringBuilder out, Problem problem) {
		String label = String.format(Locale.ROOT, "%-9s", problem.kind().label());
		if (problem.kind() == Problem.Kind.COMPILE) {
			out.append(label).append((problem.location() != null) ? problem.location() : "").append('\n');
			out.append(INDENT).append(problem.title()).append('\n');
		}
		else {
			out.append(label).append(problem.title());
			if (problem.location() != null) {
				out.append("  ").append(problem.location());
			}
			out.append('\n');
		}
		for (String line : problem.detail()) {
			out.append(INDENT).append(line).append('\n');
		}
	}

	static String duration(long millis) {
		if (millis < 60_000) {
			return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
		}
		long seconds = millis / 1000;
		return String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60);
	}

}
