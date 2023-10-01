package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*

import java.util.TreeSet

abstract class CreateFernflowerLibraries extends SingleFileOutput {
    @InputFile abstract RegularFileProperty getMeta()
    @Input abstract MapProperty<String, Object> getConfig()
    @Input abstract Property<String> getSide()
    @InputDirectory abstract RegularFileProperty getRoot()
    @InputFiles abstract ConfigurableFileCollection getExtras()
    
    @TaskAction
    def exec() {
        Utils.init()
        def libs = new TreeSet<>()
        
        libs.addAll(extras)
        
        if ('client'.equals(side.get()) || 'joined'.equals(side.get())) {
            meta.get().getAsFile().json.libraries.each{ lib ->
                if (lib.downloads.artifact != null)
                    libs.add(new File(root.get().getAsFile(), lib.downloads.artifact.path))
                
                lib.downloads.get('classifiers', [:]).forEach{ k,v ->
                    if (!k.contains('natives') && !'sources'.equals(k) && !'javadoc'.equals(k))
                        libs.add(new File(root.get().getAsFile(), v.path))
                }
            }
        }
        
        config.get().libraries.get(side.get())?.collect{ it.toMavenPath() }?.each { libs.add(new File(root.get().getAsFile(), it)) }
        dest.get().getAsFile().text = libs.collect{ '-e=' + it.absolutePath }.join('\n')
    }
}