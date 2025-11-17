package net.neoforged.neoform.manifests;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record AssetIndex(Map<String, AssetObject> objects) {
    public static AssetIndex from(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            return new GsonBuilder()
                    .create()
                    .fromJson(reader, AssetIndex.class);
        }
    }
}
