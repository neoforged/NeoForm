package net.neoforged.neoform.manifests;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record MinecraftVersionManifest(String id, Map<String, MinecraftDownload> downloads,
                                       List<MinecraftLibrary> libraries,
                                       AssetIndexReference assetIndex, String assets, JavaVersionReference javaVersion,
                                       String mainClass, MinecraftArguments arguments) {
    public static MinecraftVersionManifest from(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            return new GsonBuilder()
                    .registerTypeAdapter(UnresolvedArgument.class, UnresolvedArgument.JSON_SERIALIZER)
                    .registerTypeAdapter(UnresolvedArgument.class, UnresolvedArgument.JSON_DESERIALIZER)
                    .create()
                    .fromJson(reader, MinecraftVersionManifest.class);
        }
    }
}
