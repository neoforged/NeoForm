package net.neoforged.neoform.tasks;

import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class Decompile extends ToolAction {
    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Classpath
    public abstract ConfigurableFileCollection getInputClasspath();

    @Inject
    public Decompile() {
        var layout = getProject().getLayout();
        getLogFile().convention(layout.file(getOutput().map(rf -> {
            var outputZip = rf.getAsFile().toPath();
            return outputZip.resolveSibling(outputZip.getFileName() + "_decompiler.log").toFile();
        })));
    }

    @Inject
    protected abstract ArchiveOperations getArchiveOperations();

    @TaskAction
    public void execute() throws IOException {
        var inputJar = getInput().getAsFile().get();
        var outputFolder = getOutput().getAsFile().get().toPath().resolveSibling("temp_output_folder");
        // Code from https://stackoverflow.com/questions/35988192/java-nio-most-concise-recursive-directory-delete
        if (Files.exists(outputFolder)) {
            try (Stream<Path> walk = Files.walk(outputFolder)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }

        // TODO: this implies changes in the produced neoform config
        var libraries = new StringBuilder();

        boolean first = true;
        for (var file : getInputClasspath()) {
            if (!first) {
                libraries.append(File.pathSeparator);
            }
            first = false;
            libraries.append(file.getAbsolutePath());
        }

        exec(Map.of(
                "input", inputJar.getAbsolutePath(),
                "inputLibraries", libraries.toString(),
                "output", outputFolder.toAbsolutePath().toString()
        ));


        var outputZip = getOutput().getAsFile().get();
        if (outputZip.exists()) {
            Files.delete(outputZip.toPath());
        }
        pack(outputFolder, outputZip.toPath());
    }

    public static void pack(Path sourceDir, Path zipTarget) throws IOException {
        Path p = Files.createFile(zipTarget);
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p));
             Stream<Path> files = Files.walk(sourceDir)) {
            files
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourceDir.relativize(path).toString());
                        try {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }
}
