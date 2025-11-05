package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*

import java.util.zip.*

import net.neoforged.srgutils.IMappingFile

public abstract class SplitJar extends DefaultTask {
    @InputFile abstract RegularFileProperty getSource()
    @OutputFile abstract RegularFileProperty getSlim()
    @OutputFile abstract RegularFileProperty getExtra()
    
    @TaskAction
    def exec() {
        Utils.init()
        
        new ZipOutputStream(slim.get().getAsFile().newOutputStream()).withCloseable{ so ->
            new ZipOutputStream(extra.get().getAsFile().newOutputStream()).withCloseable{ eo ->
                new ZipInputStream(source.get().getAsFile().newInputStream()).withCloseable{ jin ->
                    for (def entry = jin.nextEntry; entry != null; entry = jin.nextEntry) {
                        def out = entry.name.endsWith(".class") ? so : eo
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