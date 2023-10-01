package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*

import java.util.zip.*

public abstract class DeduplicateJars extends DefaultTask {
    @InputFile abstract RegularFileProperty getClientIn()
    @InputFile abstract RegularFileProperty getJoinedIn()
    @InputFile abstract RegularFileProperty getServerIn()
    
    @OutputFile abstract RegularFileProperty getDuplicates()
    
    @OutputFile abstract RegularFileProperty getClientOut()
    @OutputFile abstract RegularFileProperty getJoinedOut()
    @OutputFile abstract RegularFileProperty getServerOut()
    
    @TaskAction
    def exec() {
        Utils.init()
        
        def inputs = [
            client: loadZip(clientIn.get().getAsFile()),
            server: loadZip(serverIn.get().getAsFile()),
            joined: loadZip(joinedIn.get().getAsFile())
        ]
        
        def outputs = [
            client: clientOut.get().getAsFile(),
            server: serverOut.get().getAsFile(),
            joined: joinedOut.get().getAsFile()
        ]
        
        def dupes = []
        
        new ZipOutputStream(duplicates.get().getAsFile().newOutputStream()).withCloseable{ du ->
            inputs.client.each { name, data ->
                if (inputs.server.containsKey(name) && inputs.joined.containsKey(name)) {
                    if (inputs.client[name] == inputs.server[name] &&
                        inputs.client[name] == inputs.joined[name]) {
                        du.putNextEntry(new ZipEntry(name))
                        du.write(inputs.client[name])
                        dupes.add(name)
                    }
                }
            }
        }
        inputs.each{ side ->
            dupes.each{ side.value.remove(it) }
        }
        
        inputs.each{ side, files ->
            new ZipOutputStream(outputs[side].newOutputStream()).withCloseable{ out ->
                files.each{ name, data -> 
                    out.putNextEntry(new ZipEntry(name))
                    out.write(data)
                }
            }
        }
    }
    
    def loadZip(def file) {
        def ret  = [:]
        def zip = new ZipFile(file)
        zip.entries().each{ entry ->
            if (!entry.isDirectory())
                ret[entry.name] = zip.getInputStream(entry).bytes
        }
        zip.close()
        return ret
    }
}