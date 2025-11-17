package net.neoforged.neoform.manifests;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Models the Maven coordinates for an artifact.
 */
public record MavenCoordinate(String groupId, String artifactId, String extension, String classifier, String version) {
    public static final TypeAdapter<MavenCoordinate> TYPE_ADAPTER = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, MavenCoordinate value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public MavenCoordinate read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return MavenCoordinate.parse(in.nextString());
        }
    };

    public MavenCoordinate {
        Objects.requireNonNull(groupId);
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(version);
        // Normalize the extension ("jar" is default according to Maven)
        if (extension == null || "jar".equals(extension)) {
            extension = "";
        }
        if (classifier == null) {
            classifier = "";
        }
    }

    /**
     * Valid forms:
     * <ul>
     * <li>{@code groupId:artifactId:version}</li>
     * <li>{@code groupId:artifactId:version:classifier}</li>
     * <li>{@code groupId:artifactId:version:classifier@extension}</li>
     * <li>{@code groupId:artifactId:version@extension}</li>
     * </ul>
     */
    public static MavenCoordinate parse(String coordinate) {
        var coordinateAndExt = coordinate.split("@");
        String extension = "";
        if (coordinateAndExt.length > 2) {
            throw new IllegalArgumentException("Malformed Maven coordinate: " + coordinate);
        } else if (coordinateAndExt.length == 2) {
            extension = coordinateAndExt[1];
            coordinate = coordinateAndExt[0];
        }

        var parts = coordinate.split(":");
        if (parts.length != 3 && parts.length != 4) {
            throw new IllegalArgumentException("Malformed Maven coordinate: " + coordinate);
        }

        var groupId = parts[0];
        var artifactId = parts[1];
        var version = parts[2];
        var classifier = parts.length == 4 ? parts[3] : "";
        return new MavenCoordinate(groupId, artifactId, extension, classifier, version);
    }

    /**
     * Constructs a path relative to the root of a Maven repository pointing to the artifact expressed through
     * these coordinates.
     */
    public Path toRelativeRepositoryPath() {
        final String fileName = artifactId + "-" + version +
                                (!classifier.isEmpty() ? "-" + classifier : "") +
                                (!extension.isEmpty() ? "." + extension : ".jar");

        String[] groups = groupId.split("\\.");
        Path result = Paths.get(groups[0]);
        for (int i = 1; i < groups.length; i++) {
            result = result.resolve(groups[i]);
        }

        return result.resolve(artifactId).resolve(version).resolve(fileName);
    }

    @Override
    public String toString() {
        var result = new StringBuilder();
        result.append(groupId).append(":").append(artifactId).append(":").append(version);
        if (!classifier.isEmpty()) {
            result.append(":").append(classifier);
        }
        if (!extension.isEmpty()) {
            result.append("@").append(extension);
        }
        return result.toString();
    }

    public URI toRepositoryUri(URI baseUri) {
        var originalBaseUri = baseUri.toString();
        var relativePath = toRelativeRepositoryPath().toString().replace('\\', '/');
        if (originalBaseUri.endsWith("/")) {
            return URI.create(originalBaseUri + relativePath);
        } else {
            return URI.create(originalBaseUri + "/" + relativePath);
        }
    }

    public MavenCoordinate withClassifier(String classifier) {
        return new MavenCoordinate(
                groupId,
                artifactId,
                extension,
                classifier,
                version
        );
    }

    public MavenCoordinate withVersion(String version) {
        return new MavenCoordinate(
                groupId,
                artifactId,
                extension,
                classifier,
                version
        );
    }

    /**
     * Returns {@code true} if this and the given coordinate have the same
     * {@code groupId}, {@code artifactId}, {@code classifier}, and {@code extension}, ignoring {@code version}.
     */
    public boolean equalsWithoutVersion(MavenCoordinate other) {
        return Objects.equals(groupId, other.groupId)
                && Objects.equals(artifactId, other.artifactId)
                && Objects.equals(classifier, other.classifier)
                && Objects.equals(extension, other.extension);
    }
}
