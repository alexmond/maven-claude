package org.alexmond.maven.claude.output;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers "is this class part of the build, and where is its source file?" by probing the
 * reactor's source roots. Used to keep only project frames in stack traces and to turn
 * {@code Foo.java:42} into a path an agent can open.
 */
public final class SourceIndex {

	private final List<Path> sourceRoots;

	private final Path root;

	private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

	public SourceIndex(List<Path> sourceRoots, Path root) {
		this.sourceRoots = List.copyOf(sourceRoots);
		this.root = root;
	}

	/**
	 * Locates the source file of a stack frame.
	 * @param className fully qualified class name from the frame (nested classes allowed)
	 * @param fileName file name from the frame, e.g. {@code Foo.java}; may be
	 * {@code null}
	 * @return path relative to the build root, or {@code null} when not a project class
	 */
	public String locate(String className, String fileName) {
		int lastDot = className.lastIndexOf('.');
		String packagePath = (lastDot < 0) ? "" : className.substring(0, lastDot).replace('.', '/') + "/";
		String file = fileName;
		if (file == null || file.isBlank() || !file.contains(".")) {
			String simple = className.substring(lastDot + 1);
			int nested = simple.indexOf('$');
			file = ((nested < 0) ? simple : simple.substring(0, nested)) + ".java";
		}
		String relative = packagePath + file;
		return this.cache.computeIfAbsent(relative, this::find).orElse(null);
	}

	private Optional<String> find(String relative) {
		for (Path sourceRoot : this.sourceRoots) {
			Path candidate = sourceRoot.resolve(relative);
			if (Files.isRegularFile(candidate)) {
				return Optional.of(CompilerMessageParser.Paths.relativize(this.root, candidate.toString()));
			}
		}
		return Optional.empty();
	}

}
