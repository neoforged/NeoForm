package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*
import java.nio.file.Files

public abstract class MakeDir extends DefaultTask {
    @OutputDirectory abstract RegularFileProperty getFolder()
    
    @TaskAction
    protected void exec() {
        Files.createDirectories(folder.get().getAsFile().toPath())
    }
}