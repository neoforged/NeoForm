package net.neoforged.neoform.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.util.Map;

public abstract class PrepareJarForDecompiler extends ToolAction {
    @InputFile
    public abstract RegularFileProperty getClient();

    @InputFile
    public abstract RegularFileProperty getServer();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    public void execute() throws IOException {
        var clientJar = getClient().getAsFile().get();
        var serverJar = getServer().getAsFile().get();
        var joinedJar = getOutput().getAsFile().get();

        exec(Map.of(
                "downloadClientOutput", clientJar.getAbsolutePath(),
                "downloadServerOutput", serverJar.getAbsolutePath(),
                "output", joinedJar.getAbsolutePath()
        ));
    }
}
