package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*

abstract class CreateAssetsDataJson extends SingleFileOutput {
    @InputDirectory abstract RegularFileProperty getAssets()
    @InputFile abstract RegularFileProperty getJson()
    
    @TaskAction
    def exec() {
        Utils.init()
        def data = [
            assets: assets.get().asFile.absolutePath.replace("\\", "/"),
            asset_index: json.get().asFile.json.assetIndex.id
        ]
        dest.get().asFile.json = data
    }
}