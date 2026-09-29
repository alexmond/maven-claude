package org.alexmond.maven.claude.output;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.execution.BuildFailure;
import org.apache.maven.execution.BuildSuccess;
import org.apache.maven.execution.BuildSummary;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Follows one build: which mojo is running on which thread, what each mojo logged, which
 * tests failed. Turns that into a {@link BuildReport} when the build ends and writes the
 * full log and the JSON report under {@code target/claude/}.
 */
final class BuildRecorder implements LineRouter {

	static final String OUTPUT_DIR = "target/claude";

	private static final int MAX_LOG_LINES = 500_000;

	private static final int MAX_MOJO_LINES = 2_000;

	private static final int MAX_GOAL_DETAIL = 15;

	private static final int MAX_EXCEPTION_LINES = 10;

	private static final int MAX_PROBLEMS_SHOWN = 20;

	private static final int FALLBACK_TAIL_LINES = 60;

	private final OutputMode mode;

	private final ThreadLocal<Deque<MojoRun>> running = ThreadLocal.withInitial(ArrayDeque::new);

	private final List<String> log = new ArrayList<>();

	private final List<Problem> problems = Collections.synchronizedList(new ArrayList<>());

	private final Set<String> failedModules = Collections.synchronizedSet(new HashSet<>());

	private final long start = System.currentTimeMillis();

	private String note;

	private Path root = Path.of("").toAbsolutePath();

	private MavenSession session;

	private int testsRun;

	private int testsFailed;

	private int testsErrored;

	private int testsSkipped;

	private boolean testsSeen;

	private long droppedLines;

	private Writer logWriter;

	private boolean finished;

	private volatile boolean lifecycleMojoRan;

	BuildRecorder(OutputMode mode) {
		this.mode = mode;
	}

	void note(String note) {
		this.note = note;
	}

	// ---- LineRouter ------------------------------------------------------------------

	@Override
	public boolean passthrough() {
		if (this.mode != OutputMode.SUMMARY) {
			return true;
		}
		MojoRun run = this.running.get().peek();
		return run != null && run.commandLine;
	}

	@Override
	public void line(String line) {
		synchronized (this) {
			if (this.logWriter != null) {
				try {
					this.logWriter.write(line);
					this.logWriter.write('\n');
				}
				catch (IOException ex) {
					// the log is best effort
				}
			}
			else if (this.log.size() < MAX_LOG_LINES) {
				this.log.add(line);
			}
			else {
				this.droppedLines++;
			}
		}
		MojoRun run = this.running.get().peek();
		if (run != null) {
			run.add(line);
		}
	}

	// ---- build events ----------------------------------------------------------------

	void onRequest(MavenExecutionRequest request) {
		File dir = request.getMultiModuleProjectDirectory();
		if (dir == null) {
			dir = (request.getBaseDirectory() != null) ? new File(request.getBaseDirectory()) : null;
		}
		if (dir != null) {
			this.root = dir.toPath().toAbsolutePath().normalize();
		}
	}

	void onEvent(ExecutionEvent event, ConsoleCapture capture) {
		switch (event.getType()) {
			case SessionStarted -> {
				this.session = event.getSession();
			}
			case MojoStarted -> {
				MojoRun run = new MojoRun(event.getProject(), event.getMojoExecution(), System.currentTimeMillis());
				this.lifecycleMojoRan |= !run.commandLine;
				this.running.get().push(run);
			}
			case MojoSucceeded, MojoFailed -> {
				if (capture != null) {
					capture.flushCurrentThread();
				}
				MojoRun run = this.running.get().poll();
				if (run != null) {
					mojoEnded(run, event,
							(event.getType() == ExecutionEvent.Type.MojoFailed) ? event.getException() : null);
				}
			}
			default -> {
				// other events carry nothing the report needs
			}
		}
	}

	private void mojoEnded(MojoRun run, ExecutionEvent event, Exception failure) {
		String module = run.project.getArtifactId();
		String plugin = run.execution.getArtifactId();
		String goal = run.execution.getGoal();
		boolean testGoal = ("maven-surefire-plugin".equals(plugin) && "test".equals(goal))
				|| ("maven-failsafe-plugin".equals(plugin) && "integration-test".equals(goal));
		int testProblems = 0;
		if (testGoal) {
			testProblems = collectTests(run, event.getSession(), module, plugin);
		}
		if (failure == null) {
			return;
		}
		this.failedModules.add(run.project.getId());
		if ("maven-compiler-plugin".equals(plugin) || plugin.contains("kotlin")) {
			List<Problem> compile = CompilerMessageParser.parse(run.lines(), this.root, module);
			if (compile.isEmpty() && failure instanceof MojoFailureException mfe && mfe.getLongMessage() != null) {
				compile = CompilerMessageParser.parse(List.of(mfe.getLongMessage().split("\\R")), this.root, module);
			}
			if (!compile.isEmpty()) {
				this.problems.addAll(compile);
				return;
			}
		}
		boolean verifyGoal = "maven-failsafe-plugin".equals(plugin) && "verify".equals(goal);
		if ((testGoal && testProblems > 0) || (verifyGoal && this.testsFailed + this.testsErrored > 0)) {
			return;
		}
		this.problems.add(goalProblem(run, failure, module));
	}

