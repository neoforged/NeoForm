package net.neoforged.neoform.tasks;

import de.undercouch.gradle.tasks.download.DownloadAction;
import net.neoforged.neoform.manifests.MinecraftVersionManifest;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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

        // Check if the file already exists and validate checksum in that case
        File outputFile = getOutput().getAsFile().get();
        if (outputFile.exists() && outputFile.length() == artifact.size()) {
            String checksum = checksum(outputFile);
            if (checksum.equals(artifact.checksum())) {
                setDidWork(false);
                return; // File is already up-to-date
            }
        }

        action.src(artifact.uri());
        action.dest(outputFile);
        action.tempAndMove(true);
        action.execute().thenRun(() -> {
            if (action.isUpToDate()) {
                setDidWork(false);
            }
        });
    }

    private static String checksum(File f) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new GradleException("Missing JCA algorithm", e);
        }

        try (var in = new FileInputStream(f);
             var out = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
            in.transferTo(out);
        }

        return HexFormat.of().formatHex(digest.digest());
    }

}
