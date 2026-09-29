package org.alexmond.maven.claude.context;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Builds {@link ModuleFacts} from resolved {@link MavenProject}s.
 */
public final class ModuleFactsCollector {

	/** Plugins that fail the build on a finding, mapped to how the summary names them. */
	private static final Map<String, String> CHECKS = Map.ofEntries(Map.entry("maven-checkstyle-plugin", "Checkstyle"),
			Map.entry("maven-pmd-plugin", "PMD"), Map.entry("spotbugs-maven-plugin", "SpotBugs"),
			Map.entry("spotless-maven-plugin", "Spotless"),
			Map.entry("spring-javaformat-maven-plugin", "spring-javaformat"),
			Map.entry("formatter-maven-plugin", "formatter"), Map.entry("maven-enforcer-plugin", "Enforcer"),
			Map.entry("license-maven-plugin", "license headers"), Map.entry("revapi-maven-plugin", "Revapi"),
			Map.entry("japicmp-maven-plugin", "japicmp"));

	private static final Map<String, String> FORMATTERS = Map.of("spotless-maven-plugin", "spotless:apply",
			"spring-javaformat-maven-plugin", "spring-javaformat:apply", "formatter-maven-plugin", "formatter:format",
			"sortpom-maven-plugin", "sortpom:sort");

	private ModuleFactsCollector() {
	}

	/** Collects facts for every project, in reactor order. */
	public static List<ModuleFacts> collect(List<MavenProject> projects, Path root) {
		Set<String> reactor = new LinkedHashSet<>();
		for (MavenProject project : projects) {
			reactor.add(project.getGroupId() + ":" + project.getArtifactId());
		}
		List<ModuleFacts> facts = new ArrayList<>();
		for (MavenProject project : projects) {
			facts.add(collect(project, root, reactor));
		}
		return facts;
	}

	static ModuleFacts collect(MavenProject project, Path root, Set<String> reactor) {
		List<String> reactorDeps = new ArrayList<>();
		Set<String> testStack = new LinkedHashSet<>();
		for (Dependency dependency : project.getDependencies()) {
			String key = dependency.getGroupId() + ":" + dependency.getArtifactId();
			if (reactor.contains(key) && !reactorDeps.contains(dependency.getArtifactId())) {
				reactorDeps.add(dependency.getArtifactId());
			}
			String test = testLibrary(dependency.getGroupId(), dependency.getArtifactId());
			if (test != null) {
				testStack.add(test);
			}
		}
		Set<String> checks = new LinkedHashSet<>();
		Set<String> formatters = new LinkedHashSet<>();
		boolean integrationTests = false;
		boolean springBoot = false;
		for (Plugin plugin : project.getBuildPlugins()) {
			String artifactId = plugin.getArtifactId();
			boolean bound = !plugin.getExecutions().isEmpty();
			if (bound && CHECKS.containsKey(artifactId)) {
				checks.add(CHECKS.get(artifactId));
			}
			if (bound && "jacoco-maven-plugin".equals(artifactId) && hasGoal(plugin, "check")) {
				checks.add("JaCoCo coverage");
			}
			if (bound && "maven-dependency-plugin".equals(artifactId) && hasGoal(plugin, "analyze-only")) {
				checks.add("dependency analysis");
			}
			if (FORMATTERS.containsKey(artifactId)) {
				formatters.add(FORMATTERS.get(artifactId));
			}
			if ("maven-failsafe-plugin".equals(artifactId) && bound) {
				integrationTests = true;
			}
			if ("spring-boot-maven-plugin".equals(artifactId)) {
				springBoot = true;
			}
		}
		List<ModuleFacts.Profile> profiles = new ArrayList<>();
		for (org.apache.maven.model.Profile profile : project.getModel().getProfiles()) {
			profiles.add(new ModuleFacts.Profile(profile.getId(), activation(profile.getActivation())));
		}
		String path = root.relativize(project.getBasedir().toPath().toAbsolutePath().normalize()).toString();
		return new ModuleFacts(project.getGroupId(), project.getArtifactId(), project.getVersion(),
				project.getPackaging(), project.getName(), path.isEmpty() ? "." : path.replace('\\', '/'),
				javaRelease(project), reactorDeps, new ArrayList<>(checks), new ArrayList<>(formatters),
				new ArrayList<>(testStack), integrationTests, springBoot, profiles);
	}

	private static boolean hasGoal(Plugin plugin, String goal) {
		for (PluginExecution execution : plugin.getExecutions()) {
			if (execution.getGoals().contains(goal)) {
				return true;
			}
		}
		return false;
	}

	static String testLibrary(String groupId, String artifactId) {
		if ("org.junit.jupiter".equals(groupId) || "org.junit".equals(groupId) && "junit-bom".equals(artifactId)) {
			return "JUnit 5";
		}
		if ("junit".equals(groupId) && "junit".equals(artifactId)) {
			return "JUnit 4";
		}
		if ("org.testng".equals(groupId)) {
			return "TestNG";
		}
		if ("org.spockframework".equals(groupId)) {
			return "Spock";
		}
		if ("org.mockito".equals(groupId)) {
			return "Mockito";
		}
		if ("org.assertj".equals(groupId)) {
			return "AssertJ";
		}
		if ("org.hamcrest".equals(groupId)) {
			return "Hamcrest";
		}
		if ("org.testcontainers".equals(groupId)) {
			return "Testcontainers";
		}
		if ("spring-boot-starter-test".equals(artifactId)) {
			return "Spring Boot Test (JUnit 5, Mockito, AssertJ)";
		}
		return null;
	}

	static String javaRelease(MavenProject project) {
		Plugin compiler = project.getPlugin("org.apache.maven.plugins:maven-compiler-plugin");
		if (compiler != null && compiler.getConfiguration() instanceof Xpp3Dom configuration) {
			for (String name : new String[] { "release", "target", "source" }) {
				Xpp3Dom child = configuration.getChild(name);
				if (child != null && child.getValue() != null && !child.getValue().contains("${")) {
					return child.getValue().trim();
				}
			}
		}
		Properties properties = project.getProperties();
		for (String name : new String[] { "maven.compiler.release", "maven.compiler.target", "maven.compiler.source",
				"java.version" }) {
			String value = properties.getProperty(name);
			if (value != null && !value.isBlank() && !value.contains("${")) {
				return value.trim();
			}
		}
		return null;
	}

	static String activation(Activation activation) {
		if (activation == null) {
			return "manual (-P)";
		}
		List<String> parts = new ArrayList<>();
		if (activation.isActiveByDefault()) {
			parts.add("by default");
		}
		ActivationProperty property = activation.getProperty();
		if (property != null && property.getName() != null) {
			parts.add("-D" + property.getName() + ((property.getValue() != null) ? "=" + property.getValue() : ""));
		}
		if (activation.getJdk() != null) {
			parts.add("JDK " + activation.getJdk());
		}
		if (activation.getOs() != null) {
			parts.add("OS " + ((activation.getOs().getFamily() != null) ? activation.getOs().getFamily()
					: activation.getOs().getName()));
		}
		if (activation.getFile() != null) {
			String file = (activation.getFile().getExists() != null) ? "file exists " + activation.getFile().getExists()
					: "file missing " + activation.getFile().getMissing();
			parts.add(file);
		}
		return parts.isEmpty() ? "manual (-P)" : String.join(", ", parts);
	}

}
