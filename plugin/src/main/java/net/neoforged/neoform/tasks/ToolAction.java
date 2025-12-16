package net.neoforged.neoform.tasks;

import net.neoforged.neoform.dsl.ToolSettings;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ToolAction extends DefaultTask {
    @Inject
    protected abstract ExecOperations getExecOps();

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    @Internal
    public abstract Property<JavaLauncher> getLauncher();

    @Input
    @Optional
    public abstract Property<String> getMainClass();

    @Classpath
    public abstract ConfigurableFileCollection getToolClasspath();

    @Input
    public abstract ListProperty<String> getArgs();

    @Input
    public abstract ListProperty<String> getJvmArgs();

    @Input
    public abstract Property<Integer> getJavaVersion();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getLogFile();

    @Inject
    public ToolAction() {
        var javaLangVersion = getJavaVersion().map(JavaLanguageVersion::of);
        getLauncher().convention(
                getJavaToolchainService().launcherFor(spec -> spec.getLanguageVersion().set(javaLangVersion))
        );
    }

    protected final void exec(Map<String, String> placeholders) throws IOException {
        File logFile = getLogFile().getAsFile().getOrNull();
        if (logFile == null) {
            logFile = new File(getTemporaryDir(), "tool.log");
        }

        ExecResult result;
        try (var logOutput = new BufferedOutputStream(new FileOutputStream(logFile))) {
            result = getExecOps().javaexec(spec -> {
                spec.setIgnoreExitValue(true);
                spec.setStandardOutput(logOutput);
                spec.setErrorOutput(logOutput);

                spec.setExecutable(getLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
                spec.getMainClass().set(getMainClass());
                spec.classpath(getToolClasspath());
                spec.args(replacePlaceholders(getArgs().get(), placeholders));
                spec.jvmArgs(replacePlaceholders(getJvmArgs().get(), placeholders));

                // Dump the arguments
                var writer = new OutputStreamWriter(logOutput, StandardCharsets.UTF_8);
                try {
                    writer.append("Running using:\n");
                    if (getMainClass().isPresent()) {
                        writer.append(" Main Class: ").append(getMainClass().get()).append('\n');
                    }
                    writer.append(" Classpath:\n");
                    for (var file : getToolClasspath()) {
                        writer.append("  - ").append(file.getAbsolutePath()).append('\n');
                    }
                    writer.append(" JVM Args:\n");
                    for (var arg : spec.getAllJvmArgs()) {
                        writer.append("  - ").append(arg).append('\n');
                    }
                    writer.append(" Args:\n");
                    for (var arg : spec.getArgs()) {
                        writer.append("  - ").append(arg).append('\n');
                    }
                    writer.flush();
                    logOutput.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        if (result.getExitValue() != 0) {
            // Tail the log file
            var logFileExcerpt = tailLogFile(logFile);

            throw new GradleException("Executing external tool failed with status code " + result.getExitValue() + "\n" + logFileExcerpt);
        }
    }

    private List<String> replacePlaceholders(List<String> strings, Map<String, String> placeholders) {
        return new ArrayList<>(strings.stream().map(string -> replacePlaceholders(string, placeholders)).toList());
    }

    private String replacePlaceholders(String string, Map<String, String> placeholders) {
        Pattern placeholderPattern = Pattern.compile("\\{([^}]+)}");
        return placeholderPattern.matcher(string).replaceAll(matchResult -> {
            var placeholder = matchResult.group(1);
            var value = placeholders.get(placeholder);
            if (value == null) {
                throw new IllegalArgumentException("Placeholder '" + placeholder + "' is undefined.");
            }
            return Matcher.quoteReplacement(value);
        });
    }

    public static void configure(Project project, TaskProvider<? extends ToolAction> taskProvider, ToolSettings settings) {
        var configurations = project.getConfigurations();

        var taskName = taskProvider.getName();
        var dependencyScope = configurations.dependencyScope(taskName + "Tool", spec -> {
            var dependencies = project.getDependencyFactory();
            spec.getDependencies().addAllLater(settings.getClasspath().map(i -> i.stream().<Dependency>map(notation -> {
                var dependency = dependencies.create(notation);
                dependency.setTransitive(false);
                return dependency;
            }).toList()));
        });
        var classpath = configurations.resolvable(taskName + "Classpath", spec -> {
            spec.extendsFrom(dependencyScope.get());
        });

        taskProvider.configure(task -> {
            task.getToolClasspath().from(classpath);
            task.getMainClass().set(settings.getMainClass());
            task.getArgs().set(settings.getArgs());
            task.getJvmArgs().set(settings.getJvmArgs());
            task.getJavaVersion().set(settings.getJavaVersion());
        });
    }

    private static String tailLogFile(File logFile) {
        var result = new StringWriter();
        var writer = new PrintWriter(result); 
        writer.println("Last lines of " + logFile + ":");
        writer.println("------------------------------------------------------------");
        try (var raf = new RandomAccessFile(logFile, "r")) {
            raf.seek(Math.max(0, raf.length() - 1));
            int bytesRead = 0;
            int linesRead = 0;
            while (raf.getFilePointer() > 0 && raf.getFilePointer() < raf.length() && bytesRead < 2048 && linesRead < 30) {
                byte b = raf.readByte();
                bytesRead++;
                if (b == '\n') {
                    linesRead++;
                }
                raf.seek(Math.max(0, raf.getFilePointer() - 2));
            }

            var toRead = raf.length() - raf.getFilePointer();
            var data = new byte[(int) toRead];
            raf.readFully(data);
            writer.println(new String(data, StandardCharsets.UTF_8));

        } catch (IOException e) {
            writer.println("Failed to tail log-file " + logFile);
        }
        writer.println("------------------------------------------------------------");
        return result.toString();
    }
}
