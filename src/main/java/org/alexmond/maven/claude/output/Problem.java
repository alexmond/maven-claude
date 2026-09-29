package org.alexmond.maven.claude.output;

import java.util.List;

/**
 * One thing that went wrong in the build, reduced to what an agent needs to act on it.
 *
 * @param kind what failed
 * @param module artifactId of the module it happened in, or {@code null}
 * @param title short headline (a test name, a goal, or the compiler message)
 * @param location {@code path:line[:column]} relative to the build root, or {@code null}
 * @param detail further lines worth showing (messages, trimmed stack frames)
 */
public record Problem(Kind kind, String module, String title, String location, List<String> detail) {

	public Problem {
		detail = List.copyOf(detail);
	}

	/** Category of a problem; {@link #label()} is what the summary prints. */
	public enum Kind {

		COMPILE("COMPILE"), TEST("TEST"), GOAL("FAILED"), BUILD("ERROR");

		private final String label;

		Kind(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}

	}

}
