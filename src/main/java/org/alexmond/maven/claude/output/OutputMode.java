package org.alexmond.maven.claude.output;

import java.util.Locale;
import java.util.Map;

/**
 * How the extension treats console output for one build.
 * <ul>
 * <li>{@link #OFF} — do nothing (the default outside Claude Code).</li>
 * <li>{@link #REPORT} — normal Maven output, plus the summary and
 * {@code target/claude/build-report.json}.</li>
 * <li>{@link #SUMMARY} — hide lifecycle noise; print only the summary and the output of
 * goals typed on the command line (the default inside Claude Code).</li>
 * </ul>
 */
public enum OutputMode {

	OFF, REPORT, SUMMARY;

	/** User/system property that overrides the mode: off, report, summary or auto. */
	public static final String PROPERTY = "claude.output";

	/** Environment variable equivalent of {@link #PROPERTY}. */
	public static final String ENV = "CLAUDE_MAVEN_OUTPUT";

	/** Set to {@code 1} by Claude Code in every shell it runs. */
	public static final String CLAUDE_CODE_ENV = "CLAUDECODE";

	/**
	 * Resolves the mode. The first non-blank of the user property, system property and
	 * environment variable wins; {@code auto} (or nothing) means summary inside Claude
	 * Code and off elsewhere.
	 */
	public static OutputMode resolve(String userProperty, String systemProperty, Map<String, String> env) {
		String value = firstNonBlank(userProperty, systemProperty, env.get(ENV));
		if (value == null || "auto".equalsIgnoreCase(value.trim())) {
			return "1".equals(env.get(CLAUDE_CODE_ENV)) ? SUMMARY : OFF;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "off", "false", "none" -> OFF;
			case "report", "full" -> REPORT;
			case "summary", "on", "true" -> SUMMARY;
			default -> "1".equals(env.get(CLAUDE_CODE_ENV)) ? SUMMARY : OFF;
		};
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

}
