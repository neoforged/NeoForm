package net.neoforged.neoform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

public abstract class TagRelease extends DefaultTask {
    @Input
    public abstract Property<String> getProjectVersion();

    @Inject
    protected abstract ExecOperations getExecOps();

    @Inject
    public TagRelease(Project project) {
        setGroup("neoform/internal");
        setDescription("Creates and pushes a git tag for the current project version, assuming it is not a SNAPSHOT version.");
        getProjectVersion().set(project.provider(() -> project.getVersion().toString()));
        getOutputs().upToDateWhen(task -> false);
    }

    @TaskAction
    public void tagRelease() {
        var projectVersion = getProjectVersion().get();
        if (projectVersion.endsWith("-SNAPSHOT")) {
            throw new GradleException("Cannot create Git tags for SNAPSHOT project versions.");
        }
        String tagName = "v" + projectVersion;
        getLogger().lifecycle("Tagging: {}", tagName);
        getExecOps().exec(spec -> {
            spec.commandLine("git", "tag", "-m", "Release " + projectVersion, tagName);
        });
        getLogger().lifecycle("Pushing tag");
        getExecOps().exec(spec -> spec.commandLine("git", "push", "origin", tagName));
    }
}
