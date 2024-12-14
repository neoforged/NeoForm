package net.minecraftforge.mcpconfig.tasks


import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile

abstract class GenerateSplitManifest extends ToolJarExec {
    @InputFile abstract RegularFileProperty getClient()
    @InputFile abstract RegularFileProperty getServer()
    @InputFile abstract RegularFileProperty getMappings()
    @OutputFile abstract RegularFileProperty getDest()
    
    @Override
    protected void preExec() {
        standardOutput = JarExec.NULL_OUTPUT
        setArgs(Utils.fillVariables(args, [
            'client': client.get().getAsFile().absolutePath,
            'server': server.get().getAsFile().absolutePath,
            'mappings': mappings.get().getAsFile().absolutePath,
            'output': dest.get().getAsFile().absolutePath
        ]))
    }
}