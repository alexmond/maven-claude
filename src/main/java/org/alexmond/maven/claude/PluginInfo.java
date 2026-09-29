package org.alexmond.maven.claude;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Coordinates of this plugin, read from the jar's Maven metadata. */
public final class PluginInfo {

	public static final String GROUP_ID = "org.alexmond";

	public static final String ARTIFACT_ID = "claude-maven-plugin";

	public static final String KEY = GROUP_ID + ":" + ARTIFACT_ID;

	private static final String POM_PROPERTIES = "/META-INF/maven/" + GROUP_ID + "/" + ARTIFACT_ID + "/pom.properties";

	private PluginInfo() {
	}

	/** This plugin's version, or {@code null} when not running from the packaged jar. */
	public static String version() {
		try (InputStream in = PluginInfo.class.getResourceAsStream(POM_PROPERTIES)) {
			if (in == null) {
				return null;
			}
			Properties properties = new Properties();
			properties.load(in);
			return properties.getProperty("version");
		}
		catch (IOException ex) {
			return null;
		}
	}

}
