package net.neoforged.neoform.tasks;

import net.neoforged.neoform.dsl.NeoFormExtension;
import net.neoforged.neoform.manifests.LauncherManifest;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;

@DisableCachingByDefault(because = "it checks for updates")
public abstract class CheckForMinecraftUpdate extends DefaultTask {
    private final String currentVersion;
    private final String manifestUrl;

    public CheckForMinecraftUpdate() {
        setGroup("neoform/internal");
        setDescription("Checks if there's a newer Minecraft version to update to, and outputs the results of that check in a form usable by the GitHub workflows.");
        getOutputs().upToDateWhen(task -> false); // Since we check for updates remotely, we're never up to date

        var neoForm = NeoFormExtension.fromProject(getProject());
        currentVersion = neoForm.getMinecraftVersion().get();
        manifestUrl = neoForm.getMinecraftLauncherManifestUrl().getOrElse("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    }

    @TaskAction
    public void checkForUpdate() throws Exception {

        getLogger().lifecycle("Downloading launcher manifest from {}", manifestUrl);
        var launcherManifest = getLauncherManifest();
        var latestVersion = findLatestVersion(launcherManifest);
        if (latestVersion == null) {
            throw new GradleException("No latest version could be found at " + manifestUrl);
        }

        // Detect if it is one of the detected mainline versions, if not, declare the use of a separate branch.
        // This detection is disabled when we're not on the default branch.
        var outputs = new HashMap<>();
        outputs.put("release_type", getReleaseType(latestVersion));
        outputs.put("latest_version", latestVersion.id());
        outputs.put("current_version", currentVersion);

        getLogger().lifecycle("Result of update check:");
        for (var entry : outputs.entrySet()) {
            getLogger().lifecycle("{}={}", entry.getKey(), entry.getValue());
        }

        // Make these properties easily available to the CI/CD workflow
        var githubOutput = System.getenv("GITHUB_OUTPUT");
        if (githubOutput != null) {
            var lines = outputs.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList();
            Files.write(Path.of(githubOutput), lines);
        }
    }

    private static String getReleaseType(LauncherManifest.Version latestVersion) {
        String type = latestVersion.type();
        if (type.equals("release")) {
            return type; // Release is unambiguous
        }

        // For anything else, we need to split "main-line snapshots" from any other special release like
        // experiments, unobfuscated previews or april fools snapshots.
        if (type.equals("snapshot")
                // Newer snapshtos and pre-releases follow this format:
                // 25.4-(snapshot|pre|rc)-()
                && !latestVersion.id().matches("^\\d+\\.\\d+(\\.\\d+)?-(snapshot|pre|rc)-\\d+$")
                // Older version format -pre and -rc versions are always considered mainline
                && !latestVersion.id().matches("^\\d+\\.\\d+(\\.\\d+)?(?:-(pre|rc)\\d+)?$")
                // Older version format snapshots following the year-week-formatting are also considered mainline
                && !latestVersion.id().matches("^\\d{2}w\\d{2}[a-z]$")) {
            return "special";
        }

        return type;
    }

    private LauncherManifest.Version findLatestVersion(LauncherManifest launcherManifest) {

        LauncherManifest.Version latest = null;

        for (var versionId : launcherManifest.latestByType().values()) {
            var version = launcherManifest.findById(versionId);
            if (version != null && (latest == null || version.releaseTime().isAfter(latest.releaseTime()))) {
                latest = version;
            }
        }

        if (latest == null) {
            latest = launcherManifest.versions().stream()
                    .max(Comparator.comparing(LauncherManifest.Version::releaseTime))
                    .orElse(null);
        }

        return latest;

    }

    private LauncherManifest getLauncherManifest() throws Exception {
        var url = URI.create(manifestUrl).toURL();
        var conn = (HttpURLConnection) url.openConnection();

        try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return LauncherManifest.from(reader);
        } finally {
            conn.disconnect();
        }
    }
}
