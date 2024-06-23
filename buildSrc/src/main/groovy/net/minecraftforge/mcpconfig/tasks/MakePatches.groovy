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
            .logTo((Consumer<String>){logger.log(it, LogLevel.LIFECYCLE)})
            .baseInput(Input.MultiInput.archive(ArchiveFormat.ZIP, baseZip.toPath()))
            .changedInput(Input.MultiInput.folder(modified.toPath()))
            .patchesOutput(Output.MultiOutput.folder(patches.toPath()))
            .level(io.codechicken.diffpatch.util.LogLevel.WARN)
            .lineEnding("\n")
            .build().operate()

        def exit = result.exit
        if (exit != 0 && exit != 1) {
            throw new RuntimeException("DiffPatch failed with exit code: " + exit)
        }
    }
}