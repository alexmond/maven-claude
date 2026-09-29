package org.alexmond.maven.claude;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

/**
 * When the jar is loaded as a core extension, declares it as a build plugin of every
 * project (same version, no executions) so the {@code claude:} goal prefix resolves
 * without a {@code pluginGroups} entry in settings.xml or a pom change.
 */
@Named("claude")
@Singleton
public class ClaudeLifecycleParticipant extends AbstractMavenLifecycleParticipant {

	@Override
	public void afterProjectsRead(MavenSession session) {
		String version = PluginInfo.version();
		if (version == null) {
			return;
		}
		for (MavenProject project : session.getProjects()) {
			if (project.getBuild() == null || project.getPlugin(PluginInfo.KEY) != null) {
				continue;
			}
			Plugin plugin = new Plugin();
			plugin.setGroupId(PluginInfo.GROUP_ID);
			plugin.setArtifactId(PluginInfo.ARTIFACT_ID);
			plugin.setVersion(version);
			project.getBuild().addPlugin(plugin);
		}
	}

}
