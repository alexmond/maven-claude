package org.alexmond.maven.claude.output;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerMessageParserTest {

	private static final Path ROOT = Path.of("/work/project");

	@Test
	void parsesJavacErrorWithSymbolLines() {
		List<Problem> problems = CompilerMessageParser.parse(
				List.of("[INFO] -------------", "[ERROR] COMPILATION ERROR : ",
						"[ERROR] /work/project/core/src/main/java/a/Foo.java:[88,17] cannot find symbol",
						"  symbol:   method total(java.util.List)", "  location: class a.Foo", "[INFO] 1 error"),
				ROOT, "core");
		assertEquals(1, problems.size());
		Problem problem = problems.get(0);
		assertEquals(Problem.Kind.COMPILE, problem.kind());
		assertEquals("core/src/main/java/a/Foo.java:88:17", problem.location());
		assertEquals("cannot find symbol", problem.title());
		assertEquals(List.of("symbol:   method total(java.util.List)", "location: class a.Foo"), problem.detail());
		assertEquals("core", problem.module());
	}

	@Test
	void deduplicatesRepeatedErrors() {
		String line = "[ERROR] /work/project/A.java:[1,1] boom";
		assertEquals(1, CompilerMessageParser.parse(List.of(line, line), ROOT, "m").size());
	}

	@Test
	void parsesExceptionLongMessageWithoutPrefix() {
		List<Problem> problems = CompilerMessageParser.parse(List.of("/work/project/A.java:[3] ';' expected"), ROOT,
				"m");
		assertEquals("A.java:3", problems.get(0).location());
		assertEquals("';' expected", problems.get(0).title());
	}

	@Test
	void parsesKotlinErrors() {
		List<Problem> problems = CompilerMessageParser.parse(
				List.of("[ERROR] e: file:///work/project/src/main/kotlin/A.kt:12:5 Unresolved reference: foo"), ROOT,
				"m");
		assertEquals("src/main/kotlin/A.kt:12:5", problems.get(0).location());
		assertEquals("Unresolved reference: foo", problems.get(0).title());
	}

	@Test
	void keepsPathsOutsideTheRootAbsolute() {
		List<Problem> problems = CompilerMessageParser.parse(List.of("[ERROR] /elsewhere/B.java:[2,4] nope"), ROOT,
				"m");
		assertEquals("/elsewhere/B.java:2:4", problems.get(0).location());
	}

	@Test
	void ignoresWarningsAndOtherLines() {
		assertTrue(CompilerMessageParser
			.parse(List.of("[WARNING] /work/project/A.java:[1,1] deprecated", "[INFO] Compiling 3 source files"), ROOT,
					"m")
			.isEmpty());
	}

}
