package net.neoforged.neoform.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.neoform.dsl.NeoFormSideExtension;
import net.neoforged.neoform.dsl.ToolSettings;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes the NeoForm config based on the information in the settings.
 */
public abstract class CreateConfig extends DefaultTask {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    @Optional
    public abstract Property<String> getMinecraftVersionManifestUrl();

    @Input
    public abstract Property<Integer> getJavaVersion();

    @Input
    public abstract Property<String> getEncoding();

    @Input
    public abstract Property<ToolSettings> getDecompiler();

    @Input
    public abstract MapProperty<String, Side> getSides();

    @TaskAction
    public void createConfig() throws IOException {
        var functionsDef = new JsonObject();
        functionsDef.add("decompile", createFunction(getDecompiler().get()));

        JsonObject librariesRoot = new JsonObject();
        JsonObject stepsRoot = new JsonObject();
        JsonObject patches = new JsonObject();
        for (Map.Entry<String, Side> sideEntry : getSides().get().entrySet()) {
            Side side = sideEntry.getValue();
            String name = sideEntry.getKey();
            if (side.getPreProcessJar() != null) {
                functionsDef.add(name + "PreProcessJar", createFunction(side.getPreProcessJar()));
            }
            
            JsonArray libraries = new JsonArray();
            side.getAdditionalCompileDependencies().get().forEach(libraries::add);
            side.getAdditionalRuntimeDependencies().get().forEach(libraries::add);
            librariesRoot.add(name, libraries);
            
            boolean client = name.equals("client");
            boolean joined = name.equals("joined");
            boolean server = name.equals("server");
            
            // Build steps definitions
            var steps = new JsonArray();
            steps.add(createStep("downloadManifest"));
            steps.add(createStep("downloadJson", Map.of("json", "{downloadManifestOutput}")));
            if (client || joined) {
                steps.add(createStep("downloadClient", Map.of("json", "{downloadJsonOutput}")));
            }
            if (server || joined) {
                steps.add(createStep("downloadServer", Map.of("json", "{downloadJsonOutput}")));
            }
            steps.add(createStep("listLibraries", Map.of("json", "{downloadJsonOutput}")));
            String decompileInput;
            if (side.getPreProcessJar() != null) {
                Map<String, Object> preProcessJarInputs = new HashMap<>();
                if (client || joined) {
                    preProcessJarInputs.put("inputClientJar", "{downloadClientOutput}");
                }
                if (server || joined) {
                    preProcessJarInputs.put("inputServerJar", "{downloadServerOutput}");
                }
                steps.add(createStep(name + "PreProcessJar", preProcessJarInputs));
                decompileInput = "{" + name + "PreProcessJarOutput}";
            } else if (client) {
                decompileInput = "{downloadClientOutput}";
            } else if (server) {
                decompileInput = "{downloadServerOutput}";
            } else {
                throw new GradleException("No preProcessJar set for " + name);
            }
            steps.add(createStep("decompile", Map.of(
                    "inputLibraries", "{listLibrariesOutput}",
                    "input", decompileInput
            )));
            steps.add(createStep("patch", Map.of("input", "{decompileOutput}")));
            stepsRoot.add(name, steps);

            patches.addProperty(name, "patches/" + name);
        }

        // Build final JSON
        var root = new JsonObject();
        root.addProperty("spec", 6);
        root.addProperty("version", getMinecraftVersion().get());
        root.addProperty("java_target", getJavaVersion().get());
        root.addProperty("encoding", getEncoding().get());

        var data = new JsonObject();
        data.add("patches", patches);
        root.add("data", data);

        root.add("steps", stepsRoot);
        root.add("functions", functionsDef);
        root.add("libraries", librariesRoot);

        // Write to file
        try (var writer = Files.newBufferedWriter(getOutput().get().getAsFile().toPath())) {
            GSON.toJson(root, writer);
        }
    }

    private static JsonElement createStep(String type) {
        return createStep(type, Map.of());
    }

    private static JsonElement createStep(String type, Map<String, Object> properties) {
        var step = new JsonObject();
        step.addProperty("type", type);
        for (var entry : properties.entrySet()) {
            step.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
        }
        return step;
    }

    private static JsonElement createFunction(ToolSettings settings) {
        var function = new JsonObject();
        var classpath = new JsonArray();
        for (String classpathItem : settings.getClasspath().get()) {
            classpath.add(classpathItem);
        }
        function.add("classpath", classpath);
        if (settings.getMainClass().isPresent()) {
            function.addProperty("main_class", settings.getMainClass().get());
        }
        var args = new JsonArray();
        for (String entry : settings.getArgs().get()) {
            args.add(entry);
        }
        function.add("args", args);
        var jvmArgs = new JsonArray();
        for (String entry : settings.getJvmArgs().get()) {
            jvmArgs.add(entry);
        }
        function.add("jvmargs", jvmArgs);
        if (settings.getRepositoryUrl().isPresent()) {
            function.addProperty("repo", settings.getRepositoryUrl().get());
        }
        if (settings.getJavaVersion().isPresent()) {
            function.addProperty("java_version", settings.getJavaVersion().get());
        }
        return function;
    }

    public void addSide(String name, NeoFormSideExtension sideExtension) {
        Side side = getProject().getObjects().newInstance(Side.class);
        side.getAdditionalCompileDependencies().set(sideExtension.getAdditionalCompileDependencies());
        side.getAdditionalRuntimeDependencies().set(sideExtension.getAdditionalRuntimeDependencies());
        side.setPreProcessJar(sideExtension.getPreProcessJar());
        side.getUseClient().set(sideExtension.getUseClient());
        side.getUseServer().set(sideExtension.getUseServer());
        getSides().put(name, side);
    }

    public static abstract class Side {
        public abstract ListProperty<String> getAdditionalCompileDependencies();

        public abstract ListProperty<String> getAdditionalRuntimeDependencies();

        public abstract ToolSettings getPreProcessJar();

        public abstract void setPreProcessJar(ToolSettings preProcessJar);

        public abstract Property<Boolean> getUseClient();

        public abstract Property<Boolean> getUseServer();
    }
}
