package org.alexmond.maven.claude.output;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapturingOutputStreamTest {

	private final ByteArrayOutputStream console = new ByteArrayOutputStream();

	private final List<String> lines = new ArrayList<>();

	private boolean passthrough;

	private final CapturingOutputStream stream = new CapturingOutputStream(
			new PrintStream(this.console, true, StandardCharsets.UTF_8), new LineRouter() {
				@Override
				public boolean passthrough() {
					return CapturingOutputStreamTest.this.passthrough;
				}

				@Override
				public void line(String line) {
					CapturingOutputStreamTest.this.lines.add(line);
				}
			}, StandardCharsets.UTF_8);

	@Test
	void splitsLinesAndStripsAnsiAndCarriageReturns() {
		write("\u001B[1;31m[ERROR]\u001B[m boom\r\nsecond\npart");
		assertEquals(List.of("[ERROR] boom", "second"), this.lines);
		this.stream.flushPending();
		assertEquals(List.of("[ERROR] boom", "second", "part"), this.lines);
		assertEquals("", this.console.toString(StandardCharsets.UTF_8));
	}

	@Test
	void passthroughCopiesBytesImmediatelyAndTracksPartialLines() {
		this.passthrough = true;
		write("1.0");
		assertEquals("1.0", this.console.toString(StandardCharsets.UTF_8));
		assertTrue(this.stream.midLine());
		write("\n");
		assertFalse(this.stream.midLine());
		assertEquals(List.of("1.0"), this.lines);
	}

	@Test
	void singleByteWrites() {
		for (byte b : "ab\n".getBytes(StandardCharsets.UTF_8)) {
			this.stream.write(b);
		}
		assertEquals(List.of("ab"), this.lines);
	}

	@Test
	void stripAnsiLeavesPlainTextAlone() {
		assertEquals("plain", CapturingOutputStream.stripAnsi("plain"));
	}

	private void write(String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		this.stream.write(bytes, 0, bytes.length);
	}

}
