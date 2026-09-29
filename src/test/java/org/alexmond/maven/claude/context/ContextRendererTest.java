package org.alexmond.maven.claude.context;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextRendererTest {

	@Test
	void multiModuleContextHasModulesCommandsChecksAndProfiles() {
		ModuleFacts root = new ModuleFacts("org.acme", "shop", "1.0", "pom", "Shop", ".", "17", List.of(), List.of(),
				List.of(), List.of(), false, false, List.of(new ModuleFacts.Profile("release", "manual (-P)")));
		ModuleFacts app = new ModuleFacts("org.acme", "app", "1.0", "jar", null, "services/app", null, List.of("core"),
				List.of("Checkstyle", "Enforcer"), List.of("spotless:apply"), List.of("JUnit 5"), true, true,
				List.of());
		String md = ContextRenderer.render(List.of(root, app), "./mvnw");

		assertTrue(md.startsWith(ContextRenderer.MARKER), md);
		assertTrue(md.contains("# Maven project: Shop (`org.acme:shop:1.0`)"), md);
		assertTrue(md.contains("Build with `./mvnw` (Maven wrapper) · Java 17 · 2 modules · tests: JUnit 5"), md);
		assertTrue(md.contains("| app | `services/app` | jar | core |"), md);
		assertTrue(md.contains("`./mvnw -q -pl services/app -am verify`"), md);
		assertTrue(md.contains("-Dsurefire.failIfNoSpecifiedTests=false"), md);
		assertTrue(md.contains("| One integration test |"), md);
		assertTrue(md.contains("| Fix formatting | `./mvnw spotless:apply` |"), md);
		assertTrue(md.contains("| Run the app | `./mvnw -pl services/app spring-boot:run` |"), md);
		assertTrue(md.contains("Checkstyle, Enforcer."), md);
		assertTrue(md.contains("| release | shop | manual (-P) |"), md);
	}

	@Test
	void singleModuleContextIsCompact() {
		ModuleFacts only = new ModuleFacts("org.acme", "lib", "1.0", "jar", "", ".", null, List.of(), List.of(),
				List.of(), List.of(), false, false, List.of());
		String md = ContextRenderer.render(List.of(only), "mvn");
		assertTrue(md.contains("# Maven project: lib (`org.acme:lib:1.0`)"), md);
		assertTrue(md.contains("Build with `mvn` · packaging `jar`"), md);
		assertTrue(md.contains("`mvn -q test -Dtest='FooTest#bar'`"), md);
		assertFalse(md.contains("## Modules"), md);
		assertFalse(md.contains("## Profiles"), md);
		assertFalse(md.contains("## Checks"), md);
	}

}
