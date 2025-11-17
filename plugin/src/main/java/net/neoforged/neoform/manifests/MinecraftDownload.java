package net.neoforged.neoform.manifests;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

public record MinecraftDownload(@SerializedName("sha1") String checksum, int size,
                                @SerializedName("url") URI uri, @Nullable String path) {
}
