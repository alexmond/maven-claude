package org.alexmond.maven.claude.output;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts compiler errors from captured log lines (or a compiler exception's long
 * message). Understands the maven-compiler-plugin format
 * ({@code [ERROR] /abs/Foo.java:[12,5] message}, followed by indented
 * {@code symbol:}/{@code location:} lines) and the Kotlin plugin format
 * ({@code e: file:///abs/Foo.kt:12:5 message}).
 */
public final class CompilerMessageParser {

	private static final Pattern JAVAC = Pattern
		.compile("^(?:\\[ERROR\\]\\s+)?([^\\[\\s].*?\\.(?:java|groovy|scala|kt)):\\[(\\d+)(?:,(\\d+))?\\]\\s*(.*)$");

	private static final Pattern KOTLIN = Pattern
		.compile("^(?:\\[ERROR\\]\\s+)?e:\\s+(?:file://)?([^\\[\\s].*?\\.kts?):(\\d+):(\\d+)\\s+(.*)$");

	private static final int MAX_CONTINUATION_LINES = 4;

	private CompilerMessageParser() {
	}

	/**
	 * Parses compiler errors from {@code lines}.
	 * @param lines captured output of the failing compile mojo
	 * @param root build root, used to shorten absolute paths
	 * @param module module the errors belong to
	 * @return distinct problems in order of appearance
	 */
	public static List<Problem> parse(List<String> lines, Path root, String module) {
		List<Problem> problems = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (int i = 0; i < lines.size(); i++) {
			Matcher matcher = match(lines.get(i));
			if (matcher == null) {
				continue;
			}
			String location = Paths.relativize(root, matcher.group(1)) + ":" + matcher.group(2)
					+ ((matcher.group(3) != null) ? ":" + matcher.group(3) : "");
			String message = matcher.group(4).trim();
			List<String> detail = new ArrayList<>();
			int j = i + 1;
			while (j < lines.size() && detail.size() < MAX_CONTINUATION_LINES && isContinuation(lines.get(j))) {
				detail.add(lines.get(j).strip());
				j++;
			}
			if (seen.add(location + "|" + message)) {
				problems.add(new Problem(Problem.Kind.COMPILE, module, message, location, detail));
			}
		}
		return problems;
	}

	private static Matcher match(String line) {
		Matcher javac = JAVAC.matcher(line);
		if (javac.matches()) {
			return javac;
		}
		Matcher kotlin = KOTLIN.matcher(line);
		return kotlin.matches() ? kotlin : null;
	}

	private static boolean isContinuation(String line) {
		return !line.isBlank() && Character.isWhitespace(line.charAt(0)) && !line.strip().startsWith("[");
	}

	/** Path helpers shared by the parsers. */
	static final class Paths {

		private Paths() {
		}

		static String relativize(Path root, String path) {
			if (root == null) {
				return path;
			}
			try {
				Path candidate = Path.of(path);
				if (candidate.isAbsolute() && candidate.startsWith(root)) {
					return root.relativize(candidate).toString().replace('\\', '/');
				}
			}
			catch (RuntimeException ex) {
				// not a path we can shorten
			}
			return path;
		}

	}

}
