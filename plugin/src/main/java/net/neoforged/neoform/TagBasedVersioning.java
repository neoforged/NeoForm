package net.neoforged.neoform;

import joptsimple.internal.Strings;
import net.neoforged.neoform.tasks.GetVersion;
import net.neoforged.neoform.tasks.TagRelease;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Configures the tag based versioning scheme on the project.
 * It uses Git tags to produce monotonically increasing release-numbers per Minecraft version we're releasing,
 * while allowing for differentiating between -SNAPSHOT and normal releases.
 */
final class TagBasedVersioning {
    private TagBasedVersioning() {
    }

    private record ReleaseRevision(String commitId, int revision) {
    }

    // Parses lines of the form "<commit-id> refs/tags/v<minecraft>-<release>[^{}]" and skips all others
    private static List<ReleaseRevision> parseRevList(String gitOutput, String minecraftVersion) {
        // A release tag is: v<minecraft-version>-<release-number>, where release number has no leading zeros.
        // The "^{}" at the end of line denotes the dereferenced annotated commit object. We're just including both
        // the tag objet ID and the commit ID it points to, since it doesn't matter.
        var result = new ArrayList<ReleaseRevision>();
        var releaseTagLine = Pattern.compile("^\\s*([0-9a-f]+)\\s+refs/tags/v" + Pattern.quote(minecraftVersion + "-") + "([1-9]\\d*)(?:" + Pattern.quote("^{}") + ")?\\s*$");

        for (String line : gitOutput.split("\n")) {
            var m = releaseTagLine.matcher(line);
            if (m.matches()) {
                result.add(new ReleaseRevision(m.group(1), Integer.parseUnsignedInt(m.group(2))));
            }
        }

        return result;
    }

    public static void configureVersion(Project project, String minecraftVersion) {
        var release = project.getProviders().gradleProperty("neoform_release").orElse("false").map(Boolean::parseBoolean);

        // Get the current HEAD commit SHA
        var currentCommit = runGit(project, "git", "rev-parse", "HEAD");

        // List all tags matching the pattern for the Minecraft version remotely
        // Example output:
        // f2ba5b04b8accc92453ffee37644886f56b9ca1b        refs/tags/v1.21.11-pre2_unobfuscated-1
        // 2132246adfcef540620d9a5944fb33ad99d3b896        refs/tags/v1.21.11-pre2_unobfuscated-2
        // f2ba5b04b8accc92453ffee37644886f56b9ca1b        refs/tags/v1.21.11-pre2_unobfuscated-2^{}
        var remoteReleases = runGit(project, "git", "ls-remote", "-t", "origin", "v" + minecraftVersion + "-*")
                .map(output -> parseRevList(output, minecraftVersion));
        // List all tags matching the pattern for the Minecraft version locally. Note this is NOT filtered by Minecraft version
        // Example output:
        // f2ba5b04b8accc92453ffee37644886f56b9ca1b refs/tags/v1.21.11-pre2_unobfuscated-1
        // 2132246adfcef540620d9a5944fb33ad99d3b896 refs/tags/v1.21.11-pre2_unobfuscated-2
        // f2ba5b04b8accc92453ffee37644886f56b9ca1b refs/tags/v1.21.11-pre2_unobfuscated-2^{}
        // The rather complicated looking format is used to show the *target commit* rather than the tags own SHA1 for annotated tags.
        var localReleases = runGit(project, "git", "for-each-ref", "refs/tags", "--format=%(if)%(*objectname)%(then)%(*objectname)%(else)%(objectname)%(end) %(refname)")
                .map(output -> parseRevList(output, minecraftVersion));

        // Joined releases
        var allReleases = remoteReleases.zip(localReleases, (a, b) -> Stream.concat(a.stream(), b.stream()).toList());

        var logger = project.getLogger();
        var versionFromTag = project.getObjects().property(String.class);
        versionFromTag.set(project.getProviders().zip(
                allReleases, currentCommit,
                (allReleaseList, commit) -> {
                    // The highest release for this Minecraft version overall
                    int highestReleaseOverall = allReleaseList.stream().mapToInt(ReleaseRevision::revision).max().orElse(0);
                    int highestReleaseOnThisCommit = allReleaseList.stream().filter(r -> r.commitId.equals(commit)).mapToInt(ReleaseRevision::revision).max().orElse(0);

                    if (highestReleaseOnThisCommit > 0) {
                        logger.lifecycle("HEAD is already tagged as release {} for Minecraft {}", highestReleaseOnThisCommit, minecraftVersion);
                        return minecraftVersion + "-" + highestReleaseOnThisCommit;
                    } else if (highestReleaseOverall > 0) {
                        logger.lifecycle("Latest release for Minecraft {} was {}", minecraftVersion, highestReleaseOverall);
                        return minecraftVersion + "-" + (highestReleaseOverall + 1);
                    } else {
                        logger.lifecycle("This is the first release for Minecraft {}", minecraftVersion);
                        return minecraftVersion + "-" + (highestReleaseOverall + 1);
                    }
                }
        ));
        versionFromTag.finalizeValueOnRead();

        class VersionSource {
            @Override
            public String toString() {
                if (!release.get()) {
                    return minecraftVersion + "-SNAPSHOT";
                } else {
                    return versionFromTag.get();
                }
            }
        }

        project.setVersion(new VersionSource());

        var tasks = project.getTasks();
        tasks.register("getVersion", GetVersion.class);
        tasks.register("tagRelease", TagRelease.class);
    }

    private static Provider<String> runGit(Project project, String... args) {
        var execOutput = project.getProviders().exec(spec -> {
            spec.commandLine((Object[]) args);
            spec.setWorkingDir(project.getRootDir());
            spec.setIgnoreExitValue(true); // we have to do manual error checking, otherwise we do not get reporting on the STDERR/STDOUT
        });

        return execOutput.getResult()
                .zip(execOutput.getStandardOutput().getAsText(), Map::entry)
                .zip(execOutput.getStandardError().getAsText(), (combined, stderr) -> {
                    int exitCode = combined.getKey().getExitValue();
                    String stdout = combined.getValue();

                    if (exitCode != 0) {
                        var error = new StringBuilder(Strings.join(args, " ") + " failed with exit code: ").append(exitCode).append("\n");
                        if (!stdout.isEmpty()) {
                            error.append("\nSTDOUT:\n").append(stdout).append("\n");
                        }
                        if (!stderr.isEmpty()) {
                            error.append("\nSTDERR:\n").append(stderr).append("\n");
                        }
                        throw new GradleException(error.toString());
                    }

                    return stdout.trim();
                });
    }
}
