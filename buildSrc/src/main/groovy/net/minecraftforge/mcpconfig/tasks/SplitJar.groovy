package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*

import java.util.zip.*

import net.minecraftforge.srgutils.IMappingFile

public abstract class SplitJar extends DefaultTask {
    @InputFile abstract RegularFileProperty getMappings()
    @InputFile abstract RegularFileProperty getSource()
    @OutputFile abstract RegularFileProperty getSlim()
    @OutputFile abstract RegularFileProperty getExtra()
    
    @TaskAction
    def exec() {
        Utils.init()
        def classes = IMappingFile.load(mappings.get().getAsFile()).classes.collect{ it.original + '.class' }.toSet()
        
        new ZipOutputStream(slim.get().getAsFile().newOutputStream()).withCloseable{ so ->
            new ZipOutputStream(extra.get().getAsFile().newOutputStream()).withCloseable{ eo ->
                new ZipInputStream(source.get().getAsFile().newInputStream()).withCloseable{ jin ->
                    for (def entry = jin.nextEntry; entry != null; entry = jin.nextEntry) {
                        def out = classes.contains(entry.name) ? so : eo
                        def oentry = new ZipEntry(entry.name)
                        oentry.lastModifiedTime = entry.lastModifiedTime
                        out.putNextEntry(oentry)
                        out << jin
                    }
                }
            }
        }
    }
}