	private int collectTests(MojoRun run, MavenSession eventSession, String module, String plugin) {
		Path reports = reportsDirectory(run, eventSession, plugin);
		SurefireReports.Results results = SurefireReports.read(reports, run.start - 2_000);
		SourceIndex index = sourceIndex();
		synchronized (this) {
			this.testsSeen |= results.tests() > 0;
			this.testsRun += results.tests();
			this.testsFailed += results.failures();
			this.testsErrored += results.errors();
			this.testsSkipped += results.skipped();
		}
		for (SurefireReports.Failure failure : results.failed()) {
			this.problems.add(SurefireReports.toProblem(failure, index, module));
		}
		return results.failed().size();
	}

	private Path reportsDirectory(MojoRun run, MavenSession eventSession, String plugin) {
		Xpp3Dom configuration = run.execution.getConfiguration();
		Xpp3Dom child = (configuration != null) ? configuration.getChild("reportsDirectory") : null;
		if (child != null && eventSession != null) {
			String expression = (child.getValue() != null) ? child.getValue() : child.getAttribute("default-value");
			try {
				Object value = new PluginParameterExpressionEvaluator(eventSession, run.execution).evaluate(expression);
				if (value instanceof File file) {
					return file.toPath();
				}
				if (value instanceof String path && !path.isBlank()) {
					return run.project.getBasedir().toPath().resolve(path);
				}
			}
			catch (Exception ex) {
				// fall back to the default location
			}
		}
		String name = "maven-failsafe-plugin".equals(plugin) ? "failsafe-reports" : "surefire-reports";
		return Path.of(run.project.getBuild().getDirectory(), name);
	}

	private Problem goalProblem(MojoRun run, Throwable failure, String module) {
		MojoExecution execution = run.execution;
		String title = execution.getArtifactId() + ":" + execution.getGoal()
				+ (execution.getExecutionId().startsWith("default-") ? "" : " (" + execution.getExecutionId() + ")")
				+ " @ " + module;
		Set<String> detail = new LinkedHashSet<>(messageLines(failure, MAX_EXCEPTION_LINES));
		List<String> errors = linesAtLevel(run.lines(), "[ERROR]");
		for (String line : errors.isEmpty() ? linesAtLevel(run.lines(), "[WARNING]") : errors) {
			if (detail.size() >= MAX_GOAL_DETAIL) {
				break;
			}
			detail.add(line);
		}
		return new Problem(Problem.Kind.GOAL, module, title, null, new ArrayList<>(detail));
	}

	private List<String> linesAtLevel(List<String> lines, String level) {
		List<String> result = new ArrayList<>();
		for (String line : lines) {
			if (line.startsWith(level)) {
				String text = shorten(line.substring(level.length()).strip());
				if (!text.isEmpty() && !text.startsWith("->") && !text.startsWith("To see the full stack trace")) {
					result.add(text);
				}
			}
		}
		return result;
	}

	private List<String> messageLines(Throwable failure, int max) {
		Set<String> lines = new LinkedHashSet<>();
		Set<Throwable> seen = new HashSet<>();
		for (Throwable t = failure; t != null && seen.add(t) && lines.size() < max; t = t.getCause()) {
			if (t instanceof LifecycleExecutionException && t.getCause() != null) {
				continue;
			}
			String message = t.getMessage();
			if (message == null || message.isBlank()) {
				continue;
			}
			for (String line : message.split("\\R")) {
				if (!line.isBlank() && lines.size() < max) {
					lines.add(shorten(line.strip()));
				}
			}
		}
		return new ArrayList<>(lines);
	}

	private String shorten(String text) {
		String prefix = this.root.toString() + File.separator;
		return text.replace(prefix, "");
	}

	/** Built fresh each time: source roots grow as modules generate sources. */
	private SourceIndex sourceIndex() {
		List<Path> roots = new ArrayList<>();
		if (this.session != null) {
			for (MavenProject project : this.session.getProjects()) {
				project.getCompileSourceRoots().forEach((root) -> roots.add(Path.of(root)));
				project.getTestCompileSourceRoots().forEach((root) -> roots.add(Path.of(root)));
			}
		}
		return new SourceIndex(roots, this.root);
	}

	// ---- end of build ----------------------------------------------------------------

