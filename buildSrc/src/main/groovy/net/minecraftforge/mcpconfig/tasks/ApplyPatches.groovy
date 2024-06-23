package net.minecraftforge.mcpconfig.tasks

import java.util.function.Consumer
import io.codechicken.diffpatch.cli.*
import io.codechicken.diffpatch.util.archiver.ArchiveFormat
import io.codechicken.diffpatch.util.Input
import io.codechicken.diffpatch.util.PatchMode
import io.codechicken.diffpatch.util.Output
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
            .logTo((Consumer<String>){logger.log(it, LogLevel.LIFECYCLE)})
            .baseInput(Input.MultiInput.archive(ArchiveFormat.ZIP, baseZip.toPath()))
            .patchesInput(Input.MultiInput.folder(patches.toPath()))
            .patchedOutput(Output.MultiOutput.folder(output.toPath()))
            .level(io.codechicken.diffpatch.util.LogLevel.WARN)
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