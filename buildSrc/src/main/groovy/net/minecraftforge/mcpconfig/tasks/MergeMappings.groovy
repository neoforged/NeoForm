package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.tasks.*

class MergeMappings extends ToolJarExec {
    @InputFile File mappings
    @InputFile File official
    @OutputFile File dest
    @OutputFile @Optional File log = null
    
    @Override
    protected void preExec() {
        def logStream = log == null ? JarExec.NULL_OUTPUT : log.newOutputStream()
        standardOutput logStream
        errorOutput logStream
        setArgs(Utils.fillVariables(args, [
            'mappings': mappings.absolutePath,
            'official': official.absolutePath,
            'output': dest.absolutePath
        ]))
    }
}