	/** Builds the report from Maven's result, writes the files, prints the summary. */
	void finish(MavenExecutionResult result, PrintStream console, boolean consoleMidLine) {
		BuildReport report = buildReport(result);
		Path dir = this.root.resolve(OUTPUT_DIR);
		Path logFile = dir.resolve("build.log");
		Path jsonFile = dir.resolve("build-report.json");
		boolean written = writeFiles(dir, logFile, jsonFile, report);
		String summary = SummaryRenderer.render(report, written ? display(logFile) : null,
				written ? display(jsonFile) : null, MAX_PROBLEMS_SHOWN);
		// Only goals typed on the command line ran (help:evaluate, dependency:tree) and
		// they
		// worked: their output is the answer, and scripts may be capturing it.
		boolean silent = report.success() && !this.lifecycleMojoRan;
		if (!silent) {
			if (consoleMidLine) {
				console.println();
			}
			if (this.mode == OutputMode.REPORT) {
				console.println();
				console.println("----- claude-maven-plugin summary -----");
			}
			console.print(summary);
			console.flush();
		}
		synchronized (this) {
			this.finished = true;
		}
	}

	BuildReport buildReport(MavenExecutionResult result) {
		List<MavenProject> projects = (result.getTopologicallySortedProjects() != null)
				? result.getTopologicallySortedProjects() : List.of();
		int ok = 0;
		int failed = 0;
		for (MavenProject project : projects) {
			BuildSummary summary = result.getBuildSummary(project);
			if (summary instanceof BuildSuccess) {
				ok++;
			}
			else if (summary instanceof BuildFailure) {
				failed++;
			}
		}
		List<Problem> all = new ArrayList<>(this.problems);
		for (Throwable exception : result.getExceptions()) {
			if (exception instanceof LifecycleExecutionException lee && lee.getProject() != null
					&& this.failedModules.contains(lee.getProject().getId())) {
				continue;
			}
			List<String> lines = messageLines(exception, MAX_EXCEPTION_LINES);
			String module = (exception instanceof LifecycleExecutionException lee && lee.getProject() != null)
					? lee.getProject().getArtifactId() : null;
			String title = lines.isEmpty() ? exception.getClass().getSimpleName() : lines.remove(0);
			all.add(new Problem(Problem.Kind.BUILD, module, title, null, lines));
		}
		BuildReport.Tests tests;
		synchronized (this) {
			tests = this.testsSeen
					? new BuildReport.Tests(this.testsRun, this.testsFailed, this.testsErrored, this.testsSkipped)
					: null;
		}
		return new BuildReport(!result.hasExceptions(), System.currentTimeMillis() - this.start, projects.size(), ok,
				failed, projects.size() - ok - failed, tests, all, this.note);
	}

	private boolean writeFiles(Path dir, Path logFile, Path jsonFile, BuildReport report) {
		try {
			Files.createDirectories(dir);
			Files.writeString(jsonFile, JsonReportWriter.write(report, display(logFile)), StandardCharsets.UTF_8);
			synchronized (this) {
				BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8);
				for (String line : this.log) {
					writer.write(line);
					writer.write('\n');
				}
				if (this.droppedLines > 0) {
					writer.write("[claude] " + this.droppedLines + " further lines were not kept\n");
				}
				this.log.clear();
				// Keep appending: Maven prints its own result block after this point.
				this.logWriter = writer;
			}
			return true;
		}
		catch (IOException ex) {
			return false;
		}
	}

	private String display(Path file) {
		Path cwd = Path.of("").toAbsolutePath();
		return file.startsWith(cwd) ? cwd.relativize(file).toString() : file.toString();
	}

	/**
	 * JVM shutdown: close the log, and if the build never reached {@link #finish} (it
	 * died before Maven produced a result), show the tail of what was captured so nothing
	 * is hidden.
	 */
	synchronized void shutdown(PrintStream console) {
		if (this.logWriter != null) {
			try {
				this.logWriter.close();
			}
			catch (IOException ex) {
				// nothing left to do
			}
			this.logWriter = null;
		}
		if (!this.finished && this.mode == OutputMode.SUMMARY && !this.log.isEmpty()) {
			Path logFile = this.root.resolve(OUTPUT_DIR).resolve("build.log");
			try {
				Files.createDirectories(logFile.getParent());
				Files.write(logFile, this.log, StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				// the tail below is still printed
			}
			console.println("[claude] the build ended before a summary was produced (full log: " + display(logFile)
					+ "); last captured lines:");
			List<String> tail = this.log.subList(Math.max(0, this.log.size() - FALLBACK_TAIL_LINES), this.log.size());
			tail.forEach(console::println);
			console.flush();
		}
	}

	/** One mojo execution and the lines it logged. */
	private static final class MojoRun {

		final MavenProject project;

		final MojoExecution execution;

		final long start;

		final boolean commandLine;

		private final List<String> lines = new ArrayList<>();

		MojoRun(MavenProject project, MojoExecution execution, long start) {
			this.project = project;
			this.execution = execution;
			this.start = start;
			// Goals typed on the command line ("dependency:tree", "help:evaluate") run
			// with the
			// default-cli execution id; their output is what the user asked for.
			this.commandLine = "default-cli".equals(execution.getExecutionId());
		}

		synchronized void add(String line) {
			if (this.lines.size() < MAX_MOJO_LINES) {
				this.lines.add(line);
			}
		}

		synchronized List<String> lines() {
			return new ArrayList<>(this.lines);
		}

	}

}
