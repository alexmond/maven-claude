package org.alexmond.maven.claude.output;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutputModeTest {

	private static final Map<String, String> CLAUDE = Map.of("CLAUDECODE", "1");

	private static final Map<String, String> PLAIN = Map.of();

	@Test
	void autoFollowsClaudeCodeEnvironment() {
		assertEquals(OutputMode.SUMMARY, OutputMode.resolve(null, null, CLAUDE));
		assertEquals(OutputMode.OFF, OutputMode.resolve(null, null, PLAIN));
		assertEquals(OutputMode.OFF, OutputMode.resolve("auto", null, PLAIN));
	}

	@Test
	void explicitValuesWin() {
		assertEquals(OutputMode.OFF, OutputMode.resolve("off", null, CLAUDE));
		assertEquals(OutputMode.REPORT, OutputMode.resolve("report", null, CLAUDE));
		assertEquals(OutputMode.SUMMARY, OutputMode.resolve("summary", null, PLAIN));
		assertEquals(OutputMode.SUMMARY, OutputMode.resolve(" TRUE ", null, PLAIN));
	}

	@Test
	void userPropertyBeatsSystemPropertyBeatsEnvironment() {
		assertEquals(OutputMode.REPORT, OutputMode.resolve("report", "off", Map.of(OutputMode.ENV, "summary")));
		assertEquals(OutputMode.OFF, OutputMode.resolve(" ", "off", Map.of(OutputMode.ENV, "summary")));
		assertEquals(OutputMode.SUMMARY, OutputMode.resolve(null, null, Map.of(OutputMode.ENV, "summary")));
	}

	@Test
	void unknownValueFallsBackToAuto() {
		assertEquals(OutputMode.SUMMARY, OutputMode.resolve("banana", null, CLAUDE));
		assertEquals(OutputMode.OFF, OutputMode.resolve("banana", null, PLAIN));
	}

}
