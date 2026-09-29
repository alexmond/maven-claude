package org.alexmond.maven.claude.context;

import java.util.List;

/**
 * What an agent needs to know about one reactor module, extracted from its effective POM.
 *
 * @param groupId groupId
 * @param artifactId artifactId
 * @param version version
 * @param packaging packaging
 * @param name human name, or {@code null}
 * @param path module directory relative to the build root ({@code .} for the root)
 * @param javaRelease Java release the module compiles for, or {@code null}
 * @param reactorDependencies artifactIds of other reactor modules it depends on
 * @param checks build checks that can fail the build (Checkstyle, Enforcer, ...)
 * @param formatCommands goals that auto-fix formatting
 * @param testStack test libraries in use
 * @param integrationTests whether failsafe integration tests are bound
 * @param springBoot whether spring-boot-maven-plugin is present
 * @param profiles profiles declared in the module's POM
 */
public record ModuleFacts(String groupId, String artifactId, String version, String packaging, String name, String path,
		String javaRelease, List<String> reactorDependencies, List<String> checks, List<String> formatCommands,
		List<String> testStack, boolean integrationTests, boolean springBoot, List<Profile> profiles) {

	public ModuleFacts {
		reactorDependencies = List.copyOf(reactorDependencies);
		checks = List.copyOf(checks);
		formatCommands = List.copyOf(formatCommands);
		testStack = List.copyOf(testStack);
		profiles = List.copyOf(profiles);
	}

	/** {@code groupId:artifactId:version}. */
	public String coordinates() {
		return this.groupId + ":" + this.artifactId + ":" + this.version;
	}

	/**
	 * A declared profile.
	 *
	 * @param id profile id
	 * @param activation how it activates, e.g. {@code by default}, {@code -Dfoo}, or
	 * {@code manual}
	 */
	public record Profile(String id, String activation) {

	}

}
