package net.neoforged.neoform.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.IOException;
import java.util.Map;

@UntrackedTask(because = "Prepares jar for decompiler, not cacheable")
public abstract class PrepareJarForDecompiler extends ToolAction {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getClient();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getServer();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    public void execute() throws IOException {
        var clientJar = getClient().getAsFile().get();
        var serverJar = getServer().getAsFile().get();
        var joinedJar = getOutput().getAsFile().get();

        exec(Map.of(
                "inputClientJar", clientJar.getAbsolutePath(),
                "inputServerJar", serverJar.getAbsolutePath(),
                "output", joinedJar.getAbsolutePath()
        ));
    }
}
