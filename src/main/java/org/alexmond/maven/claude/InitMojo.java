package org.alexmond.maven.claude;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * One-time setup of a project for Claude Code:
 * <ol>
 * <li>registers this plugin in {@code .mvn/extensions.xml}, so builds run from Claude
 * Code print a compact summary and {@code claude:} goals resolve;</li>
 * <li>writes {@code .claude/maven-context.md} (see {@code claude:context});</li>
 * <li>allows Maven commands in {@code .claude/settings.json}, so the agent can build and
 * test without a permission prompt each time;</li>
 * <li>imports the context from {@code CLAUDE.md}, if the project has one.</li>
 * </ol>
 * Re-running is safe: each step only adds what is missing.
 * <p>
 * {@code mvn org.alexmond:claude-maven-plugin:init}
 */
@Mojo(name = "init", aggregator = true, threadSafe = true)
public class InitMojo extends AbstractMojo {

	/** Register the core extension in {@code .mvn/extensions.xml}. */
	@Parameter(property = "claude.extension", defaultValue = "true")
	private boolean extension;

	/** Allow {@code mvn}/{@code ./mvnw} commands in {@code .claude/settings.json}. */
	@Parameter(property = "claude.permissions", defaultValue = "true")
	private boolean permissions;

	/** Import the generated context from an existing {@code CLAUDE.md}. */
	@Parameter(property = "claude.claudeMd", defaultValue = "true")
	private boolean claudeMd;

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	@Parameter(defaultValue = "${plugin}", readonly = true, required = true)
	private PluginDescriptor plugin;

	@Override
	public void execute() throws MojoExecutionException {
		Path root = BuildRoot.of(this.session);
		String mvn = BuildRoot.mavenCommand(root);
		try {
			if (this.extension) {
				Path file = root.resolve(".mvn/extensions.xml");
				InitFiles.Change change = InitFiles.ensureExtension(file, this.plugin.getVersion());
				report(change, ".mvn/extensions.xml",
						"core extension " + PluginInfo.ARTIFACT_ID + " " + this.plugin.getVersion());
			}
			Path context = ContextMojo.write(this.session, root, null);
			getLog().info("wrote     " + BuildRoot.display(context));
			if (this.permissions) {
				List<String> added = InitFiles.allowPermissions(root.resolve(".claude/settings.json"),
						List.of("Bash(./mvnw *)", "Bash(mvn *)"));
				getLog().info((added.isEmpty() ? "unchanged " : "allowed   ") + ".claude/settings.json"
						+ (added.isEmpty() ? "" : " " + String.join(", ", added)));
			}
			if (this.claudeMd) {
				InitFiles.Change change = InitFiles.importContext(root.resolve("CLAUDE.md"), mvn);
				if (change == InitFiles.Change.SKIPPED) {
					getLog()
						.info("skipped   CLAUDE.md (none yet) — after /init, add the line @.claude/maven-context.md");
				}
				else {
					report(change, "CLAUDE.md", "import of .claude/maven-context.md");
				}
			}
		}
		catch (IOException ex) {
			throw new MojoExecutionException("claude:init failed: " + ex.getMessage(), ex);
		}
	}

	private void report(InitFiles.Change change, String file, String what) {
		String verb = switch (change) {
			case CREATED -> "created  ";
			case ADDED -> "added    ";
			case UPDATED -> "updated  ";
			default -> "unchanged";
		};
		getLog().info(verb + " " + file + " (" + what + ")");
	}

}
