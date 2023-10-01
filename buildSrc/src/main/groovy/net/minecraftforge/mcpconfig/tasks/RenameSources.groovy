package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*

import java.nio.file.Files

import net.minecraftforge.srgutils.*

public abstract class RenameSources extends DefaultTask {
    @InputDirectory abstract RegularFileProperty getInput()
    @InputFile abstract RegularFileProperty getSrg()
    @InputFile abstract RegularFileProperty getOfficial()
    @OutputDirectory abstract RegularFileProperty getDest()
    
    @TaskAction
    def exec() {
        Utils.init()
        def names = loadMappings()
        def root = input.get().getAsFile().toPath()
        
        Files.walk(root).withCloseable{ stream ->
            stream.each { entry ->
                if(!Files.isDirectory(entry)) {
                    def path = root.relativize(entry).toString()
                    def out = new File(dest.get().getAsFile(), path)
                    if (!out.parentFile.exists())
                        out.parentFile.mkdirs();
                        
                    def data
                    if (path.endsWith('.java')) {
                        data = entry.toFile().text.replaceAll(/f_\d+_|m_\d+_|func_\d+_[a-zA-Z_]+|field_\d+_[a-zA-Z_]+/) { 
                            name -> names.getOrDefault(name, name)
                        } 
                    } else
                        data = entry.toFile().text
                        
                    Files.newBufferedWriter(out.toPath()).withCloseable { writer ->
                        writer.write(data)
                    }
                }
            }
        }
    }
    
    def loadMappings() {
        def ret = [:]
        def msrg = IMappingFile.load(srg.get().getAsFile())
        def moff = IMappingFile.load(official.get().getAsFile()).reverse()
        msrg.classes.each{scls -> 
            def ocls = moff.getClass(scls.original)
            if (ocls != null) {
                scls.fields.each{ sfld ->
                    if (sfld.mapped.startsWith('f_') || sfld.mapped.startsWith('field_'))
                        ret.put(sfld.mapped, ocls.remapField(sfld.original))
                }
                scls.methods.each{ smtd ->
                    if (smtd.mapped.startsWith('m_') || smtd.mapped.startsWith('func_'))
                        ret.put(smtd.mapped, ocls.remapMethod(smtd.original, smtd.descriptor))
                }
            }
        }
        return ret
    }
}