package net.neoforged.neoform.manifests;

import com.google.gson.annotations.SerializedName;

import java.net.URI;

public record AssetIndexReference(String id, @SerializedName("sha1") String checksum, int size, long totalSize,
                                  @SerializedName("url") URI uri) {
}
