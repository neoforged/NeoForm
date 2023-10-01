package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*

abstract class ExtractBundleJar extends ToolJarExec {
    @InputFile abstract RegularFileProperty getInput()
    @OutputFile abstract RegularFileProperty getDest()
    
    @Override
    protected void preExec() {
        standardOutput = JarExec.NULL_OUTPUT
        setArgs(Utils.fillVariables(args, [
            'input': input.get().getAsFile().absolutePath,
            'output': dest.get().getAsFile().absolutePath
        ]))
    }
}