package org.alexmond.maven.claude.context;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationFile;
import org.apache.maven.model.ActivationOS;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleFactsCollectorTest {

	private static final Path ROOT = Path.of("/work/shop").toAbsolutePath();

	@Test
	void collectsReactorDependenciesChecksAndTestStack() {
		MavenProject parent = project("shop", "pom", ".");
		MavenProject core = project("core", "jar", "core");
		MavenProject app = project("app", "jar", "app");
		app.getModel().addDependency(dependency("org.acme", "core"));
		app.getModel().addDependency(dependency("org.junit.jupiter", "junit-jupiter"));
		app.getModel().addDependency(dependency("org.mockito", "mockito-core"));
		app.getBuild().addPlugin(plugin("maven-checkstyle-plugin", "check"));
		app.getBuild().addPlugin(plugin("jacoco-maven-plugin", "check"));
		app.getBuild().addPlugin(plugin("maven-failsafe-plugin", "integration-test"));
		app.getBuild().addPlugin(plugin("spotless-maven-plugin", "check"));
		app.getBuild().addPlugin(plugin("spring-boot-maven-plugin"));
		app.getBuild().addPlugin(plugin("maven-pmd-plugin"));
		Profile profile = new Profile();
		profile.setId("release");
		app.getModel().addProfile(profile);

		List<ModuleFacts> facts = ModuleFactsCollector.collect(List.of(parent, core, app), ROOT);

		assertEquals(".", facts.get(0).path());
		ModuleFacts appFacts = facts.get(2);
		assertEquals("app", appFacts.path());
		assertEquals(List.of("core"), appFacts.reactorDependencies());
		assertEquals(List.of("JUnit 5", "Mockito"), appFacts.testStack());
		assertEquals(List.of("Checkstyle", "JaCoCo coverage", "Spotless"), appFacts.checks());
		assertEquals(List.of("spotless:apply"), appFacts.formatCommands());
		assertTrue(appFacts.integrationTests());
		assertTrue(appFacts.springBoot());
		assertEquals(List.of(new ModuleFacts.Profile("release", "manual (-P)")), appFacts.profiles());
		assertFalse(facts.get(1).springBoot());
		assertEquals("org.acme:app:1.0", appFacts.coordinates());
	}

	@Test
	void javaReleaseFromCompilerConfigThenProperties() {
		MavenProject project = project("a", "jar", "a");
		assertNull(ModuleFactsCollector.javaRelease(project));
		project.getProperties().setProperty("java.version", "17");
		assertEquals("17", ModuleFactsCollector.javaRelease(project));
		project.getProperties().setProperty("maven.compiler.release", "21");
		assertEquals("21", ModuleFactsCollector.javaRelease(project));
		project = project("b", "jar", "b");
		Plugin compiler = plugin("maven-compiler-plugin");
		Xpp3Dom configuration = new Xpp3Dom("configuration");
		Xpp3Dom release = new Xpp3Dom("release");
		release.setValue("25");
		configuration.addChild(release);
		compiler.setConfiguration(configuration);
		project.getBuild().addPlugin(compiler);
		assertEquals("25", ModuleFactsCollector.javaRelease(project));
	}

	@Test
	void describesActivation() {
		assertEquals("manual (-P)", ModuleFactsCollector.activation(null));
		Activation activation = new Activation();
		activation.setActiveByDefault(true);
		ActivationProperty property = new ActivationProperty();
		property.setName("ci");
		property.setValue("true");
		activation.setProperty(property);
		activation.setJdk("[21,)");
		ActivationOS os = new ActivationOS();
		os.setFamily("windows");
		activation.setOs(os);
		ActivationFile file = new ActivationFile();
		file.setMissing("local.properties");
		activation.setFile(file);
		assertEquals("by default, -Dci=true, JDK [21,), OS windows, file missing local.properties",
				ModuleFactsCollector.activation(activation));
		assertEquals("manual (-P)", ModuleFactsCollector.activation(new Activation()));
	}

	@Test
	void recognisesTestLibraries() {
		assertEquals("JUnit 4", ModuleFactsCollector.testLibrary("junit", "junit"));
		assertEquals("TestNG", ModuleFactsCollector.testLibrary("org.testng", "testng"));
		assertEquals("AssertJ", ModuleFactsCollector.testLibrary("org.assertj", "assertj-core"));
		assertEquals("Spock", ModuleFactsCollector.testLibrary("org.spockframework", "spock-core"));
		assertEquals("Hamcrest", ModuleFactsCollector.testLibrary("org.hamcrest", "hamcrest"));
		assertEquals("Testcontainers", ModuleFactsCollector.testLibrary("org.testcontainers", "postgresql"));
		assertEquals("Spring Boot Test (JUnit 5, Mockito, AssertJ)",
				ModuleFactsCollector.testLibrary("org.springframework.boot", "spring-boot-starter-test"));
		assertNull(ModuleFactsCollector.testLibrary("com.google.guava", "guava"));
	}

	private static MavenProject project(String artifactId, String packaging, String path) {
		Model model = new Model();
		model.setGroupId("org.acme");
		model.setArtifactId(artifactId);
		model.setVersion("1.0");
		model.setPackaging(packaging);
		model.setBuild(new Build());
		MavenProject project = new MavenProject(model);
		project.setFile(new File(ROOT.resolve(path).toFile(), "pom.xml"));
		return project;
	}

	private static Dependency dependency(String groupId, String artifactId) {
		Dependency dependency = new Dependency();
		dependency.setGroupId(groupId);
		dependency.setArtifactId(artifactId);
		dependency.setVersion("1.0");
		return dependency;
	}

	private static Plugin plugin(String artifactId, String... goals) {
		Plugin plugin = new Plugin();
		plugin.setGroupId(artifactId.startsWith("maven-") ? "org.apache.maven.plugins" : "org.example");
		plugin.setArtifactId(artifactId);
		if (goals.length > 0) {
			PluginExecution execution = new PluginExecution();
			execution.setId("default");
			for (String goal : goals) {
				execution.addGoal(goal);
			}
			plugin.addExecution(execution);
		}
		return plugin;
	}

}
