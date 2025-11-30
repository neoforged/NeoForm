package net.neoforged.neoform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@DisableCachingByDefault
public abstract class GetVersion extends DefaultTask {
    @Internal
    protected abstract Property<String> getVersion();

    @Inject
    public GetVersion(Project project) {
        getOutputs().upToDateWhen(task -> false); // Should always print.

        setGroup("neoform/internal");
        setDescription("Allows the project version to be computed by the publishing workflow");

        getVersion().set(project.provider(() -> project.getVersion().toString()));
    }

    @TaskAction
    public void printVersion() throws IOException {
        getLogger().lifecycle("Version: {}", getVersion().get());

        // Make these properties easily available to the CI/CD workflow
        var githubOutput = System.getenv("GITHUB_OUTPUT");
        if (githubOutput != null) {
            Files.writeString(Path.of(githubOutput), "version=" + getVersion().get() + "\n");
        }
    }
}
