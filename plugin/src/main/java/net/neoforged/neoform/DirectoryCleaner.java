package net.neoforged.neoform;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Predicate;

public final class DirectoryCleaner {
    private DirectoryCleaner() {
    }

    /**
     * {@return number of files that were deleted}
     */
    public static int cleanDirectory(Path rootDir, Predicate<Path> deletePredicate) throws IOException {
        int[] filesDeleted = new int[1];
        Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (deletePredicate.test(file)) {
                    filesDeleted[0]++;
                    Files.delete(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) throws IOException {
                // Delete empty dirs
                if (dir.startsWith(rootDir) && !dir.equals(rootDir)) {
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
        return filesDeleted[0];
    }
}
