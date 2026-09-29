package org.alexmond.maven.claude.output;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

/**
 * Splits written bytes into lines per writing thread and hands them to a
 * {@link LineRouter}. Bytes written while the router says "passthrough" are copied to the
 * real stream unchanged and immediately, so partial lines (e.g.
 * {@code help:evaluate -DforceStdout}) still reach the console.
 */
final class CapturingOutputStream extends OutputStream {

	private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]");

	private static final int MAX_LINE_BYTES = 64 * 1024;

	private final ThreadLocal<ByteArrayOutputStream> pending = ThreadLocal
		.withInitial(() -> new ByteArrayOutputStream(256));

	private final PrintStream real;

	private final LineRouter router;

	private final Charset charset;

	private volatile boolean midLine;

	CapturingOutputStream(PrintStream real, LineRouter router, Charset charset) {
		this.real = real;
		this.router = router;
		this.charset = charset;
	}

	@Override
	public void write(int b) {
		write(new byte[] { (byte) b }, 0, 1);
	}

	@Override
	public void write(byte[] bytes, int off, int len) {
		if (this.router.passthrough() && len > 0) {
			this.real.write(bytes, off, len);
			this.midLine = bytes[off + len - 1] != '\n';
		}
		ByteArrayOutputStream buffer = this.pending.get();
		int start = off;
		for (int i = off; i < off + len; i++) {
			if (bytes[i] == '\n') {
				buffer.write(bytes, start, i - start);
				emit(buffer);
				start = i + 1;
			}
		}
		buffer.write(bytes, start, off + len - start);
		if (buffer.size() > MAX_LINE_BYTES) {
			emit(buffer);
		}
	}

	@Override
	public void flush() {
		this.real.flush();
	}

	/** Whether the last passed-through output ended without a line break. */
	boolean midLine() {
		return this.midLine;
	}

	/** Emits whatever partial line the current thread has buffered. */
	void flushPending() {
		ByteArrayOutputStream buffer = this.pending.get();
		if (buffer.size() > 0) {
			emit(buffer);
		}
	}

	private void emit(ByteArrayOutputStream buffer) {
		String line = buffer.toString(this.charset);
		buffer.reset();
		if (line.endsWith("\r")) {
			line = line.substring(0, line.length() - 1);
		}
		this.router.line(stripAnsi(line));
	}

	static String stripAnsi(String line) {
		return (line.indexOf('\u001B') < 0) ? line : ANSI.matcher(line).replaceAll("");
	}

}
