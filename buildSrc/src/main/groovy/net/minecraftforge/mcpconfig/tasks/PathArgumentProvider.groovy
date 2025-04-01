package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

abstract class ZipPathArgumentProvider implements CommandLineArgumentProvider {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getFile()

    @Override
    Iterable<String> asArguments() {
        [file.get().asFile.absolutePath]
    }
}
