package net.minecraftforge.mcpconfig.tasks


import java.util.zip.ZipFile
import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import groovy.json.JsonSlurper

public abstract class CreateProjectTemplate extends DefaultTask {
    @Input abstract Property<String> getDistro()
    @InputFile abstract RegularFileProperty getTemplate()
    @Optional @InputFile abstract RegularFileProperty getMeta()
    @Optional @InputFile abstract RegularFileProperty getBundle()
    @Input abstract ListProperty<String> getLibraries()
    @Input abstract MapProperty<String, String> getReplace()
    @Internal abstract ConfigurableFileCollection getDirectories()
    @Input abstract Property<String> getVersion()
    @Optional @InputFile abstract RegularFileProperty getAssetJsonFile()

    @OutputDirectory abstract RegularFileProperty getDest()
    
    def library(lib) {
        libraries.add(lib)
    }
    
    def libraryFile(lib) {
        libraries.add("files('" + lib.absolutePath.replace('\\', '/') + "')")
    }
    
    def replace(key, value) {
        replace.put(key, value)
    }
    
    def replaceFile(key, RegularFileProperty value) {
        if (value == null) {
            replace(key, 'null')
        } else {
            replace(key, escapePathForGradle(value.get().getAsFile().absolutePath))
            directories + value
        }
    }

    def replaceFile(key, File value) {
        if (value == null) {
            replace(key, 'null')
        } else {
            replace(key, escapePathForGradle(value.absolutePath))
            directories + value
        }
    }

    private static String escapePathForGradle(String path) {
        return "'" + path.replace('\\', '/') + "'"
    }

    @TaskAction
    protected void exec() {
        if (!dest.get().getAsFile().exists())
            dest.get().getAsFile().mkdirs()
        
        for (def dir : directories) {
            if (!dir.exists())
                dir.mkdirs()
        }
        
        def data = template.get().getAsFile().text
        def libs = []
        
        if (bundle.isPresent()) {
            def zf = new ZipFile(bundle.get().getAsFile())
            zf.entries().findAll{ it.name.equals('META-INF/libraries.list') }.each {
                zf.getInputStream(it).text.split('\r?\n').each { line -> 
                    libs.add("'" + line.split('\t')[1] + "'")
                }
            }
        }
        
        if (meta.isPresent()) {
            def json = new JsonSlurper().parse(meta.get().getAsFile())
            libs += json.libraries.findAll { Utils.testJsonRules(it.rules) }.collect { lib -> "'${lib.name}' " }.unique { a, b -> a <=> b }
        }
        libs += libraries.get()

        data = data.replace('{libraries}', 'implementation ' + libs.join('\n    implementation '))
        data = data.replace('{distro}', distro.get())
        for (def k : replace.keySet().get()) {
            def v = replace.get().get(k)
            data = data.replace(k, v)
        }

        if (getAssetJsonFile().isPresent()) {
            def assetInfo = new JsonSlurper().parse(getAssetJsonFile().get().asFile)
            data = data.replace("{assets}", escapePathForGradle(assetInfo.assets))
        }

        new File(dest.get().getAsFile(), 'build.gradle').withWriter('UTF-8') { it.write(data) }
    }
}