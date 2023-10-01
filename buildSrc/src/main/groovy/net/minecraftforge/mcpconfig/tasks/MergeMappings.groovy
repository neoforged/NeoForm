package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*

abstract class MergeMappings extends ToolJarExec {
    @InputFile abstract RegularFileProperty getMappings()
    @InputFile abstract RegularFileProperty getOfficial()
    @OutputFile abstract RegularFileProperty getDest()
    @OutputFile @Optional abstract RegularFileProperty getLog()
    
    @Override
    protected void preExec() {
        def logStream = log.isPresent() ? log.get().getAsFile().newOutputStream() : JarExec.NULL_OUTPUT 
        standardOutput logStream
        errorOutput logStream
        setArgs(Utils.fillVariables(args, [
            'mappings': mappings.get().getAsFile().absolutePath,
            'official': official.get().getAsFile().absolutePath,
            'output': dest.get().getAsFile().absolutePath
        ]))
    }
}