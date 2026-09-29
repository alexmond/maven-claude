package org.alexmond.maven.claude.output;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reduces a Java stack trace to its message, the frames that belong to the project, and
 * the root cause. Framework frames (JUnit, reflection, Spring, the JDK) are dropped.
 */
public final class StackTraces {

	private static final Pattern FRAME = Pattern
		.compile("^\\s*at\\s+(?:[^\\s(]*/)?([\\w$.]+)\\.([\\w$<>-]+)\\(([^:)]*)(?::(\\d+))?\\)");

	private static final int MAX_MESSAGE_LINES = 6;

	private StackTraces() {
	}

	/**
	 * Trims {@code stackTrace}.
	 * @param stackTrace full trace text as printed by {@code printStackTrace}
	 * @param index source index deciding which frames are project frames
	 * @param maxFrames maximum project frames to keep
	 * @return the trimmed trace
	 */
	public static Trimmed trim(String stackTrace, SourceIndex index, int maxFrames) {
		List<String> message = new ArrayList<>();
		List<Frame> frames = new ArrayList<>();
		String rootCause = null;
		boolean inHeader = true;
		for (String raw : stackTrace.split("\\R")) {
			Matcher frame = FRAME.matcher(raw);
			if (frame.find()) {
				inHeader = false;
				if (frames.size() < maxFrames) {
					String path = index.locate(frame.group(1), frame.group(3));
					if (path != null) {
						Frame candidate = new Frame(frame.group(1), frame.group(2), path, frame.group(4));
						if (!frames.contains(candidate)) {
							frames.add(candidate);
						}
					}
				}
			}
			else if (raw.startsWith("Caused by:")) {
				inHeader = false;
				rootCause = shortenType(raw.substring("Caused by:".length()).strip());
			}
			else if (inHeader && !raw.isBlank() && message.size() < MAX_MESSAGE_LINES) {
				message.add(message.isEmpty() ? shortenType(raw.strip()) : raw.stripTrailing());
			}
		}
		return new Trimmed(message, frames, rootCause);
	}

	/**
	 * {@code org.opentest4j.AssertionFailedError: boom} becomes
	 * {@code AssertionFailedError: boom}.
	 */
	static String shortenType(String line) {
		int colon = line.indexOf(": ");
		String type = (colon < 0) ? line : line.substring(0, colon);
		if (type.contains(" ") || !type.contains(".")) {
			return line;
		}
		return type.substring(type.lastIndexOf('.') + 1) + ((colon < 0) ? "" : line.substring(colon));
	}

	/**
	 * A trimmed trace.
	 *
	 * @param message the exception headline lines
	 * @param frames project frames, outermost last
	 * @param rootCause the last {@code Caused by:} line, or {@code null}
	 */
	public record Trimmed(List<String> message, List<Frame> frames, String rootCause) {

	}

	/**
	 * A project stack frame.
	 *
	 * @param className declaring class
	 * @param method method name
	 * @param path source path relative to the build root
	 * @param line line number, or {@code null}
	 */
	public record Frame(String className, String method, String path, String line) {

		public String location() {
			return (this.line != null) ? this.path + ":" + this.line : this.path;
		}

		@Override
		public String toString() {
			String simple = this.className.substring(this.className.lastIndexOf('.') + 1);
			return "at " + simple + "." + this.method + "(" + location() + ")";
		}

	}

}
