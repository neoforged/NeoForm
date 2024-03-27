package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*

abstract class UnpickTask extends ToolJarExec {
    @InputFile abstract RegularFileProperty getUnpick()
    @InputFile abstract RegularFileProperty getInput()
    @OutputFile abstract RegularFileProperty getDest()
    @OutputFile @Optional abstract RegularFileProperty getLog()
    
    @Override
    protected void preExec() {
        def logStream = log.isPresent() ? log.get().getAsFile().newOutputStream() : JarExec.NULL_OUTPUT
        standardOutput logStream
        errorOutput logStream
        setArgs(Utils.fillVariables(args, [
            'unpick': unpick.get().getAsFile().absolutePath,
            'input': input.get().getAsFile().absolutePath,
            'output': dest.get().getAsFile().absolutePath
        ]))
    }
}