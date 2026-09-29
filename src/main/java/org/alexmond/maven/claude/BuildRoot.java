package org.alexmond.maven.claude;

import java.io.File;
import java.nio.file.Path;

import org.apache.maven.execution.MavenSession;

/** The directory that holds {@code .mvn}, {@code .claude} and the root {@code target}. */
final class BuildRoot {

	private BuildRoot() {
	}

	static Path of(MavenSession session) {
		File dir = session.getRequest().getMultiModuleProjectDirectory();
		if (dir == null && session.getTopLevelProject() != null) {
			dir = session.getTopLevelProject().getBasedir();
		}
		if (dir == null) {
			dir = new File(session.getExecutionRootDirectory());
		}
		return dir.toPath().toAbsolutePath().normalize();
	}

	/** {@code ./mvnw} when the build has a Maven wrapper, else {@code mvn}. */
	static String mavenCommand(Path root) {
		return root.resolve("mvnw").toFile().isFile() ? "./mvnw" : "mvn";
	}

	/** Relative to the directory Maven runs in when below it, else absolute. */
	static String display(Path file) {
		Path cwd = Path.of("").toAbsolutePath();
		return file.startsWith(cwd) ? cwd.relativize(file).toString().replace('\\', '/') : file.toString();
	}

}
