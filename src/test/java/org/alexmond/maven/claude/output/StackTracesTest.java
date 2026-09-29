package org.alexmond.maven.claude.output;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StackTracesTest {

	private final SourceIndex nothingIsProject = new SourceIndex(List.of(), null);

	@Test
	void shortensQualifiedExceptionTypes() {
		assertEquals("IllegalStateException: x", StackTraces.shortenType("java.lang.IllegalStateException: x"));
		assertEquals("Boom", StackTraces.shortenType("a.b.Boom"));
		assertEquals("expected: <1> but was: <2>", StackTraces.shortenType("expected: <1> but was: <2>"));
	}

	@Test
	void keepsMultiLineMessagesUntilTheFirstFrame() {
		StackTraces.Trimmed trimmed = StackTraces.trim("""
				org.opentest4j.MultipleFailuresError: Multiple Failures (2 failures)
					first failure
					second failure
					at org.junit.Foo.bar(Foo.java:1)
				""", this.nothingIsProject, 5);
		assertEquals(
				List.of("MultipleFailuresError: Multiple Failures (2 failures)", "\tfirst failure", "\tsecond failure"),
				trimmed.message());
		assertEquals(List.of(), trimmed.frames());
		assertNull(trimmed.rootCause());
	}

	@Test
	void frameFormatting() {
		StackTraces.Frame frame = new StackTraces.Frame("a.b.Outer$Inner", "run", "src/a/b/Outer.java", "12");
		assertEquals("at Outer$Inner.run(src/a/b/Outer.java:12)", frame.toString());
		assertEquals("src/a/B.java", new StackTraces.Frame("a.B", "m", "src/a/B.java", null).location());
	}

}
