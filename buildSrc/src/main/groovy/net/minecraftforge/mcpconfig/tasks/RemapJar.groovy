package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*

abstract class RemapJar extends ToolJarExec {
    @InputFile abstract RegularFileProperty getMappings()
    @InputFile abstract RegularFileProperty getInput()
    @InputFile abstract RegularFileProperty getLibraries()
    @OutputFile abstract RegularFileProperty getDest()
    @OutputFile @Optional abstract RegularFileProperty getLog()
    
    @Override
    protected void preExec() {
        def logStream = log.isPresent() ? log.get().getAsFile().newOutputStream() : JarExec.NULL_OUTPUT
        standardOutput logStream
        errorOutput logStream
        setArgs(Utils.fillVariables(args, [
            'mappings': mappings.get().getAsFile().absolutePath,
            'input': input.get().getAsFile().absolutePath,
            'output': dest.get().getAsFile().absolutePath,
            'libraries': libraries.get().getAsFile().absolutePath
        ]))
    }
}