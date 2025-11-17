package net.neoforged.neoform.tasks;

import net.neoforged.nfrtgradle.NeoFormRuntimeTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public abstract class TestNeoFormData extends NeoFormRuntimeTask {
    @InputFile
    public abstract RegularFileProperty getNeoFormDataArchive();

    @OutputDirectory
    public abstract DirectoryProperty getResultsDirectory();

    @TaskAction
    public void test() {
        var resultsDir = getResultsDirectory().getAsFile().get();

        var args = new ArrayList<String>();
        Collections.addAll(args,
                "run",
                "--dist=joined",
                "--neoform=" + getNeoFormDataArchive().getAsFile().get().getAbsolutePath()
        );

        Map<String, File> requestedResults = Map.of(
                "vanillaDeobfuscated", new File(resultsDir, "minecraft-processed.jar"),
                "compiled", new File(resultsDir, "minecraft-recompiled.jar"),
                "sources", new File(resultsDir, "minecraft-sources.zip"),
                "sourcesAndCompiled", new File(resultsDir, "minecraft-recompiled-with-sources.jar")
        );

        for (var entry : requestedResults.entrySet()) {
            args.add("--write-result=" + entry.getKey() + ":" + entry.getValue().getAbsolutePath());
            entry.getValue().delete();
        }

        run(args);

        // The requested results should have been written
        for (var entry : requestedResults.entrySet()) {
            if (!entry.getValue().isFile()) {
                throw new GradleException("Expected result " + entry.getKey() + " to be written to " + entry.getValue());
            }
        }
    }
}
