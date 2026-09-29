package org.alexmond.maven.claude.output;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JsonReportWriterTest {

	@Test
	void writesValidJsonThatRoundTrips() throws Exception {
		BuildReport report = new BuildReport(
				false, 1234, 2, 1, 1, 0, new BuildReport.Tests(5, 1, 0, 0), List.of(new Problem(Problem.Kind.TEST,
						"core", "ATest.\"quoted\"", "a/ATest.java:3", List.of("line\twith tab", "back\\slash\u0001"))),
				"note");
		JsonNode json = new ObjectMapper().readTree(JsonReportWriter.write(report, "target/claude/build.log"));
		assertFalse(json.get("success").asBoolean());
		assertEquals(1234, json.get("durationMillis").asLong());
		assertEquals(2, json.get("modules").get("total").asInt());
		assertEquals(1, json.get("tests").get("failed").asInt());
		assertEquals("target/claude/build.log", json.get("log").asText());
		JsonNode problem = json.get("problems").get(0);
		assertEquals("TEST", problem.get("kind").asText());
		assertEquals("ATest.\"quoted\"", problem.get("title").asText());
		assertEquals("back\\slash\u0001", problem.get("detail").get(1).asText());
	}

	@Test
	void emptyProblemsAndNoTests() throws Exception {
		BuildReport report = new BuildReport(true, 1, 1, 1, 0, 0, null, List.of(), null);
		JsonNode json = new ObjectMapper().readTree(JsonReportWriter.write(report, null));
		assertEquals(0, json.get("problems").size());
		assertFalse(json.has("tests"));
		assertFalse(json.has("log"));
	}

}
