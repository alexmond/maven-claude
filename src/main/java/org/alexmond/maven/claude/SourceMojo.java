package org.alexmond.maven.claude;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.alexmond.maven.claude.source.ClassLookup;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

/**
 * Finds which dependency provides a class and extracts its source file, so an agent can
 * read the real API instead of guessing it.
 * <p>
 * Looks in the JDK, the module's own classes, and its test class path. The source comes
 * from the artifact's {@code -sources.jar} (downloaded if needed), the JDK's
 * {@code src.zip}, or — for reactor modules — the module's source directory. When no
 * source is published, prints {@code javap -p} output instead.
 * <p>
 * {@code ./mvnw -q claude:source -Dclass=com.fasterxml.jackson.databind.ObjectMapper}
 */
@Mojo(name = "source", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class SourceMojo extends AbstractMojo {

	private static final int MAX_MATCHES = 10;

	private final RepositorySystem repositorySystem;

	/**
	 * Class to look up: fully qualified, nested ({@code a.B.C} or {@code a.B$C}), or
	 * simple.
	 */
	@Parameter(property = "class", required = true)
	private String className;

	/** Unpack the whole sources jar instead of the single file, so it can be searched. */
	@Parameter(property = "claude.unpackAll", defaultValue = "false")
	private boolean unpackAll;

	/**
	 * Where sources are extracted. Defaults to {@code target/claude/sources} under the
	 * build root.
	 */
	@Parameter(property = "claude.sourcesDir")
	private File sourcesDirectory;

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
	private RepositorySystemSession repositorySession;

	@Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
	private List<RemoteRepository> remoteRepositories;

	@Inject
	public SourceMojo(RepositorySystem repositorySystem) {
		this.repositorySystem = repositorySystem;
	}

	@Override
	public void execute() throws MojoFailureException {
		String query = (this.className != null) ? this.className.strip() : "";
		if (query.isEmpty()) {
			throw new MojoFailureException("Pass the class to look up: -Dclass=com.example.SomeClass");
		}
		String doneKey = "claude.source.done:" + query;
		if (this.repositorySession.getData().get(doneKey) != null) {
			return;
		}
		Path root = BuildRoot.of(this.session);
		Path outputDir = (this.sourcesDirectory != null) ? this.sourcesDirectory.toPath()
				: root.resolve("target/claude/sources");
		List<String> report = new ArrayList<>();
		try {
			boolean found = lookUpInJdk(query, outputDir, report) || lookUpInProject(query, report)
					|| lookUpInDependencies(query, outputDir, report);
			if (found) {
				this.repositorySession.getData().set(doneKey, Boolean.TRUE);
				print(report);
			}
			else if (isLastProject()) {
				throw new MojoFailureException("No class matching '" + query
						+ "' on the class path of any module in this build (dependencies, test dependencies, the JDK).");
			}
		}
		catch (IOException ex) {
			throw new MojoFailureException("Could not read class path entries: " + ex.getMessage(), ex);
		}
	}

	private boolean lookUpInJdk(String query, Path outputDir, List<String> report) throws IOException {
		if (ClassLookup.isSimpleName(query)) {
			return false;
		}
		String entry = null;
		String module = null;
		for (String candidate : ClassLookup.entryCandidates(query)) {
			String binaryName = ClassLookup.binaryName(candidate);
			Optional<String> candidateModule = ClassLookup.jdkModule(binaryName);
			if (entry == null && candidateModule.isPresent() && isJdkClass(binaryName)) {
				entry = candidate;
				module = candidateModule.get();
			}
		}
		if (entry == null) {
			return false;
		}
		report.add(ClassLookup.binaryName(entry));
		report.add("  from    JDK module " + module + " (Java " + System.getProperty("java.version") + ")");
		Optional<Path> srcZip = ClassLookup.jdkSourceArchive();
		Optional<Path> source = srcZip.isPresent() ? ClassLookup.extract(srcZip.get(), module + "/",
				ClassLookup.sourceEntry(entry), outputDir.resolve("jdk-" + Runtime.version().feature()))
				: Optional.empty();
		report.add(source.map((path) -> "  source  " + BuildRoot.display(path))
			.orElse("  source  not available (install the JDK's src.zip, e.g. the openjdk-src package)"));
		return true;
	}

	private static boolean isJdkClass(String binaryName) {
		try {
			Class.forName(binaryName, false, ClassLoader.getPlatformClassLoader());
			return true;
		}
		catch (ClassNotFoundException | LinkageError ex) {
			return false;
		}
	}

	private boolean lookUpInProject(String query, List<String> report) throws IOException {
		List<Path> outputs = List.of(Path.of(this.project.getBuild().getOutputDirectory()),
				Path.of(this.project.getBuild().getTestOutputDirectory()));
		for (Path output : outputs) {
			for (String entry : limit(ClassLookup.find(output, query))) {
				report.add(ClassLookup.binaryName(entry));
				report.add("  from    this build, module " + this.project.getArtifactId());
				reportReactorSource(this.project, entry, report);
			}
		}
		return !report.isEmpty();
	}

	private boolean lookUpInDependencies(String query, Path outputDir, List<String> report) throws IOException {
		int matches = 0;
		for (Artifact artifact : this.project.getArtifacts()) {
			if (artifact.getFile() == null || matches >= MAX_MATCHES) {
				continue;
			}
			for (String entry : limit(ClassLookup.find(artifact.getFile().toPath(), query))) {
				matches++;
				report.add(ClassLookup.binaryName(entry));
				report.add("  from    " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
						+ artifact.getVersion() + " (" + artifact.getScope() + ")");
				MavenProject reactorModule = reactorProject(artifact);
				if (reactorModule != null) {
					reportReactorSource(reactorModule, entry, report);
				}
				else {
					reportDependencySource(artifact, entry, outputDir, report);
				}
			}
		}
		return matches > 0;
	}

	private void reportReactorSource(MavenProject module, String entry, List<String> report) {
		String sourceEntry = ClassLookup.sourceEntry(entry);
		List<String> roots = new ArrayList<>(module.getCompileSourceRoots());
		roots.addAll(module.getTestCompileSourceRoots());
		for (String sourceRoot : roots) {
			Path candidate = Path.of(sourceRoot).resolve(sourceEntry);
			if (Files.isRegularFile(candidate)) {
				report.add("  source  " + BuildRoot.display(candidate));
				return;
			}
		}
		report.add("  source  not found under " + module.getArtifactId() + "'s source roots (generated code?)");
	}

	private void reportDependencySource(Artifact artifact, String entry, Path outputDir, List<String> report)
			throws IOException {
		Path targetDir = outputDir.resolve(artifact.getArtifactId() + "-" + artifact.getVersion());
		Path sourcesJar = resolveSourcesJar(artifact);
		if (sourcesJar != null) {
			if (this.unpackAll) {
				int files = ClassLookup.unpackAll(sourcesJar, targetDir);
				report.add("  sources " + BuildRoot.display(targetDir) + " (" + files + " files)");
			}
			Optional<Path> source = ClassLookup.extract(sourcesJar, "", ClassLookup.sourceEntry(entry), targetDir);
			if (source.isPresent()) {
				report.add("  source  " + BuildRoot.display(source.get()));
				return;
			}
		}
		report.add("  source  not published" + ((sourcesJar != null) ? " for this class" : "") + "; signatures:");
		ClassLookup.javap(artifact.getFile().toPath(), ClassLookup.binaryName(entry))
			.ifPresentOrElse((javap) -> javap.lines().forEach((line) -> report.add("    " + line)),
					() -> report.add("    (javap unavailable)"));
	}

	private Path resolveSourcesJar(Artifact artifact) {
		DefaultArtifact sources = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "sources", "jar",
				artifact.getVersion());
		try {
			File file = this.repositorySystem
				.resolveArtifact(this.repositorySession, new ArtifactRequest(sources, this.remoteRepositories, null))
				.getArtifact()
				.getFile();
			return (file != null) ? file.toPath() : null;
		}
		catch (ArtifactResolutionException ex) {
			getLog().debug("No sources jar for " + artifact + ": " + ex.getMessage());
			return null;
		}
	}

	private MavenProject reactorProject(Artifact artifact) {
		for (MavenProject candidate : this.session.getProjects()) {
			if (candidate.getGroupId().equals(artifact.getGroupId())
					&& candidate.getArtifactId().equals(artifact.getArtifactId())) {
				return candidate;
			}
		}
		return null;
	}

	private boolean isLastProject() {
		List<MavenProject> projects = this.session.getProjects();
		return projects.isEmpty() || projects.get(projects.size() - 1).equals(this.project);
	}

	/**
	 * The lookup result is the answer to the command, so it goes to stdout even under
	 * {@code -q} (like {@code help:evaluate -DforceStdout}).
	 */
	private static void print(List<String> lines) {
		lines.forEach(System.out::println);
		System.out.flush();
	}

	private static List<String> limit(List<String> entries) {
		return (entries.size() > MAX_MATCHES) ? entries.subList(0, MAX_MATCHES) : entries;
	}

}
