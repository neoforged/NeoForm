package net.neoforged.neoform.tasks;

import io.codechicken.diffpatch.cli.DiffOperation;
import io.codechicken.diffpatch.util.Input;
import io.codechicken.diffpatch.util.Output;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * This task compares the modified sources in the workspace against the original source zip produced
 * by the decompiler, and updates the patches stored in the src/patches directory accordingly.
 */
public abstract class CreatePatches extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getSourcesZip();

    @InputDirectory
    public abstract DirectoryProperty getModifiedSources();

    @OutputDirectory
    public abstract DirectoryProperty getPatchesDir();

    @TaskAction
    public void generateSourcePatches() throws IOException {
        // Let it create a folder in the temp dir
        var patchesDir = getTemporaryDir().toPath().resolve("patches");

        // Build a zip for DiffPatch with only the .java files, otherwise it tries to remove the resources
        var baseSourcesZip = new File(getTemporaryDir(), "base_sources.zip");
        buildJavaSourceZip(getSourcesZip().getAsFile().get(), baseSourcesZip);

        var builder = DiffOperation.builder()
                .logTo(getLogger()::lifecycle)
                .baseInput(Input.MultiInput.detectedArchive(baseSourcesZip.toPath()))
                .changedInput(Input.MultiInput.folder(getModifiedSources().get().getAsFile().toPath()))
                .patchesOutput(Output.MultiOutput.folder(patchesDir))
                .autoHeader(true)
                .level(io.codechicken.diffpatch.util.LogLevel.WARN)
                .summary(false)
                .aPrefix("a/")
                .bPrefix("b/")
                .lineEnding("\n");

        var result = builder.build().operate();

        int exit = result.exit;
        if (exit != 0 && exit != 1) {
            throw new GradleException("DiffPatch failed with exit code: " + exit);
        }

        // Only when successful, update the patches source dir
        updatePatchesInSourceDir(patchesDir, getPatchesDir().getAsFile().get().toPath());
    }

    private void updatePatchesInSourceDir(Path patchesDir, Path patchesDestination) throws IOException {
        var addedFolders = new HashSet<Path>();
        var patchesWritten = new HashSet<Path>();
        var newPatches = new AtomicInteger();
        var unchangedPatches = new AtomicInteger();
        var modifiedPatches = new AtomicInteger();
        var removedPatches = new AtomicInteger();
        Files.walkFileTree(patchesDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().endsWith(".patch")) {
                    var relativePath = patchesDir.relativize(file);
                    var destination = patchesDestination.resolve(relativePath);
                    if (addedFolders.add(destination.getParent())) {
                        Files.createDirectories(destination.getParent());
                    }

                    if (Files.exists(destination)) {
                        if (!Arrays.equals(
                                Files.readAllBytes(file),
                                Files.readAllBytes(destination)
                        )) {
                            modifiedPatches.incrementAndGet();
                        } else {
                            unchangedPatches.incrementAndGet();
                        }
                    } else {
                        newPatches.incrementAndGet();
                    }

                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                    patchesWritten.add(destination.toAbsolutePath());
                }

                return FileVisitResult.CONTINUE;
            }
        });

        // Delete orphaned patches
        Files.walkFileTree(patchesDestination, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().endsWith(".patch") && !patchesWritten.contains(file.toAbsolutePath())) {
                    Files.delete(file);
                    removedPatches.incrementAndGet();
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) throws IOException {
                // Delete empty patch dirs
                if (dir.startsWith(patchesDestination) && !dir.equals(patchesDestination)) {
                    var deleteDir = true;
                    try (var content = Files.list(dir)) {
                        deleteDir = content.findAny().isEmpty();
                    }
                    if (deleteDir) {
                        Files.delete(dir);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        getLogger().lifecycle("Patches added: {}, modified: {}, deleted: {}, unchanged: {}",
                newPatches.get(), modifiedPatches.get(), removedPatches.get(), unchangedPatches.get());
    }

    private static void buildJavaSourceZip(File fullSourceZip, File javaSourceZip) throws IOException {
        try (var zip = new ZipFile(fullSourceZip);
             var zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(javaSourceZip)))) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().endsWith(".java")) {
                    zipOut.putNextEntry(entry);
                    try (var in = zip.getInputStream(entry)) {
                        in.transferTo(zipOut);
                    }
                    zipOut.closeEntry();
                }
            }
        }
    }
}
