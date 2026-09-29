package org.alexmond.maven.claude.output;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * Redirects {@code System.out}/{@code System.err} through a {@link LineRouter}.
 * <p>
 * Maven 3.9 configures its SLF4J simple logger with {@code cacheOutputStream=true}, so
 * the logger keeps writing to the stream it saw at startup no matter what
 * {@code System.setOut} does later. {@link #install} therefore re-initialises the
 * logger's configuration with caching off (every other setting — level, format — is
 * re-read from the same {@code simplelogger.properties} and system properties). If that
 * isn't possible (a different logging backend, e.g. Maven 4), nothing is redirected and
 * the build output stays untouched.
 */
public final class ConsoleCapture {

	private static final String SIMPLE_LOGGER = "org.slf4j.impl.SimpleLogger";

	private final PrintStream realOut;

	private final PrintStream realErr;

	private final CapturingOutputStream out;

	private final CapturingOutputStream err;

	private ConsoleCapture(PrintStream realOut, PrintStream realErr, CapturingOutputStream out,
			CapturingOutputStream err) {
		this.realOut = realOut;
		this.realErr = realErr;
		this.out = out;
		this.err = err;
	}

	/**
	 * Installs the capture, or returns {@code null} when Maven's logger can't be pointed
	 * at the redirected stream.
	 */
	public static ConsoleCapture install(LineRouter router) {
		if (!makeMavenLoggerFollowSystemOut()) {
			return null;
		}
		Charset charset = Charset.defaultCharset();
		PrintStream realOut = System.out;
		PrintStream realErr = System.err;
		CapturingOutputStream out = new CapturingOutputStream(realOut, router, charset);
		CapturingOutputStream err = new CapturingOutputStream(realErr, router, charset);
		System.setOut(new PrintStream(out, true, charset));
		System.setErr(new PrintStream(err, true, charset));
		return new ConsoleCapture(realOut, realErr, out, err);
	}

	/** The console as it was before the capture was installed. */
	public PrintStream realOut() {
		return this.realOut;
	}

	/**
	 * Whether the console is in the middle of a line (e.g. after -DforceStdout output).
	 */
	public boolean midLine() {
		return this.out.midLine() || this.err.midLine();
	}

	/** Emits the current thread's partial lines (call when a mojo finishes). */
	public void flushCurrentThread() {
		System.out.flush();
		System.err.flush();
		this.out.flushPending();
		this.err.flushPending();
	}

	/** Restores the original streams. */
	public void uninstall() {
		flushCurrentThread();
		System.setOut(this.realOut);
		System.setErr(this.realErr);
	}

	@SuppressWarnings("PMD.AvoidAccessibilityAlteration") // the whole point: reach
															// Maven's logger init
	static boolean makeMavenLoggerFollowSystemOut() {
		try {
			ILoggerFactory factory = LoggerFactory.getILoggerFactory();
			ClassLoader loader = factory.getClass().getClassLoader();
			Class<?> simpleLogger = Class.forName(SIMPLE_LOGGER, false, loader);
			if (!simpleLogger.isAssignableFrom(LoggerFactory.getLogger(ConsoleCapture.class).getClass())) {
				return false;
			}
			Method init = simpleLogger.getDeclaredMethod("init");
			init.setAccessible(true);
			System.setProperty("org.slf4j.simpleLogger.cacheOutputStream", "false");
			// init() re-reads simplelogger.properties through the context class loader;
			// make
			// sure it is the one that can see Maven's conf/logging directory.
			Thread thread = Thread.currentThread();
			ClassLoader previous = thread.getContextClassLoader();
			thread.setContextClassLoader(loader);
			try {
				init.invoke(null);
			}
			finally {
				thread.setContextClassLoader(previous);
			}
			return true;
		}
		catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			return false;
		}
	}

}
