package org.alexmond.maven.claude.output;

import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;

/**
 * Core-extension entry point. Loaded from {@code .mvn/extensions.xml}; decides the
 * {@link OutputMode}, captures the console, and prints the summary when Maven hands over
 * the execution result.
 * <p>
 * Everything here is defensive: a failure inside the extension must never change the
 * outcome of the user's build.
 */
@Named("claude")
@Singleton
public class ClaudeEventSpy extends AbstractEventSpy {

	private BuildRecorder recorder;

	private ConsoleCapture capture;

	private PrintStream console;

	@Override
	public void init(Context context) {
		try {
			Map<String, Object> data = context.getData();
			Properties user = properties(data.get("userProperties"));
			Properties system = properties(data.get("systemProperties"));
			OutputMode mode = OutputMode.resolve(user.getProperty(OutputMode.PROPERTY),
					system.getProperty(OutputMode.PROPERTY), System.getenv());
			if (mode == OutputMode.OFF) {
				return;
			}
			String reason = (mode == OutputMode.SUMMARY) ? reasonToKeepFullOutput() : null;
			if (reason != null) {
				mode = OutputMode.REPORT;
			}
			this.console = System.out;
			this.recorder = new BuildRecorder(mode);
			this.capture = ConsoleCapture.install(this.recorder);
			if (this.capture != null) {
				this.console = this.capture.realOut();
			}
			else if (mode == OutputMode.SUMMARY) {
				reason = "Maven's logger could not be redirected";
			}
			if (reason != null) {
				this.recorder.note("output not condensed: " + reason);
			}
			BuildRecorder shutdownRecorder = this.recorder;
			PrintStream shutdownConsole = this.console;
			Runtime.getRuntime()
				.addShutdownHook(new Thread(() -> shutdownRecorder.shutdown(shutdownConsole), "claude-maven-shutdown"));
		}
		catch (RuntimeException | LinkageError ex) {
			disable();
		}
	}

	@Override
	public void onEvent(Object event) {
		if (this.recorder == null) {
			return;
		}
		try {
			if (event instanceof MavenExecutionRequest request) {
				this.recorder.onRequest(request);
			}
			else if (event instanceof ExecutionEvent executionEvent) {
				this.recorder.onEvent(executionEvent, this.capture);
			}
			else if (event instanceof MavenExecutionResult result) {
				this.recorder.finish(result, this.console, this.capture != null && this.capture.midLine());
			}
		}
		catch (RuntimeException | LinkageError ex) {
			PrintStream out = this.console;
			disable();
			out.println("[claude] claude-maven-plugin disabled itself after an internal error: " + ex);
		}
	}

	private void disable() {
		if (this.capture != null) {
			this.capture.uninstall();
		}
		this.capture = null;
		this.recorder = null;
	}

	/**
	 * Cases where hiding output would do more harm than good; the build then runs with
	 * full output plus the summary.
	 */
	static String reasonToKeepFullOutput() {
		if (System.getProperty("mvnd.home") != null) {
			return "running in the Maven daemon";
		}
		String level = System.getProperty("org.slf4j.simpleLogger.defaultLogLevel", "");
		if ("debug".equalsIgnoreCase(level) || "trace".equalsIgnoreCase(level)) {
			return "debug logging (-X) requested";
		}
		String command = " " + System.getProperty("sun.java.command", "") + " ";
		if (command.contains(" -l ") || command.contains(" --log-file")) {
			return "a log file (-l) was requested";
		}
		return null;
	}

	private static Properties properties(Object value) {
		return (value instanceof Properties properties) ? properties : new Properties();
	}

}
