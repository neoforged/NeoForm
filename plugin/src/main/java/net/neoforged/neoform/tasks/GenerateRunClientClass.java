package net.neoforged.neoform.tasks;

import net.neoforged.neoform.manifests.MinecraftVersionManifest;
import net.neoforged.neoform.manifests.Rule;
import net.neoforged.neoform.manifests.UnresolvedArgument;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates the class used to start the client for testing purposes.
 */
public abstract class GenerateRunClientClass extends DefaultTask {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    @InputFile
    public abstract RegularFileProperty getVersionManifest();

    @InputFile
    public abstract RegularFileProperty getAssetProperties();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @TaskAction
    public void generate() throws Exception {
        // Collect the program and JVM arguments from the version manifest
        var versionManifest = MinecraftVersionManifest.from(getVersionManifest().getAsFile().get().toPath());

        var resolvedArgs = new ArrayList<String>();
        for (var argument : versionManifest.arguments().game()) {
            switch (argument) {
                case UnresolvedArgument.ConditionalValue conditional ->
                        resolvedArgs.addAll(resolveConditional(conditional));
                case UnresolvedArgument.Value value -> resolvedArgs.add(value.value());
            }
        }

        resolvedArgs.replaceAll(this::resolvePlaceholder);

        var destination = getOutput().getAsFile().get().toPath();

        String templateContent;
        try (var in = getClass().getResourceAsStream("/net/neoforged/neoform/templates/StartClient.java")) {
            templateContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }


    }

    private String resolvePlaceholder(String argument) {
        var matcher = PLACEHOLDER_PATTERN.matcher(argument);
        return matcher.replaceAll(matchResult -> {
            var placeholder = matchResult.group(1);
            return switch (placeholder) {
                case "game_directory" -> ".";
                case "assets_index_name" -> "0";
                case "clientid" -> "0";
                case "assets_root" -> "0";
                case "auth_uuid" -> "0";
                case "auth_xuid" -> "0";
                case "auth_access_token" -> "0";
                default -> {
                    getLogger().warn("Unknown placeholder in client launch arguments: {}", placeholder);
                    yield Matcher.quoteReplacement(matcher.group());
                }
            };
        });
    }

    private List<String> resolveConditional(UnresolvedArgument.ConditionalValue conditional) {
        if (!Rule.rulesMatch(conditional.rules())) {
            return List.of();
        }

        return conditional.value();
    }
}
