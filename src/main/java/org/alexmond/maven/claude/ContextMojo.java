package org.alexmond.maven.claude;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.alexmond.maven.claude.context.ContextRenderer;
import org.alexmond.maven.claude.context.ModuleFactsCollector;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Writes {@code .claude/maven-context.md}: modules and how they depend on each other,
 * Java release, test stack, checks that fail the build, profiles, and the exact commands
 * to build or test one module. Import it from CLAUDE.md with
 * {@code @.claude/maven-context.md}.
 */
@Mojo(name = "context", aggregator = true, threadSafe = true)
public class ContextMojo extends AbstractMojo {

	/** Output file. Defaults to {@code .claude/maven-context.md} under the build root. */
	@Parameter(property = "claude.contextFile")
	private File contextFile;

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	@Override
	public void execute() throws MojoExecutionException {
		Path root = BuildRoot.of(this.session);
		Path file = write(this.session, root, (this.contextFile != null) ? this.contextFile.toPath() : null);
		getLog().info("Wrote " + BuildRoot.display(file) + " (" + this.session.getProjects().size()
				+ " modules). Import it from CLAUDE.md with @.claude/maven-context.md");
	}

	static Path write(MavenSession session, Path root, Path target) throws MojoExecutionException {
		Path file = (target != null) ? target : root.resolve(".claude/maven-context.md");
		List<MavenProject> projects = new ArrayList<>(session.getProjects());
		MavenProject top = session.getTopLevelProject();
		if (top != null && projects.remove(top)) {
			projects.add(0, top);
		}
		String markdown = ContextRenderer.render(ModuleFactsCollector.collect(projects, root),
				BuildRoot.mavenCommand(root));
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, markdown, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new MojoExecutionException("Could not write " + file + ": " + ex.getMessage(), ex);
		}
		return file;
	}

}
