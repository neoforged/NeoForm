package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*

import java.util.zip.*

import net.neoforged.srgutils.*

public abstract class RenameMappings extends SingleFileOutput {
    @InputFile abstract RegularFileProperty getIntermediate()
    @InputFile @Optional abstract RegularFileProperty getOfficial()
    
    @TaskAction
    def exec() {
        Utils.init()
        def ret
        if (official.isPresent()) {
            def inter = INamedMappingFile.load(intermediate.get().getAsFile()).getMap('obf', 'srg')
            def offic = IMappingFile.load(official.get().getAsFile()).reverse()
            ret = inter.rename(new IRenamer() {
                String rename(IMappingFile.IClass cls) {
                    return offic.remapClass(cls.original)
                }
            })
        } else {
            ret = IMappingFile.load(intermediate.get().getAsFile())
        }
        
        ret.write(dest.get().getAsFile().toPath(), IMappingFile.Format.TSRG2, false)
    }
}