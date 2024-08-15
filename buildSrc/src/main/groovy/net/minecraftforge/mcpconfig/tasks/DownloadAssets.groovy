package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

@DisableCachingByDefault(because = "has its own caching")
abstract class DownloadAssets extends DefaultTask {
    /**
     * Writes a JSON file detailing the path to the asset index and asset root.
     */
    @OutputFile
    abstract RegularFileProperty getAssetJson()

    /**
     * Points to the single executable jar for
     * https://projects.neoforged.net/neoforged/neoformruntime
     */
    @InputFiles
    abstract ConfigurableFileCollection getNfrt();

    /**
     * The Minecraft version matching the NeoForge version to install.
     */
    @Input
    abstract Property<String> getMinecraftVersion();

    @Inject
    abstract ExecOperations getExecOperations();

    @TaskAction
    def exec() {
        // Download Minecraft Assets and write asset index and location to JSON file to read back for starting the game
        execOperations.javaexec(spec -> {
            spec.classpath(getNfrt().getSingleFile());
            spec.args(
                    "download-assets",
                    "--minecraft-version",
                    minecraftVersion.get(),
                    "--write-json",
                    assetJson.get().asFile.getAbsolutePath()
            );
        });
    }
}
