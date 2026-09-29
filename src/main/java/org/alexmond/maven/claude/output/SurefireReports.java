package org.alexmond.maven.claude.output;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads surefire/failsafe {@code TEST-*.xml} reports.
 */
public final class SurefireReports {

	private static final int MAX_FRAMES = 5;

	private SurefireReports() {
	}

	/**
	 * Reads the reports in {@code directory} written at or after {@code notBefore} (epoch
	 * millis), so reports left over from earlier builds are ignored.
	 */
	public static Results read(Path directory, long notBefore) {
		Results results = new Results();
		if (!Files.isDirectory(directory)) {
			return results;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "TEST-*.xml")) {
			List<Path> sorted = new ArrayList<>();
			files.forEach(sorted::add);
			sorted.sort(null);
			for (Path file : sorted) {
				if (Files.getLastModifiedTime(file).toMillis() >= notBefore) {
					parse(file, results);
				}
			}
		}
		catch (IOException ex) {
			// unreadable report directory: report nothing rather than break the build
		}
		return results;
	}

	static void parse(Path file, Results into) {
		try (InputStream in = Files.newInputStream(file)) {
			Document document = newBuilder().parse(in);
			NodeList suites = document.getElementsByTagName("testsuite");
			for (int i = 0; i < suites.getLength(); i++) {
				Element suite = (Element) suites.item(i);
				into.tests += intAttribute(suite, "tests");
				into.failures += intAttribute(suite, "failures");
				into.errors += intAttribute(suite, "errors");
				into.skipped += intAttribute(suite, "skipped");
			}
			NodeList cases = document.getElementsByTagName("testcase");
			for (int i = 0; i < cases.getLength(); i++) {
				Element testCase = (Element) cases.item(i);
				Element problem = firstChild(testCase, "failure");
				boolean error = false;
				if (problem == null) {
					problem = firstChild(testCase, "error");
					error = problem != null;
				}
				if (problem != null) {
					into.failed.add(new Failure(testCase.getAttribute("classname"), testCase.getAttribute("name"),
							error, problem.getAttribute("type"), problem.getAttribute("message"),
							problem.getTextContent()));
				}
			}
		}
		catch (IOException | SAXException | ParserConfigurationException ex) {
			// a half-written or foreign report: skip it
		}
	}

	private static DocumentBuilder newBuilder() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setExpandEntityReferences(false);
		return factory.newDocumentBuilder();
	}

	private static Element firstChild(Element parent, String name) {
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element element && name.equals(element.getTagName())) {
				return element;
			}
		}
		return null;
	}

	private static int intAttribute(Element element, String name) {
		String value = element.getAttribute(name);
		try {
			return value.isEmpty() ? 0 : (int) Double.parseDouble(value);
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	/**
	 * Turns a failed test into a {@link Problem}: headline message, the project frames,
	 * and the root cause.
	 */
	public static Problem toProblem(Failure failure, SourceIndex index, String module) {
		String trace = failure.stackTrace().isBlank()
				? failure.type() + (failure.message().isEmpty() ? "" : ": " + failure.message()) : failure.stackTrace();
		StackTraces.Trimmed trimmed = StackTraces.trim(trace, index, MAX_FRAMES);
		String simpleClass = failure.className().substring(failure.className().lastIndexOf('.') + 1);
		String location = null;
		for (StackTraces.Frame frame : trimmed.frames()) {
			if (frame.className().equals(failure.className())
					|| frame.className().startsWith(failure.className() + "$")) {
				location = frame.location();
				break;
			}
		}
		if (location == null && !trimmed.frames().isEmpty()) {
			location = trimmed.frames().get(0).location();
		}
		List<String> detail = new ArrayList<>(trimmed.message());
		if (detail.isEmpty() && !failure.message().isEmpty()) {
			detail.add(failure.message());
		}
		for (StackTraces.Frame frame : trimmed.frames()) {
			detail.add("  " + frame);
		}
		if (trimmed.rootCause() != null && (detail.isEmpty() || !detail.get(0).equals(trimmed.rootCause()))) {
			detail.add("Caused by: " + trimmed.rootCause());
		}
		return new Problem(Problem.Kind.TEST, module, simpleClass + "." + failure.name(), location, detail);
	}

	/**
	 * A failed or errored test case.
	 *
	 * @param className test class
	 * @param name test method (may include parameters)
	 * @param error {@code true} for an unexpected exception, {@code false} for an
	 * assertion failure
	 * @param type exception type
	 * @param message exception message as recorded by surefire (may be truncated)
	 * @param stackTrace full stack trace text
	 */
	public record Failure(String className, String name, boolean error, String type, String message,
			String stackTrace) {

	}

	/** Totals and failures accumulated across report files. */
	public static final class Results {

		int tests;

		int failures;

		int errors;

		int skipped;

		final List<Failure> failed = new ArrayList<>();

		public int tests() {
			return this.tests;
		}

		public int failures() {
			return this.failures;
		}

		public int errors() {
			return this.errors;
		}

		public int skipped() {
			return this.skipped;
		}

		public List<Failure> failed() {
			return this.failed;
		}

	}

}
