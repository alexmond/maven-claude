# claude-maven-plugin

Maven integration for [Claude Code](https://claude.com/claude-code).

When Claude runs `mvn verify`, it usually gets thousands of lines: downloads, plugin
banners, and full stack traces. This plugin turns that into the few lines an agent needs.

```
BUILD FAILED in 41.2s · 3 modules: 1 ok, 1 failed, 1 skipped · tests: 216 run, 1 failed

COMPILE  core/src/main/java/org/acme/OrderService.java:88:17
         cannot find symbol
         symbol:   method total(java.util.List)

TEST     OrderServiceTest.rejectsEmptyCart  core/src/test/java/org/acme/OrderServiceTest.java:42
         AssertionFailedError: expected: <400> but was: <200>
           at OrderService.checkout(core/src/main/java/org/acme/OrderService.java:61)
           at OrderServiceTest.rejectsEmptyCart(core/src/test/java/org/acme/OrderServiceTest.java:42)

Full log: target/claude/build.log · JSON: target/claude/build-report.json
```

It also gives Claude two things it usually has to guess:

- **Library source.** `claude:source` finds which dependency has a class and extracts its
  real source file.
- **Project facts.** `claude:context` writes the modules, commands, and build checks to a
  file that CLAUDE.md imports.

## Quick start

Run this once in your project root:

```bash
mvn org.alexmond:claude-maven-plugin:0.1.0:init
```

It does four things. Each is safe to re-run:

| File | Change |
|---|---|
| `.mvn/extensions.xml` | Registers the plugin as a Maven core extension |
| `.claude/maven-context.md` | Generated project facts (see `claude:context`) |
| `.claude/settings.json` | Allows `mvn *` and `./mvnw *`, so Claude can build without a prompt each time |
| `CLAUDE.md` | Appends `@.claude/maven-context.md`, if the file exists |

Commit these files. Now every build Claude runs is summarized, and the `claude:` goals
work without extra setup.

To skip a step, pass `-Dclaude.permissions=false`, `-Dclaude.extension=false`, or
`-Dclaude.claudeMd=false`.

## Build summary (the extension)

The extension is **off for people** and **on for Claude**. It turns on only when the
`CLAUDECODE=1` environment variable is set, which Claude Code sets in every shell it
opens. Your own terminal builds don't change.

When it is on:

- **The console shows only the summary.** It lists compile errors and failing tests with
  `file:line`. Stack traces keep only your project's frames and the root cause.
- **Goals you type still print.** For example, `dependency:tree` and
  `help:evaluate -q -DforceStdout` print their output as usual.
- **Nothing is lost.** The full log is in `target/claude/build.log`, including Maven's own
  error block.
- **Structured results.** `target/claude/build-report.json` has the same problems as data.
- **The exit code doesn't change.**

You can override the mode with `-Dclaude.output=…` or the `CLAUDE_MAVEN_OUTPUT` variable:

| Value | Behaviour |
|---|---|
| `auto` (default) | `summary` in Claude Code, `off` elsewhere |
| `summary` | Hide lifecycle output; print the summary |
| `report` | Normal Maven output, plus the summary and JSON at the end (useful in CI) |
| `off` | Do nothing |

The plugin keeps the full output, with the summary added at the end, in these cases:

- debug logging (`-X`) is on
- a log file (`-l`) is set
- the build runs in the Maven daemon (`mvnd`)

## Goals

### `claude:source`: read a dependency's real code

```bash
mvn -q claude:source -Dclass=com.fasterxml.jackson.databind.ObjectMapper
```
```
com.fasterxml.jackson.databind.ObjectMapper
  from    com.fasterxml.jackson.core:jackson-databind:2.20.1 (compile)
  source  target/claude/sources/jackson-databind-2.20.1/com/fasterxml/jackson/databind/ObjectMapper.java
```

- **Lookup order:** the JDK first, then your own modules, then the full test class path.
- **Class name forms:** a full name, a nested class (`a.B.C` or `a.B$C`), or a simple
  name (`ObjectMapper`).
- **Source:** it downloads the `-sources.jar` if needed. For JDK classes it uses the JDK's
  `src.zip`.
- **No sources published:** it prints `javap -p` signatures instead.
- **Search a whole library:** add `-Dclaude.unpackAll` to unpack the entire sources jar.
- **Output under `-q`:** the result goes to stdout, so it still prints.

### `claude:context`: project facts for CLAUDE.md

```bash
mvn -q claude:context
```

Writes `.claude/maven-context.md` with:

- modules and how they depend on each other
- Java release, test libraries, and failsafe ITs
- checks that fail the build: Checkstyle, PMD, Spotless, Enforcer, JaCoCo, and others
- profiles and how they activate
- exact commands to build one module, run one test, fix formatting, or run the app

Re-run it after changing the POMs.

### `claude:init`

This is the one-time setup described in [Quick start](#quick-start).

## Without the extension

The `claude:` prefix resolves because the extension adds the plugin to each project. If
you skip the extension, use full coordinates instead:
`mvn org.alexmond:claude-maven-plugin:0.1.0:source -Dclass=…`. The other option is to add
`<pluginGroup>org.alexmond</pluginGroup>` to `~/.m2/settings.xml`.

## Requirements

- **Maven 3.9.x.** The summary relies on Maven 3.9's logging.
  - On other Maven versions, the extension detects that it can't take over the logger. It
    then keeps the normal output and still writes the summary and JSON.
- **Java 17 or newer** to run Maven.

## How it works

The jar plays two roles:

- **Core extension.** Registered in `.mvn/extensions.xml`, it provides an `EventSpy` and a
  lifecycle participant. `init` handles the registration.
- **Maven plugin.** It provides the `claude:*` goals.

The extension follows each mojo execution and records what that mojo logs:

- For compile errors, it parses the compiler's messages.
- For failing tests, it reads surefire/failsafe XML reports written during this build.
- For any other failing goal, it shows that goal's error lines.

Maven 3.9 caches `System.out` inside its logger at startup. So the extension re-reads the
logger's own configuration with caching turned off, then routes output through a capture
stream. If anything inside the extension fails, it turns itself off and the build
continues unchanged.

## Building

```bash
./mvnw verify          # formatting, checkstyle, PMD, unit tests, and the src/it end-to-end builds
./mvnw verify -Dinvoker.skip   # unit tests only
./mvnw spring-javaformat:apply # fix formatting
```

## API documentation

Javadoc for every release is on
[javadoc.io](https://javadoc.io/doc/org.alexmond/claude-maven-plugin), and the goal
reference is available with `mvn claude:help -Ddetail`.

## License

Apache License 2.0
