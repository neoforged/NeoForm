package net.neoforged.neoform.tasks;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public abstract class Decompile extends ToolAction {
    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Classpath
    public abstract ConfigurableFileCollection getInputClasspath();

    @Inject
    public Decompile() {
        var layout = getProject().getLayout();
        getLogFile().convention(layout.file(getOutput().map(rf -> {
            var outputZip = rf.getAsFile().toPath();
            return outputZip.resolveSibling(outputZip.getFileName() + "_decompiler.log").toFile();
        })));
    }

    @TaskAction
    public void execute() throws IOException {
        var inputJar = getInput().getAsFile().get();
        var outputZip = getOutput().getAsFile().get();

        var librariesFile = new File(getTemporaryDir(), "libraries.cfg");
        try (var writer = new BufferedWriter(new FileWriter(librariesFile, StandardCharsets.UTF_8))) {
            for (var file : getInputClasspath()) {
                writer.append("--add-external=").append(file.getAbsolutePath()).append('\n');
            }
        }

        exec(Map.of(
                "preProcessJarOutput", inputJar.getAbsolutePath(),
                "output", outputZip.getAbsolutePath(),
                "listLibrariesOutput", librariesFile.getAbsolutePath()
        ));
    }
}
