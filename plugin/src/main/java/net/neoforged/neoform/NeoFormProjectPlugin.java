package net.neoforged.neoform;

import net.neoforged.gradleutils.GradleUtilsExtension;
import net.neoforged.gradleutils.PomUtilsExtension;
import net.neoforged.neoform.dsl.NeoFormExtension;
import net.neoforged.neoform.tasks.CreateConfig;
import net.neoforged.neoform.tasks.CreatePatchWorkspace;
import net.neoforged.neoform.tasks.CreatePatches;
import net.neoforged.neoform.tasks.Decompile;
import net.neoforged.neoform.tasks.DownloadVersionArtifact;
import net.neoforged.neoform.tasks.DownloadVersionManifest;
import net.neoforged.neoform.tasks.PrepareJarForDecompiler;
import net.neoforged.neoform.tasks.TestNeoFormData;
import net.neoforged.neoform.tasks.ToolAction;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.bundling.Zip;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class NeoFormProjectPlugin implements Plugin<Project> {
    public void apply(Project project) {
        if (project.getRootProject() != project) {
            throw new InvalidUserCodeException("This plugin should only be applied to the root project.");
        }

        var neoForm = NeoFormExtension.fromProject(project);
        var tasks = project.getTasks();
        var buildDir = project.getLayout().getBuildDirectory();
        var inputsDir = buildDir.dir("neoform/inputs");
        var minecraftVersion = neoForm.getMinecraftVersion();

        var timestamp = DateTimeFormatter.ofPattern("uuuuMMdd.HHmmss").format(OffsetDateTime.now(ZoneId.of("UTC")));
        project.setGroup("net.neoforged");
        project.setVersion(minecraftVersion.get() + "-" + timestamp);

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Download the Version Manifest
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var downloadManifest = tasks.register("downloadVersionManifest", DownloadVersionManifest.class, task -> {
            task.getMinecraftVersion().set(minecraftVersion);
            task.getLauncherManifestUrl().set(neoForm.getMinecraftLauncherManifestUrl());
            task.getOutput().set(prefixFilenameWithVersion(neoForm, inputsDir, "version.json"));
        });
        var versionManifest = downloadManifest.flatMap(DownloadVersionManifest::getOutput);

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Download Artifacts from Version Manifest
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var downloadClient = tasks.register("downloadClient", DownloadVersionArtifact.class, task -> {
            task.getVersionManifest().set(versionManifest);
            task.getArtifactName().set("client");
            task.getOutput().set(prefixFilenameWithVersion(neoForm, inputsDir, "client.jar"));
        });
        var downloadServer = tasks.register("downloadServer", DownloadVersionArtifact.class, task -> {
            task.getVersionManifest().set(versionManifest);
            task.getArtifactName().set("server");
            task.getOutput().set(prefixFilenameWithVersion(neoForm, inputsDir, "server.jar"));
        });

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Merge the client and server to get the input for the decompiler
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var prepareJarForDecompiler = tasks.register("prepareJarForDecompiler", PrepareJarForDecompiler.class, task -> {
            task.setGroup("neoform/internal");
            task.getClient().set(downloadClient.flatMap(DownloadVersionArtifact::getOutput));
            task.getServer().set(downloadServer.flatMap(DownloadVersionArtifact::getOutput));
            task.getOutput().set(prefixFilenameWithVersion(neoForm, inputsDir, "joined.jar"));
        });
        ToolAction.configure(project, prepareJarForDecompiler, neoForm.getPreProcessJar());

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Decompile
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var minecraftLibrariesClasspath = MinecraftLibraries.createConfiguration(project);
        var decompile = tasks.register("decompile", Decompile.class, task -> {
            task.setGroup("neoform/internal");
            task.getInput().set(prepareJarForDecompiler.flatMap(PrepareJarForDecompiler::getOutput));
            task.getInputClasspath().from(minecraftLibrariesClasspath);
            task.getOutput().set(prefixFilenameWithVersion(neoForm, inputsDir, "sources.zip"));
        });
        ToolAction.configure(project, decompile, neoForm.getDecompiler());

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Workflow Tasks
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var createPatchWorkspace = tasks.register("createPatchWorkspace", CreatePatchWorkspace.class, task -> {
            task.setGroup("neoform");
            task.getPatchesDir().set(project.getLayout().getProjectDirectory().dir("src/patches"));
            task.getSourcesZip().set(decompile.flatMap(Decompile::getOutput));
            task.getWorkspace().set(project.getLayout().getProjectDirectory().dir("workspace"));
        });
        var createPatchWorkspaceForUpdate = tasks.register("createPatchWorkspaceForUpdate", CreatePatchWorkspace.class, task -> {
            task.setGroup("neoform");
            task.getPatchesDir().set(project.getLayout().getProjectDirectory().dir("src/patches"));
            task.getSourcesZip().set(decompile.flatMap(Decompile::getOutput));
            task.getWorkspace().set(project.getLayout().getProjectDirectory().dir("workspace"));
            task.getUpdateMode().set(true);
        });
        var createPatches = tasks.register("createPatches", CreatePatches.class, task -> {
            task.setGroup("neoform");
            task.getPatchesDir().set(project.getLayout().getProjectDirectory().dir("src/patches"));
            task.getSourcesZip().set(decompile.flatMap(Decompile::getOutput));
            task.getModifiedSources().set(project.getLayout().getProjectDirectory().dir("workspace/src/main/java"));
        });
        var createConfig = tasks.register("createConfig", CreateConfig.class, task -> {
            task.setGroup("neoform");
            task.setDescription("Creates the NeoForm config JSON for use by tooling from the configuration in the current project");
            task.getOutput().set(project.getLayout().getBuildDirectory().file("neoform/config.json"));
            task.getMinecraftVersion().set(neoForm.getMinecraftVersion());
            task.getDecompiler().set(neoForm.getDecompiler());
            task.getPreProcessJar().set(neoForm.getPreProcessJar());
            task.getAdditionalCompileDependencies().set(neoForm.getAdditionalCompileDependencies());
            task.getAdditionalRuntimeDependencies().set(neoForm.getAdditionalRuntimeDependencies());
            task.getEncoding().set("UTF-8");
            task.getJavaVersion().set(neoForm.getJavaVersion());
        });
        var createDataZip = tasks.register("createDataArchive", Zip.class, task -> {
            task.setGroup("neoform");
            task.setDescription("Builds the data archive containing NeoForm patches and configuration");
            task.from(createConfig, spec -> spec.into("/").rename(".*", "config.json"));
            task.from(project.files("src/patches"), spec -> spec.into("/patches"));
            task.getArchiveBaseName().set("neoform");
            task.getArchiveAppendix().set(project.provider(() -> project.getVersion().toString()));
            task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("libs"));
        });

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Testing Tasks
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var check = tasks.register("check", task -> task.setGroup("verification"));
        var testData = tasks.register("testData", TestNeoFormData.class, task -> {
            task.setGroup("verification");
            task.getNeoFormDataArchive().set(createDataZip.flatMap(Zip::getArchiveFile));
            task.getResultsDirectory().set(project.getLayout().getBuildDirectory().dir("test-results"));
        });
        check.configure(task -> task.dependsOn(testData));

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Publishing Tasks
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        project.getPlugins().apply(MavenPublishPlugin.class);
        project.getPlugins().apply("net.neoforged.gradleutils");
        var gradleUtilsExtension = project.getExtensions().getByType(GradleUtilsExtension.class);
        var publishing = project.getExtensions().getByType(PublishingExtension.class);
        // publishing.getRepositories().maven(gradleUtilsExtension.getPublishingMaven());
        publishing.getRepositories().maven(maven -> {
            maven.setUrl(project.getRootDir().getAbsoluteFile().toURI());
        });

        // Set common POM properties for all published artifacts
        publishing.getPublications().register("maven", MavenPublication.class, publication -> {
            publication.artifact(createDataZip);
            publication.pom(spec -> {
               spec.getName().set("CLI Utils");
               spec.getDescription().set("CLI Utilities for NeoForge's projects");
            });
        });
        publishing.getPublications().withType(MavenPublication.class).configureEach(it -> {
            var pomUtils = project.getExtensions().getByType(PomUtilsExtension.class);
            it.pom(pom -> {
                pomUtils.githubRepo(pom, "NeoForm");
                pomUtils.license(pom, PomUtilsExtension.License.LGPL_v2);
                pomUtils.neoForgedDeveloper(pom);
            });
        });
    }

    static Provider<RegularFile> prefixFilenameWithVersion(NeoFormExtension neoForm, Provider<Directory> dirProvider, String suffix) {
        return dirProvider.zip(neoForm.getMinecraftVersion(), (dir, version) -> dir.file(version + "_" + suffix));
    }
}
