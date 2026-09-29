package org.alexmond.maven.claude.output;

/**
 * Receives captured console output. Both methods are called on the thread that wrote the
 * output, so implementations can attribute lines to the mojo running on that thread.
 */
public interface LineRouter {

	/**
	 * Whether output written by the current thread should also reach the real console.
	 */
	boolean passthrough();

	/** One complete captured line (no line terminator, ANSI escapes removed). */
	void line(String line);

}
