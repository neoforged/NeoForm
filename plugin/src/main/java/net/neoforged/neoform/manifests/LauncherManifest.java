package net.neoforged.neoform.manifests;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record LauncherManifest(@SerializedName("latest") Map<String, String> latestByType, List<Version> versions) {
    public static LauncherManifest from(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            return from(reader);
        }
    }

    public Version findById(String id) {
        for (Version version : versions) {
            if (id.equals(version.id)) {
                return version;
            }
        }
        return null;
    }

    public static LauncherManifest from(Reader reader) throws IOException {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantJsonDeserializer())
                .create()
                .fromJson(reader, LauncherManifest.class);
    }

    public record Version(String id, String type, URI url, String sha1, Instant time, Instant releaseTime) {
    }

    private static class InstantJsonDeserializer implements JsonDeserializer<Instant> {
        @Override
        public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return Instant.parse(json.getAsString());
        }
    }
}
