package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import de.undercouch.gradle.tasks.download.DownloadAction
import de.undercouch.gradle.tasks.download.DownloadDetails

public abstract class DownloadLibraries extends DefaultTask {
    @InputFile abstract RegularFileProperty getJson()
    @Input abstract MapProperty<String, Object> getConfig()
    @OutputDirectory abstract RegularFileProperty getDest()
    @Internal DownloadAction action

    DownloadLibraries() {
        action = new DownloadAction(project, this)
        action.overwrite(false)
        action.useETag('all')
    }
    
    @TaskAction
    def exec() {
        Utils.init()
        def files = [:]
        def mavenPaths = [] as Set
        action.dest dest.get().getAsFile()
        action.eachFile(new Action<DownloadDetails>() {
            @Override
            public void execute(DownloadDetails details) {
                details.relativePath = new RelativePath(false, files[details.sourceURL.toString()])
            }
        })
        
        json.get().getAsFile().json.libraries.each{ lib ->
            def artifacts = (lib.downloads.artifact == null ? [] : [lib.downloads.artifact]) + lib.downloads.get('classifiers', [:]).values()
            artifacts.each{ art ->
                if (mavenPaths.add(art.path)) {
                    action.src art.url
                    files[art.url] = art.path
                }
            }
        }
        for (def side : ['client', 'server', 'joined']) {
            if (config.get()?.libraries?.get(side) != null) {
                config.get().libraries.get(side).each { art ->
                    if (mavenPaths.add(art.toMavenPath())) {
                        action.src('https://maven.neoforged.net/releases/' + art.toMavenPath())
                        files['https://maven.neoforged.net/releases/' + art.toMavenPath()] = art.toMavenPath()
                    }
                }
            }
        }
        action.execute()
    }
    
    def download(def url, def target) {
    }
}