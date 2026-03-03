package net.neoforged.neoform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class DeduplicateSources extends DefaultTask {

    @Nested
    public abstract MapProperty<String, Source> getSides();

    @TaskAction
    public void deduplicate() throws IOException {
        Set<String> finished = new HashSet<>();
        Map<String, Map<String, Integer>> hashes = new HashMap<>();
        boolean progress = false;
        while (finished.size() < getSides().get().size()) {
            progress = false;
            for (Map.Entry<String, Source> entry : getSides().get().entrySet()) {
                if (!finished.containsAll(entry.getValue().getContains().get()))
                    continue;
                progress = true;
                Map<String, Integer> sourceHashes = new HashMap<>();
                try (
                        FileSystem input = FileSystems.newFileSystem(entry.getValue().getInput().get().getAsFile().toPath());
                        ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(entry.getValue().getOutput().get().getAsFile().toPath()))) {
                    Path root = input.getPath("/");
                    for (Path path : Files.walk(root).filter(Files::isRegularFile).toList()) {
                        byte[] bytes = Files.readAllBytes(path);
                        Path relative = root.relativize(path);
                        if (path.startsWith("/META-INF")) {
                            output.putNextEntry(new ZipEntry(relative.toString()));
                            output.write(bytes);
                            continue;
                        }

                        int hash = Arrays.hashCode(bytes);
                        boolean present = false;
                        for (String contained : entry.getValue().getContains().get()) {
                            Integer otherHash = hashes.get(contained).get(relative.toString());
                            if (otherHash != null && hash != otherHash) {
                                throw new GradleException(relative + " does not match on " + entry.getKey() + " and " + contained);
                            } else if (otherHash != null) {
                                present = true;
                            }
                        }

                        if (!present) {
                            output.putNextEntry(new ZipEntry(relative.toString()));
                            output.write(bytes);
                        }

                        sourceHashes.put(relative.toString(), hash);
                    }
                }
                hashes.put(entry.getKey(), sourceHashes);
                finished.add(entry.getKey());
            }
            if (!progress) {
                throw new GradleException("Deduplicate sources cannot progress");
            }
        }
    }

    public Source getSource(String name) {
        if (!getSides().get().containsKey(name)) {
            Source source = getProject().getObjects().newInstance(Source.class);
            getSides().put(name, source);
            // Gradle refuses to acknolowedge that this is an output without this even when getOutput has OutputFile
            getOutputs().file(source.getOutput());
        }
        return getSides().get().get(name);
    }

    public static abstract class Source {
        @InputFile
        public abstract RegularFileProperty getInput();

        @Internal
        public abstract RegularFileProperty getOutput();

        @Input
        public abstract ListProperty<String> getContains();
    }
}
