package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.file.*
import org.gradle.api.tasks.*
import java.util.zip.*

@CacheableTask
public abstract class FernflowerTask extends ToolJarExec {
    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    abstract RegularFileProperty getLibraries()
    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    abstract RegularFileProperty getInput()
    @OutputFile
    abstract RegularFileProperty getLog()
    @OutputFile
    abstract RegularFileProperty getDest()
    
    @Override
    protected void preExec() {
        def logStream = log.get().getAsFile().newOutputStream()
        standardOutput logStream
        errorOutput logStream
        setArgs(Utils.fillVariables(args, [
            'libraries': libraries.get().getAsFile(),
            'input': input.get().getAsFile(),
            'output': dest.get().getAsFile()
        ]))
    }
    
    @Override
    protected void postExec() {
        if (!dest.get().getAsFile().exists())
            throw new IllegalStateException('Could not find fernflower output: ' + dest)
        def failed = []
        new ZipFile(dest.get().getAsFile()).withCloseable{ zip -> 
            zip.entries().findAll{ !it.directory && it.name.endsWith('.java') }.each { e ->
                def data = zip.getInputStream(e).text
                if (data.isEmpty() || data.contains("\$VF: Couldn't be decompiled"))
                    failed.add(e.name)
            }
        }
        if (!failed.isEmpty()) {
            logger.lifecycle('Failed to decompile: ')
            failed.each{ logger.lifecycle('  ' + it) }
            throw new IllegalStateException('Decompile failed')
        }
    }
}