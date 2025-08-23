package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream

import java.nio.file.Files

/**
 * Compresses a file using LZMA (XZ) compression.
 * This aims to use the same type of compression we use for the binpatches in our installer.
 */
abstract class CompressLzma extends DefaultTask {
    @InputFile
    abstract RegularFileProperty getInput();

    @OutputFile
    abstract RegularFileProperty getOutput();

    @TaskAction
    protected void compress() {
        File inputFile = input.asFile.get()
        File outputFile = output.asFile.get()

        byte[] data
        try {
            data = Files.readAllBytes(inputFile.toPath())
        } catch (IOException e) {
            throw new GradleException("Failed to read the mappings file " + inputFile, e)
        }

        LZMA2Options options = new LZMA2Options()
        try (FileOutputStream fos = new FileOutputStream(outputFile)
             OutputStream lzma = new LZMAOutputStream(new BufferedOutputStream(fos), options, data.length)) {
            lzma.write(data)
        } catch (IOException e) {
            throw new GradleException("Failed to compress the mappings.", e)
        }
    }
}
