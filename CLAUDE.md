# CLAUDE.md

Guidance for Claude Code in this repository.

## What this is

`claude-maven-plugin` (`org.alexmond`). One jar with two roles:

- **Core extension** (`output/` package: `ClaudeEventSpy`, plus the root
  `ClaudeLifecycleParticipant`). It is loaded from a project's `.mvn/extensions.xml` and
  condenses build output when `CLAUDECODE=1`. It also injects the plugin into every
  project, so the `claude:` prefix resolves.
- **Maven plugin** with three goals: `source`, `context`, and `init`.

`README.md` is the user-facing description. `CHANGELOG.md` tracks releases.

## Build & test

The shell running Claude Code has `CLAUDECODE=1` set. Prefix builds of **this** repo with
`env -u CLAUDECODE` so the ITs see a clean environment. The ITs set the variable
themselves where they need it.

```bash
env -u CLAUDECODE ./mvnw -q verify                    # everything, incl. src/it (~1 min)
env -u CLAUDECODE ./mvnw -q verify -Dinvoker.skip     # lint + unit tests only
env -u CLAUDECODE ./mvnw -q spring-javaformat:apply   # fix formatting (tabs)
```

`validate` runs spring-javaformat, Checkstyle, and PMD, and these fail the build.

- The PMD ruleset (`pmd-ruleset.xml`) is tuned for an extension that must never break a
  build.
- Swallowed exceptions are fine **only** with a comment saying why.

## Architecture notes

- **Output capture (`ConsoleCapture`).** Maven 3.9's SLF4J simple logger caches
  `System.out` (`cacheOutputStream=true`). We call `SimpleLogger.init()` by reflection
  with caching off, then swap `System.out`/`err` for `CapturingOutputStream`. If
  reflection fails (Maven 4, another logging backend), there is no capture and the mode
  degrades to REPORT.
- **Line attribution (`BuildRecorder`).** Each builder thread keeps a stack of running
  mojos, set by `MojoStarted`/`MojoSucceeded`/`MojoFailed` events. Lines go to the mojo on
  top of the writing thread's stack.
  - `default-cli` executions (goals typed on the command line) pass through to the real
    console.
  - Everything else goes only to `target/claude/build.log`.
- **Summary timing.** The summary is printed on the `MavenExecutionResult` event. That
  covers failures before any module builds, such as POM errors. A shutdown hook closes the
  log and prints the captured tail if Maven never produced a result.
- **When the summary is skipped.** It is not printed when the build succeeded and only
  command-line goals ran. This keeps `V=$(mvn -q help:evaluate … -DforceStdout)` working.
- **Stay dependency-free.** The extension path must not use third-party classes. Jackson
  is used only by `init`.

## Integration tests (`src/it`, maven-invoker-plugin)

- **Each IT needs its own `.mvn/`.** Otherwise Maven picks this repo as the build root,
  and `init` would edit this repo's files.
- **Two runs per IT.** Run 1 is `…:init`, which registers the extension. Run 2 is the real
  build.
- **`build.log` covers both runs.** `verify.groovy` slices it after the last
  `BUILD SUCCESS`, which belongs to run 1.

## Releasing

- Versions are plain numeric (`0.1.0`), with `-SNAPSHOT` on development versions.
- `.github/workflows/maven_release.yml` publishes to Maven Central. It needs the
  `OSSRH_*` and `GPG_*` repo secrets.
