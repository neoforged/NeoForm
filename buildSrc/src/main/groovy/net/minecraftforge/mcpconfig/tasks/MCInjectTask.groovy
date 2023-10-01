package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.file.*
import org.gradle.api.tasks.*

//Deprecated.. Only here until we migrate all the old versions to not use MCInject
public abstract class MCInjectTask extends ToolJarExec {
    @InputFile abstract RegularFileProperty getAccess()
    @InputFile abstract RegularFileProperty getConstructors()
    @InputFile abstract RegularFileProperty getExceptions()
    @InputFile abstract RegularFileProperty getInput()
    @OutputFile abstract RegularFileProperty getLog()
    @OutputFile abstract RegularFileProperty getDest()
    
    @Override
    protected void preExec() {
        setArgs(Utils.fillVariables(args, [
            'access': access.get().getAsFile(),
            'constructors': constructors.get().getAsFile(),
            'exceptions': exceptions.get().getAsFile(),
            'log': log.get().getAsFile(),
            'input': input.get().getAsFile(),
            'output': dest.get().getAsFile()
        ]))
    }
}