package net.neoforged.neoform.manifests;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record LauncherManifest(List<Version> versions) {
    public static LauncherManifest from(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            return new Gson().fromJson(reader, LauncherManifest.class);
        }
    }

    public record Version(String id, String type, URI url, String sha1) {
    }
}
