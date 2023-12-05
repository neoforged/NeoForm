package net.minecraftforge.mcpconfig.tasks

import codechicken.diffpatch.cli.*
import codechicken.diffpatch.util.LoggingOutputStream
import org.gradle.api.*
import org.gradle.api.logging.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*

public abstract class MakePatches extends DefaultTask {
    @InputFile abstract RegularFileProperty getBaseZip()
    @InputDirectory abstract RegularFileProperty getModified()
    @OutputDirectory abstract RegularFileProperty getPatches()
    
    @TaskAction
    def exec() {
        Utils.init()

        def baseZip = baseZip.get().asFile
        def modified = modified.get().asFile
        def patches = patches.get().asFile

        def result = DiffOperation.builder()
            .logTo(new LoggingOutputStream(logger, LogLevel.LIFECYCLE))
            .aPath(baseZip.toPath())
            .bPath(modified.toPath())
            .outputPath(patches.toPath())
            .level(codechicken.diffpatch.util.LogLevel.WARN)
            .build().operate()

        def exit = result.exit
        if (exit != 0 && exit != 1) {
            throw new RuntimeException("DiffPatch failed with exit code: " + exit)
        }
    }
}