package net.neoforged.neoform.tasks;

import net.neoforged.neoform.dsl.ToolSettings;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.attributes.Bundling;
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

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
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

        try (var logOutput = new BufferedOutputStream(new FileOutputStream(logFile))) {
            getExecOps().javaexec(spec -> {
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
                    writer.append(" Main Class: ").append(getMainClass().get()).append('\n');
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
            spec.getDependencies().addLater(settings.getVersion().map(dependencies::create));
        });
        var classpath = configurations.resolvable(taskName + "Classpath", spec -> {
            spec.extendsFrom(dependencyScope.get());
            spec.getAttributes().attribute(
                    // Since we specify the classpath anyway, just use "normal" dependency bundling.
                    Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL)
            );
        });

        taskProvider.configure(task -> {
            task.getToolClasspath().from(classpath);
            task.getArgs().set(settings.getArgs());
            task.getJvmArgs().set(settings.getJvmArgs());
            task.getJavaVersion().set(settings.getJavaVersion());
        });
    }
}
