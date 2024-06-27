package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*
import de.undercouch.gradle.tasks.download.*

public abstract class DownloadAssets extends DefaultTask {
    @InputFile abstract RegularFileProperty getJson()
    @OutputDirectory abstract RegularFileProperty getDest()
    @Internal DownloadAction indexAction
    @Internal DownloadAction assetAction

    DownloadAssets() {
        indexAction = new DownloadAction(project, this)
        indexAction.onlyIfModified(true)
        indexAction.useETag('all')
        assetAction = new DownloadAction(project, this)
        assetAction.overwrite(false)
        assetAction.useETag('all')
    }
    
    @TaskAction
    def exec() {
        Utils.init()
        
        def dl = json.get().getAsFile().json.assetIndex
        def index = new File(dest.get().getAsFile(), 'indexes/' + dl.id + '.json')
        if (index.sha1 != dl.sha1) {
            indexAction.src dl.url
            indexAction.dest index
            indexAction.execute().join()
        }
        
        def assets = [] as Set // Some assets are copies of other assets

        assetAction.dest(new File(dest.get().getAsFile(), 'objects'))
        index.json.objects.each { asset ->
            def key = asset.value.hash.take(2) + '/' + asset.value.hash
            def target = new File(dest.get().getAsFile(), 'objects/' + key)
            if (!target.exists() && assets.add(asset.value.hash)) {
                assetAction.src('https://resources.download.minecraft.net/' + key)
            }
        }

        if (assets.size() > 1) {
            assetAction.eachFile(new Action<DownloadDetails>() {
                @Override
                public void execute(DownloadDetails details) {
                    details.relativePath = new RelativePath(false, details.sourceURL.toString().replace('https://resources.download.minecraft.net/', ''))
                }
            })
        } else if (assets.size() == 1) {
            assetAction.dest(new File(dest.get().getAsFile(), 'objects/' + assets[0].take(2) + '/' + assets[0]))
        }

        if (!assets.isEmpty()) {
            assetAction.execute()
        }
    }

    def download(def url, def target) {
        def ret = new DownloadAction(project, this)
        ret.overwrite(false)
        ret.useETag('all')
        ret.src url
        ret.dest target
        ret.execute()
    }
}