package net.neoforged.neoform.tasks;

import de.undercouch.gradle.tasks.download.DownloadAction;
import net.neoforged.neoform.manifests.MinecraftVersionManifest;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Downloads one of the files from the artifacts section of a Minecraft version manifest.
 */
public abstract class DownloadVersionArtifact extends DefaultTask {
    private final DownloadAction action;

    @InputFile
    public abstract RegularFileProperty getVersionManifest();

    @Input
    public abstract Property<String> getArtifactName();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Inject
    public DownloadVersionArtifact(Project project) {
        action = new DownloadAction(project, this);
    }

    @TaskAction
    public void download() throws IOException {
        var manifest = MinecraftVersionManifest.from(getVersionManifest().getAsFile().get().toPath());

        var artifactName = getArtifactName().get();
        var artifact = manifest.downloads().get(artifactName);
        if (artifact == null || artifact.uri() == null) {
            throw new InvalidUserCodeException(artifactName + " is not listed in the downloads section of the version manifest.");
        }

        action.src(artifact.uri());
        action.dest(getOutput());
        action.execute().thenRun(() -> {
            if (action.isUpToDate()) {
                setDidWork(false);
            }
        });
    }

}
