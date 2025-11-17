package net.neoforged.neoform.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.neoform.dsl.ToolSettings;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
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
    public abstract Property<ToolSettings> getPreProcessJar();

    @Input
    public abstract ListProperty<String> getAdditionalCompileDependencies();

    @Input
    public abstract ListProperty<String> getAdditionalRuntimeDependencies();

    @TaskAction
    public void createConfig() throws IOException {
        // Build steps definitions
        var steps = new JsonArray();
        steps.add(createStep("downloadManifest"));
        steps.add(createStep("downloadJson", Map.of("json", "{downloadManifestOutput}")));
        steps.add(createStep("downloadClient", Map.of("json", "{downloadJsonOutput}")));
        steps.add(createStep("downloadServer", Map.of("json", "{downloadJsonOutput}")));
        steps.add(createStep("listLibraries", Map.of("json", "{downloadJsonOutput}")));
        steps.add(createStep("preProcessJar", Map.of(
                "inputClient", "{downloadClientOutput}",
                "inputServer", "{downloadServerOutput}"
        )));
        steps.add(createStep("decompile", Map.of(
                "libraries", "{listLibrariesOutput}",
                "input", "{preProcessJarOutput}"
        )));
        steps.add(createStep("patch", Map.of("input", "{decompileOutput}")));

        // Build functions definitions
        var functionsDef = new JsonObject();
        functionsDef.add("preProcessJar", createFunction(getPreProcessJar().get()));
        functionsDef.add("decompile", createFunction(getDecompiler().get()));

        // Build final JSON
        var root = new JsonObject();
        root.addProperty("spec", 5);
        root.addProperty("version", getMinecraftVersion().get());
        root.addProperty("java_target", getJavaVersion().get());
        root.addProperty("encoding", getEncoding().get());

        var data = new JsonObject();
        data.addProperty("patches", "patches/");
        root.add("data", data);

        root.add("steps", wrapJoined(steps));
        root.add("functions", functionsDef);

        var libraries = new JsonArray();
        getAdditionalCompileDependencies().get().forEach(libraries::add);
        getAdditionalRuntimeDependencies().get().forEach(libraries::add);
        root.add("libraries", wrapJoined(libraries));

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
        function.addProperty("version", settings.getVersion().get());
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

    private static JsonElement wrapJoined(JsonElement element) {
        var wrapper = new JsonObject();
        wrapper.add("joined", element);
        return wrapper;
    }
}
