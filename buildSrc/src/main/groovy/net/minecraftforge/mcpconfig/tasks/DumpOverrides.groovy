package net.minecraftforge.mcpconfig.tasks

import java.util.HashSet
import org.gradle.api.file.*
import org.gradle.api.tasks.*
import groovy.io.FileType
import groovy.json.JsonSlurper
import net.neoforged.srgutils.IMappingFile

import static org.objectweb.asm.Opcodes.*

public abstract class DumpOverrides extends SingleFileOutput {
    @InputFile abstract RegularFileProperty getSrg()
    @InputFile abstract RegularFileProperty getMeta()
    
    @TaskAction
    protected void exec() {
        Utils.init()
        def srgO = IMappingFile.load(srg.get().getAsFile())
        
        def methods = [] as HashSet

        meta.get().getAsFile().json.each{ k,v ->
            v.methods?.each{ sig,data ->
                if (data['override']) {
                    def (name, desc) = sig.split('\\(')
                    desc = '(' + desc
                    def mapped = srgO.getClass(k)?.remapMethod(name, desc)
                    if (mapped == null) {
                        print('Missing srg mapping for access: ' + k + '/' + sig + '\n')
                    } else {
                        methods.add(srgO.remapClass(k) + '/' + mapped + ' ' + srgO.remapDescriptor(desc))
                    }
                }
            }
        }

        dest.get().getAsFile().withWriter('UTF-8') { writer ->
            methods = methods.sort{it}
            methods.each{ writer.write(it + '\n') }
        }
    }
}