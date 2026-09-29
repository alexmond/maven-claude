package org.alexmond.maven.claude;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitFilesTest {

	@TempDir
	Path dir;

	@Test
	void createsThenLeavesThenUpdatesExtension() throws IOException {
		Path file = this.dir.resolve(".mvn/extensions.xml");
		assertEquals(InitFiles.Change.CREATED, InitFiles.ensureExtension(file, "0.1.0"));
		assertTrue(Files.readString(file).contains("<version>0.1.0</version>"));
		assertEquals(InitFiles.Change.UNCHANGED, InitFiles.ensureExtension(file, "0.1.0"));
		assertEquals(InitFiles.Change.UPDATED, InitFiles.ensureExtension(file, "0.2.0"));
		String xml = Files.readString(file);
		assertTrue(xml.contains("<version>0.2.0</version>") && !xml.contains("0.1.0"), xml);
	}

	@Test
	void addsExtensionNextToExistingOnes() throws IOException {
		Path file = this.dir.resolve("extensions.xml");
		Files.writeString(file, "<extensions>\n    <extension><groupId>x</groupId><artifactId>y</artifactId>"
				+ "<version>1</version></extension>\n</extensions>\n");
		assertEquals(InitFiles.Change.ADDED, InitFiles.ensureExtension(file, "0.1.0"));
		String xml = Files.readString(file);
		assertTrue(xml.contains("<artifactId>y</artifactId>"), xml);
		assertTrue(xml.indexOf("claude-maven-plugin") < xml.indexOf("</extensions>"), xml);
	}

	@Test
	void rejectsFileWithoutExtensionsElement() throws IOException {
		Path file = this.dir.resolve("broken.xml");
		Files.writeString(file, "<nope/>");
		assertThrows(IOException.class, () -> InitFiles.ensureExtension(file, "0.1.0"));
	}

	@Test
	void mergesPermissionsKeepingOtherSettings() throws IOException {
		Path file = this.dir.resolve(".claude/settings.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "{\"model\": \"opus\", \"permissions\": {\"allow\": [\"Bash(git *)\"], \"deny\": []}}");
		List<String> added = InitFiles.allowPermissions(file, List.of("Bash(git *)", "Bash(mvn *)"));
		assertEquals(List.of("Bash(mvn *)"), added);
		JsonNode json = new ObjectMapper().readTree(file.toFile());
		assertEquals("opus", json.get("model").asText());
		assertEquals(2, json.get("permissions").get("allow").size());
		assertTrue(Files.readString(file).contains("\"model\": \"opus\""));
		assertEquals(List.of(), InitFiles.allowPermissions(file, List.of("Bash(mvn *)")));
	}

	@Test
	void createsSettingsWhenMissing() throws IOException {
		Path file = this.dir.resolve("new/settings.json");
		assertEquals(List.of("Bash(mvn *)"), InitFiles.allowPermissions(file, List.of("Bash(mvn *)")));
		assertEquals("Bash(mvn *)",
				new ObjectMapper().readTree(file.toFile()).get("permissions").get("allow").get(0).asText());
	}

	@Test
	void rejectsNonObjectSettings() throws IOException {
		Path file = this.dir.resolve("array.json");
		Files.writeString(file, "[]");
		assertThrows(IOException.class, () -> InitFiles.allowPermissions(file, List.of("Bash(mvn *)")));
	}

	@Test
	void importsContextIntoClaudeMdOnce() throws IOException {
		Path claudeMd = this.dir.resolve("CLAUDE.md");
		assertEquals(InitFiles.Change.SKIPPED, InitFiles.importContext(claudeMd, "mvn"));
		Files.writeString(claudeMd, "# Project");
		assertEquals(InitFiles.Change.ADDED, InitFiles.importContext(claudeMd, "./mvnw"));
		String content = Files.readString(claudeMd);
		assertTrue(content.startsWith("# Project\n\n## Maven\n"), content);
		assertTrue(content.endsWith("@.claude/maven-context.md\n"), content);
		assertEquals(InitFiles.Change.UNCHANGED, InitFiles.importContext(claudeMd, "./mvnw"));
	}

}
