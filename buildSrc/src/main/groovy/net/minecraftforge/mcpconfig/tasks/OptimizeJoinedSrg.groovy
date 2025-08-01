package net.minecraftforge.mcpconfig.tasks

import net.neoforged.srgutils.IMappingBuilder
import net.neoforged.srgutils.IMappingFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Removes superflous entries from the mappings file.
 * We only need method parameter mappings, so we can strip:
 * - field mappings
 * - methods without parameters
 * - the SRG ID (for publication at least)
 */
abstract class OptimizeJoinedSrg extends DefaultTask {
    @InputFile
    abstract RegularFileProperty getInput()

    @OutputFile
    abstract RegularFileProperty getOutput()

    @TaskAction
    void optimize() {
        var input = getInput().get().asFile
        var output = getOutput().get().asFile

        def srgInput = IMappingFile.load(input)

        // Drop additional mapping columns and mappings for fields
        def srgOutput = IMappingBuilder.create("obf", "srg")
        for (var srcClass : srgInput.classes) {
            var dstClass = srgOutput.addClass(srcClass.original, srcClass.original)
            for (var srcMethod : srcClass.methods) {
                if (srcMethod.parameters.isEmpty()) {
                    continue // Skip methods with no parameters
                }
                var dstMethod = dstClass.method(srcMethod.descriptor, srcMethod.original, srcMethod.original)
                for (var srcParam in srcMethod.parameters) {
                    dstMethod.parameter(srcParam.index, srcParam.original, srcParam.mapped)
                }
            }
        }
        srgOutput.build().write(output.toPath(), IMappingFile.Format.TSRG2)
    }
}
