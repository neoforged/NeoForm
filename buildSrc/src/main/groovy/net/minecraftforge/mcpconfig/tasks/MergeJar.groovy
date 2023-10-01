package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*

abstract class MergeJar extends ToolJarExec {
    @InputFile abstract RegularFileProperty getClient()
    @InputFile abstract RegularFileProperty getServer()
    @Input abstract Property<String> getVersion()
    @OutputFile abstract RegularFileProperty getDest()
    
    @Override
    protected void preExec() {
        setArgs(Utils.fillVariables(args, [
            'client': client.get().getAsFile().absolutePath,
            'server': server.get().getAsFile().absolutePath,
            'version': version.get(),
            'output': dest.get().getAsFile().absolutePath
        ]))
    }
}