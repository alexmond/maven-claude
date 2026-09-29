package org.alexmond.maven.claude.output;

import java.util.List;

/**
 * Serialises a {@link BuildReport} to JSON. Hand-rolled so the extension stays free of
 * third-party classes on Maven's core class path.
 */
public final class JsonReportWriter {

	private JsonReportWriter() {
	}

	/** Returns the report as pretty-printed JSON. */
	public static String write(BuildReport report, String logPath) {
		StringBuilder json = new StringBuilder(1024);
		json.append("{\n");
		field(json, 1, "success", String.valueOf(report.success()), true);
		field(json, 1, "durationMillis", String.valueOf(report.durationMillis()), true);
		json.append(indent(1)).append("\"modules\": {");
		json.append("\"total\": ").append(report.modules());
		json.append(", \"ok\": ").append(report.modulesOk());
		json.append(", \"failed\": ").append(report.modulesFailed());
		json.append(", \"skipped\": ").append(report.modulesSkipped()).append("},\n");
		BuildReport.Tests tests = report.tests();
		if (tests != null) {
			json.append(indent(1)).append("\"tests\": {");
			json.append("\"run\": ").append(tests.run());
			json.append(", \"failed\": ").append(tests.failed());
			json.append(", \"errors\": ").append(tests.errors());
			json.append(", \"skipped\": ").append(tests.skipped()).append("},\n");
		}
		if (logPath != null) {
			field(json, 1, "log", quote(logPath), true);
		}
		if (report.note() != null) {
			field(json, 1, "note", quote(report.note()), true);
		}
		json.append(indent(1)).append("\"problems\": [");
		List<Problem> problems = report.problems();
		for (int i = 0; i < problems.size(); i++) {
			Problem problem = problems.get(i);
			json.append((i == 0) ? "\n" : ",\n").append(indent(2)).append("{\n");
			field(json, 3, "kind", quote(problem.kind().name()), true);
			if (problem.module() != null) {
				field(json, 3, "module", quote(problem.module()), true);
			}
			field(json, 3, "title", quote(problem.title()), true);
			if (problem.location() != null) {
				field(json, 3, "location", quote(problem.location()), true);
			}
			json.append(indent(3)).append("\"detail\": [");
			for (int j = 0; j < problem.detail().size(); j++) {
				json.append((j == 0) ? "" : ", ").append(quote(problem.detail().get(j)));
			}
			json.append("]\n").append(indent(2)).append('}');
		}
		json.append(problems.isEmpty() ? "]\n" : "\n" + indent(1) + "]\n");
		json.append("}\n");
		return json.toString();
	}

	private static void field(StringBuilder json, int depth, String name, String value, boolean comma) {
		json.append(indent(depth)).append('"').append(name).append("\": ").append(value);
		json.append(comma ? ",\n" : "\n");
	}

	private static String indent(int depth) {
		return "  ".repeat(depth);
	}

	static String quote(String value) {
		StringBuilder out = new StringBuilder(value.length() + 2).append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		return out.append('"').toString();
	}

}
