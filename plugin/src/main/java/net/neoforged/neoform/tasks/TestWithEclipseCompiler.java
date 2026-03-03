package net.neoforged.neoform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

public abstract class TestWithEclipseCompiler extends DefaultTask {

    @Classpath
    public abstract ConfigurableFileCollection getEclipseCompilerClasspath();

    @InputFiles
    public abstract ConfigurableFileCollection getSources();

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
            spec.args(getSources().getFiles().stream().map(File::getAbsolutePath).toArray(Object[]::new));
        });
    }

}
