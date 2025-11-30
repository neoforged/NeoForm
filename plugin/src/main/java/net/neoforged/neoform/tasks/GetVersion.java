package net.neoforged.neoform.tasks;

import net.neoforged.neoform.dsl.NeoFormExtension;
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
    public abstract Property<String> getMinecraftVersion();

    @Internal
    public abstract Property<Boolean> getRelease();

    @Inject
    public GetVersion(Project project) {
        getOutputs().upToDateWhen(task -> false); // Should always print.

        getRelease().convention(project.getProviders().gradleProperty("neoform_release").map(Boolean::parseBoolean));

        var neoForm = NeoFormExtension.fromProject(project);
        getMinecraftVersion().convention(neoForm.getMinecraftVersion());
        setGroup("neoform/internal");
        setDescription("Allows the project version to be computed by the publishing workflow");
    }

    @TaskAction
    public void printMinecraftVersion() throws IOException {
        boolean release = getRelease().get();

        getLogger().lifecycle("Version: {}", getMinecraftVersion().get());

        // Make these properties easily available to the CI/CD workflow
        var githubOutput = System.getenv("GITHUB_OUTPUT");
        if (githubOutput != null) {
            Files.writeString(Path.of(githubOutput), "minecraft_version" + getMinecraftVersion().get() + "\n");
        }
    }
}
