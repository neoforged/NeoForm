package net.neoforged.neoform.manifests;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MinecraftLibrary(@SerializedName("name") String artifactId, Downloads downloads, List<Rule> rules,
                               @Nullable Map<OsType, String> natives) {
    public MinecraftLibrary {
        Objects.requireNonNull(artifactId, "name");
        rules = Objects.requireNonNullElseGet(rules, List::of);
    }

    public boolean rulesMatch() {
        return Rule.rulesMatch(rules);
    }

    @Nullable
    public MinecraftDownload getArtifactDownload() {
        if (downloads == null) {
            return null;
        }

        if (natives != null) {
            var classifier = natives.get(OsType.current());
            if (classifier != null) {
                var download = downloads.classifiers.get(classifier);
                if (download == null) {
                    throw new IllegalStateException("Download for " + artifactId + " references classifier " + classifier
                                                    + " for natives for OS " + OsType.current() + " but it doesn't exist.");
                }
                return download;
            }
        }

        return downloads.artifact;
    }

    public MavenCoordinate getMavenCoordinate() {
        var coordinate = MavenCoordinate.parse(artifactId);

        if (natives != null) {
            String classifier = natives.get(OsType.current());
            if (classifier != null) {
                coordinate = coordinate.withClassifier(classifier);
            }
        }

        return coordinate;
    }

    public record Downloads(MinecraftDownload artifact, Map<String, MinecraftDownload> classifiers) {
        public Downloads {
            if (classifiers == null) {
                classifiers = Map.of();
            }
        }
    }

    @Override
    public String toString() {
        return artifactId;
    }
}
