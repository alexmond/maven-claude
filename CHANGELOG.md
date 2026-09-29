# Changelog

## 0.1.0

First release.

- **Build summary extension.** Register it in `.mvn/extensions.xml`. Inside Claude Code
  (`CLAUDECODE=1`), it replaces Maven's console output with a short summary:
  - compile errors with `file:line:col`
  - failing tests with the message and only the project's stack frames
  - other failed goals with their error lines
  - module and test totals

  The full log goes to `target/claude/build.log`, and structured results to
  `target/claude/build-report.json`.

  Modes are `auto`, `summary`, `report`, and `off`, set via `-Dclaude.output` or
  `CLAUDE_MAVEN_OUTPUT`. Output from goals typed on the command line still prints.
- **`claude:source`.** Finds the dependency, JDK module, or reactor module that provides
  a class and extracts its source. Falls back to `javap -p` when no source is published.
- **`claude:context`.** Writes `.claude/maven-context.md` with modules, reactor
  dependencies, Java release, test stack, checks that fail the build, profiles, and
  ready-to-run commands.
- **`claude:init`.** One-time setup: registers the extension, writes the context, allows
  Maven commands in `.claude/settings.json`, and imports the context from `CLAUDE.md`.
- **`claude:` prefix without setup.** The goal prefix resolves wherever the extension is
  registered; no `pluginGroups` entry is needed.
