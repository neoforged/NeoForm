package net.neoforged.neoform.tasks;

import kotlin.io.FilesKt;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public abstract class TestWithEclipseCompiler extends DefaultTask {

    @Classpath
    public abstract ConfigurableFileCollection getEclipseCompilerClasspath();

    @InputFile
    public abstract RegularFileProperty getSourcesZip();

    @Classpath
    public abstract ConfigurableFileCollection getCompileClasspath();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input
    public abstract Property<Integer> getJavaVersion();

    @TaskAction
    public void compile() throws IOException {
        var tempDir = new File(getTemporaryDir(), "sources");
        FilesKt.deleteRecursively(tempDir);
        tempDir.mkdirs();

        var createdDirs = new HashSet<File>();

        // Unzip sources
        try (var zipFile = new ZipFile(getSourcesZip().getAsFile().get())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().endsWith(".java")) {
                    var sourceFile = new File(tempDir, entry.getName());
                    var parentDir = sourceFile.getParentFile();
                    if (createdDirs.add(parentDir)) {
                        parentDir.mkdirs();
                    }
                    try (var in = zipFile.getInputStream(entry);
                         var out = new FileOutputStream(sourceFile)) {
                        in.transferTo(out);
                    }
                }
            }
        }

        getExecOperations().javaexec(spec -> {
            spec.executable(getJavaExecutable().get());
            spec.classpath(getEclipseCompilerClasspath());
            if (!getCompileClasspath().isEmpty()) {
                spec.args("-cp", getCompileClasspath().getFiles().stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)));
            }
            spec.args("-encoding", "UTF-8");
            spec.args("--release", getJavaVersion().get());
            // Disable all warnings, since we don't really care about warnings in Mojangs code.
            spec.args("-warn:none");
            // -d none disables class file generation
            spec.args("-d", "none");
            spec.args(getTemporaryDir().getAbsolutePath());
        });
    }

}
