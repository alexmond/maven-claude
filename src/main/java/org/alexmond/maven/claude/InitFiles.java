package org.alexmond.maven.claude;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The small, idempotent file edits {@code claude:init} makes. Each edit leaves existing
 * content alone and only adds what is missing.
 */
final class InitFiles {

	private static final Pattern EXTENSION_VERSION = Pattern
		.compile("(<artifactId>\\s*" + PluginInfo.ARTIFACT_ID + "\\s*</artifactId>\\s*<version>)([^<]*)(</version>)");

	private static final String CONTEXT_IMPORT = "@.claude/maven-context.md";

	private InitFiles() {
	}

	/** Outcome of one edit. */
	enum Change {

		CREATED, ADDED, UPDATED, UNCHANGED, SKIPPED

	}

	/** Registers this plugin as a core extension in {@code .mvn/extensions.xml}. */
	static Change ensureExtension(Path file, String version) throws IOException {
		String block = "    <extension>\n" + "        <groupId>" + PluginInfo.GROUP_ID + "</groupId>\n"
				+ "        <artifactId>" + PluginInfo.ARTIFACT_ID + "</artifactId>\n" + "        <version>" + version
				+ "</version>\n" + "    </extension>\n";
		if (!Files.exists(file)) {
			Files.createDirectories(file.getParent());
			Files.writeString(file, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<extensions xmlns=\"http://maven.apache.org/EXTENSIONS/1.1.0\"\n"
					+ "            xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
					+ "            xsi:schemaLocation=\"http://maven.apache.org/EXTENSIONS/1.1.0 https://maven.apache.org/xsd/core-extensions-1.1.0.xsd\">\n"
					+ block + "</extensions>\n", StandardCharsets.UTF_8);
			return Change.CREATED;
		}
		String xml = Files.readString(file, StandardCharsets.UTF_8);
		Matcher matcher = EXTENSION_VERSION.matcher(xml);
		if (matcher.find()) {
			if (matcher.group(2).strip().equals(version)) {
				return Change.UNCHANGED;
			}
			Files.writeString(file, matcher.replaceFirst("$1" + Matcher.quoteReplacement(version) + "$3"),
					StandardCharsets.UTF_8);
			return Change.UPDATED;
		}
		int close = xml.lastIndexOf("</extensions>");
		if (close < 0) {
			throw new IOException(file + " has no </extensions> element");
		}
		Files.writeString(file, xml.substring(0, close) + block + xml.substring(close), StandardCharsets.UTF_8);
		return Change.ADDED;
	}

	/**
	 * Adds permission rules to {@code permissions.allow} in a Claude Code settings file.
	 * @return the rules that were not there before
	 */
	static List<String> allowPermissions(Path file, List<String> rules) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode settings;
		if (Files.exists(file)) {
			JsonNode existing = mapper.readTree(file.toFile());
			if (existing != null && !existing.isObject()) {
				throw new IOException(file + " is not a JSON object");
			}
			settings = (existing != null) ? (ObjectNode) existing : mapper.createObjectNode();
		}
		else {
			settings = mapper.createObjectNode();
		}
		ObjectNode permissions = (settings.get("permissions") instanceof ObjectNode node) ? node
				: settings.putObject("permissions");
		ArrayNode allow = (permissions.get("allow") instanceof ArrayNode node) ? node : permissions.putArray("allow");
		Set<String> present = new HashSet<>();
		allow.forEach((rule) -> present.add(rule.asText()));
		List<String> added = new ArrayList<>();
		for (String rule : rules) {
			if (present.add(rule)) {
				allow.add(rule);
				added.add(rule);
			}
		}
		if (!added.isEmpty()) {
			Files.createDirectories(file.getParent());
			DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
				.withSeparators(Separators.createDefaultInstance()
					.withObjectFieldValueSpacing(Separators.Spacing.AFTER)
					.withArrayEmptySeparator("")
					.withObjectEmptySeparator(""))
				.withObjectIndenter(new DefaultIndenter("  ", "\n"))
				.withArrayIndenter(new DefaultIndenter("  ", "\n"));
			Files.writeString(file, mapper.writer(printer).writeValueAsString(settings) + "\n", StandardCharsets.UTF_8);
		}
		return added;
	}

	/** Appends an import of the generated context to an existing CLAUDE.md. */
	static Change importContext(Path claudeMd, String mvn) throws IOException {
		if (!Files.exists(claudeMd)) {
			return Change.SKIPPED;
		}
		String content = Files.readString(claudeMd, StandardCharsets.UTF_8);
		if (content.contains("maven-context.md")) {
			return Change.UNCHANGED;
		}
		String section = (content.endsWith("\n") ? "" : "\n") + "\n## Maven\n\n"
				+ "Build facts (modules, commands, checks that fail the build) are generated by claude-maven-plugin; "
				+ "refresh with `" + mvn + " -q claude:context`.\n\n" + CONTEXT_IMPORT + "\n";
		Files.writeString(claudeMd, content + section, StandardCharsets.UTF_8);
		return Change.ADDED;
	}

}
