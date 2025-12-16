package net.neoforged.neoform.tasks;

import net.neoforged.nfrtgradle.NeoFormRuntimeTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Downloads one of the files from the artifacts section of a Minecraft version manifest.
 */
public abstract class DownloadVersionArtifacts extends NeoFormRuntimeTask {
    @Input
    public abstract Property<String> getMinecraftVersion();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getVersionManifest();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getClient();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getServer();

    @TaskAction
    public void download() {
        var args = new ArrayList<String>();
        Collections.addAll(
                args,
                "download-artifacts",
                "--minecraft-version", getMinecraftVersion().get()
        );
        if (getVersionManifest().isPresent()) {
            Collections.addAll(args, "--write-version-manifest", getVersionManifest().get().getAsFile().getAbsolutePath());
        }
        if (getClient().isPresent()) {
            Collections.addAll(args, "--write-artifact", "client:" + getClient().get().getAsFile().getAbsolutePath());
        }
        if (getServer().isPresent()) {
            Collections.addAll(args, "--write-artifact", "server:" + getServer().get().getAsFile().getAbsolutePath());
        }
        run(args);
    }
}
