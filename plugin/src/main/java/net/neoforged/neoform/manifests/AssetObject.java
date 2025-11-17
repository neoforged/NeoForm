package net.neoforged.neoform.manifests;

public record AssetObject(String hash, int size) {
    public String getRelativePath() {
        return hash.substring(0, 2) + "/" + hash;
    }
}
