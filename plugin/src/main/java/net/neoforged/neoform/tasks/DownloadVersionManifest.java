package net.neoforged.neoform.tasks;

import de.undercouch.gradle.tasks.download.DownloadAction;
import net.neoforged.neoform.Constants;
import net.neoforged.neoform.manifests.LauncherManifest;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipFile;

/**
 * This task will download the launcher manifest, grab a given Minecraft version, and ensure the
 * corresponding version JSON is downloaded to the given destination.
 */
public abstract class DownloadVersionManifest extends DefaultTask {
    private final DownloadAction action;

    private final DownloadAction launcherManifestAction;

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<String> getLauncherManifestUrl();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Inject
    public DownloadVersionManifest(Project project) {
        action = new DownloadAction(project, this);
        launcherManifestAction = new DownloadAction(project, this);
        getLauncherManifestUrl().convention(Constants.MOJANG_METADATA_URI);
    }

    @TaskAction
    public void download() throws IOException, ExecutionException, InterruptedException {

        // Download the launcher manifest and grab the version from it
        var minecraftVersion = getMinecraftVersion().get();

        var launcherManifestFile = new File(getTemporaryDir(), "launcher_manifest.json");
        launcherManifestAction.src(getLauncherManifestUrl());
        launcherManifestAction.dest(launcherManifestFile);
        launcherManifestAction.execute().get();

        var launcherManifest = LauncherManifest.from(launcherManifestFile.toPath());
        var version = launcherManifest.versions().stream().filter(v -> v.id().equals(minecraftVersion)).findFirst();
        if (version.isEmpty()) {
            throw new InvalidUserCodeException("Minecraft version " + minecraftVersion + " does not exist in the launcher manifest at " + getLauncherManifestUrl().get());
        }
        var versionManifestUrl = version.get().url().toString();

        action.overwrite(false);
        action.src(versionManifestUrl);

        // Supported zipped version manifests (for weird unobfuscated versions)
        if (versionManifestUrl.endsWith(".zip")) {
            var tempFile = new File(getTemporaryDir(), "version.zip");
            action.dest(tempFile);

            action.execute().thenRun(() -> {
                try {
                    unzipManifest(tempFile, getOutput().getAsFile().get());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                if (action.isUpToDate()) {
                    getState().setDidWork(false);
                }
            });
        } else {
            action.dest(getOutput());

            action.execute().thenRun(() -> {
                if (action.isUpToDate()) {
                    getState().setDidWork(false);
                }
            });
        }
    }

    private void unzipManifest(File manifestZipFile, File destination) throws IOException {

        boolean entryFound = false;
        try (var zipFile = new ZipFile(manifestZipFile)) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().endsWith(".json")) {
                    if (entryFound) {
                        throw new IllegalStateException("Version manifest ZIP contains more than one JSON file");
                    }
                    entryFound = true;
                    try (var in = zipFile.getInputStream(entry)) {
                        Files.copy(in, destination.toPath());
                    }
                }
            }
        }

        if (!entryFound) {
            throw new IllegalStateException("Version manifest ZIP does not contain any JSON files");
        }
    }
}
