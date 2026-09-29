package org.alexmond.maven.claude.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds a class in jars/directories and pulls its source file out of a sources jar (or
 * the JDK's {@code src.zip}).
 * <p>
 * A query is either a qualified name ({@code com.acme.Foo}, {@code com.acme.Foo.Inner},
 * {@code com.acme.Foo$Inner}) or a simple name ({@code Foo}), which matches any top-level
 * class of that name.
 */
public final class ClassLookup {

	private static final String[] SOURCE_EXTENSIONS = { ".java", ".kt", ".groovy", ".scala" };

	private ClassLookup() {
	}

	/** Whether the query is a bare class name. */
	public static boolean isSimpleName(String query) {
		return query.indexOf('.') < 0 && query.indexOf('/') < 0;
	}

	/**
	 * Class-file entry names a qualified query may refer to, most likely first:
	 * {@code a.b.C.D} yields {@code a/b/C/D.class}, then {@code a/b/C$D.class}, and so
	 * on.
	 */
	public static List<String> entryCandidates(String query) {
		String name = query.strip().replace('/', '.');
		if (name.endsWith(".class")) {
			name = name.substring(0, name.length() - ".class".length());
		}
		if (name.endsWith(".java")) {
			name = name.substring(0, name.length() - ".java".length());
		}
		String[] parts = name.split("\\.");
		List<String> candidates = new ArrayList<>();
		if (parts.length == 1) {
			candidates.add(parts[0] + ".class");
		}
		// Try the longest package first: a.b.C.D -> a/b/C/D, then a/b/C$D, then
		// a/b$C$D...
		for (int split = parts.length - 1; split >= 1; split--) {
			String pkg = String.join("/", Arrays.copyOfRange(parts, 0, split));
			String cls = String.join("$", Arrays.copyOfRange(parts, split, parts.length));
			candidates.add(pkg + "/" + cls + ".class");
		}
		return candidates;
	}

	/**
	 * Class entries in {@code container} (a jar or a classes directory) matching the
	 * query.
	 */
	public static List<String> find(Path container, String query) throws IOException {
		if (Files.isDirectory(container)) {
			return findInDirectory(container, query);
		}
		if (!Files.isRegularFile(container)) {
			return List.of();
		}
		List<String> matches = new ArrayList<>();
		try (ZipFile zip = new ZipFile(container.toFile())) {
			if (isSimpleName(query)) {
				String suffix = "/" + query + ".class";
				Enumeration<? extends ZipEntry> entries = zip.entries();
				while (entries.hasMoreElements()) {
					String entry = entries.nextElement().getName();
					if ((entry.endsWith(suffix) || entry.equals(query + ".class")) && !entry.startsWith("META-INF/")) {
						matches.add(entry);
					}
				}
			}
			else {
				for (String candidate : entryCandidates(query)) {
					if (zip.getEntry(candidate) != null) {
						matches.add(candidate);
						break;
					}
				}
			}
		}
		return matches;
	}

	private static List<String> findInDirectory(Path directory, String query) throws IOException {
		if (isSimpleName(query)) {
			String fileName = query + ".class";
			try (Stream<Path> files = Files.walk(directory)) {
				return files.filter((file) -> file.getFileName().toString().equals(fileName))
					.map((file) -> directory.relativize(file).toString().replace('\\', '/'))
					.sorted()
					.toList();
			}
		}
		for (String candidate : entryCandidates(query)) {
			if (Files.isRegularFile(directory.resolve(candidate))) {
				return List.of(candidate);
			}
		}
		return List.of();
	}

	/** {@code a/b/C$D.class} becomes {@code a/b/C.java}. */
	public static String sourceEntry(String classEntry) {
		String base = classEntry.substring(0, classEntry.length() - ".class".length());
		int slash = base.lastIndexOf('/');
		int nested = base.indexOf('$', slash + 1);
		return ((nested < 0) ? base : base.substring(0, nested)) + ".java";
	}

	/** {@code a/b/C$D.class} becomes {@code a.b.C$D}. */
	public static String binaryName(String classEntry) {
		return classEntry.substring(0, classEntry.length() - ".class".length()).replace('/', '.');
	}

	/**
	 * Copies the source of {@code sourceEntry} out of an archive (a sources jar or
	 * {@code src.zip}), also trying Kotlin/Groovy/Scala file names.
	 * @param archive the archive
	 * @param prefix path prefix inside the archive ({@code java.base/} for src.zip, else
	 * empty)
	 * @param sourceEntry e.g. {@code a/b/C.java}
	 * @param targetDir where to write it (keeps the package path)
	 * @return the written file, or empty when the archive doesn't contain it
	 */
	public static Optional<Path> extract(Path archive, String prefix, String sourceEntry, Path targetDir)
			throws IOException {
		String base = sourceEntry.substring(0, sourceEntry.length() - ".java".length());
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			for (String extension : SOURCE_EXTENSIONS) {
				ZipEntry entry = zip.getEntry(prefix + base + extension);
				if (entry != null) {
					Path target = safeResolve(targetDir, base + extension);
					Files.createDirectories(target.getParent());
					try (InputStream in = zip.getInputStream(entry)) {
						Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
					}
					return Optional.of(target);
				}
			}
		}
		return Optional.empty();
	}

	/** Unpacks a whole sources jar so it can be searched. Returns the number of files. */
	public static int unpackAll(Path archive, Path targetDir) throws IOException {
		int count = 0;
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
					continue;
				}
				Path target = safeResolve(targetDir, entry.getName());
				Files.createDirectories(target.getParent());
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				}
				count++;
			}
		}
		return count;
	}

	private static Path safeResolve(Path dir, String entry) throws IOException {
		Path target = dir.resolve(entry).normalize();
		if (!target.startsWith(dir.normalize())) {
			throw new IOException("Refusing to extract outside the target directory: " + entry);
		}
		return target;
	}

	/** Name of the JDK module that exports {@code className}'s package, if any. */
	public static Optional<String> jdkModule(String className) {
		int dot = className.lastIndexOf('.');
		if (dot < 0) {
			return Optional.empty();
		}
		String pkg = className.substring(0, dot);
		return ModuleLayer.boot()
			.modules()
			.stream()
			.filter((module) -> module.getPackages().contains(pkg))
			.map(Module::getName)
			.findFirst();
	}

	/** The running JDK's {@code lib/src.zip}, if installed. */
	public static Optional<Path> jdkSourceArchive() {
		Path srcZip = Path.of(System.getProperty("java.home"), "lib", "src.zip");
		return Files.isRegularFile(srcZip) ? Optional.of(srcZip) : Optional.empty();
	}

	/**
	 * Runs {@code javap -p} on a class, for when no source is published.
	 * @return javap's output, or empty when javap is unavailable or fails
	 */
	public static Optional<String> javap(Path classpath, String binaryName) {
		Optional<ToolProvider> javap = ToolProvider.findFirst("javap");
		if (javap.isEmpty()) {
			return Optional.empty();
		}
		StringWriter out = new StringWriter();
		StringWriter err = new StringWriter();
		int exit = javap.get()
			.run(new PrintWriter(out), new PrintWriter(err), "-p", "-cp", classpath.toString(), binaryName);
		return (exit == 0) ? Optional.of(out.toString()) : Optional.empty();
	}

}
