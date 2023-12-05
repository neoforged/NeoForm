package net.minecraftforge.mcpconfig.tasks

import codechicken.diffpatch.cli.*
import codechicken.diffpatch.util.LoggingOutputStream
import codechicken.diffpatch.util.PatchMode
import org.gradle.api.*
import org.gradle.api.logging.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*

public abstract class ApplyPatches extends DefaultTask {
    @InputFile abstract RegularFileProperty getBaseZip()
    @InputDirectory @Optional abstract RegularFileProperty getPatches()
    @OutputDirectory abstract RegularFileProperty getOutput()
    
    @TaskAction
    def exec() {
        Utils.init()

        def baseZip = baseZip.get().asFile
        def output = output.get().asFile
        def patches = patches.get().asFile

        def result = PatchOperation.builder()
            .logTo(new LoggingOutputStream(logger, LogLevel.LIFECYCLE))
            .basePath(baseZip.toPath())
            .patchesPath(patches.toPath())
            .outputPath(output.toPath())
            .level(codechicken.diffpatch.util.LogLevel.WARN)
            .mode(PatchMode.OFFSET)
            .build().operate()

        def exit = result.exit
        if (exit != 0 && exit != 1) {
            throw new RuntimeException("DiffPatch failed with exit code: " + exit)
        }
        if (exit != 0) {
            throw new RuntimeException("Patches failed to apply")
        }

        output.mkdirs()
    }
}