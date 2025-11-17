package net.neoforged.neoform;

import net.neoforged.moddevgradle.boot.RepositoriesPlugin;
import net.neoforged.neoform.dsl.NeoFormExtension;
import net.neoforged.neoform.dsl.ToolSettings;
import net.neoforged.nfrtgradle.NeoFormRuntimeExtension;
import net.neoforged.nfrtgradle.NeoFormRuntimePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.DependencyResolutionManagement;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.toolchains.foojay.FoojayToolchainsConventionPlugin;

import java.io.File;
import java.net.URI;

public class NeoFormSettingsPlugin implements Plugin<Settings> {
    @Override
    public void apply(Settings settings) {
        // We require access to various versions of JREs that are *not* the JRE hosting the build
        settings.getPlugins().apply(FoojayToolchainsConventionPlugin.class);
        settings.getPlugins().apply(RepositoriesPlugin.class);

        settings.getRootProject().setName("neoform");

        var neoForm = settings.getExtensions().create("neoForm", NeoFormExtension.class);
        settings.getGradle().getExtensions().add("neoForm", neoForm);

        settings.dependencyResolutionManagement(spec -> {
            spec.getRepositoriesMode().set(RepositoriesMode.FAIL_ON_PROJECT_REPOS);
            spec.repositories(repositories -> {
                // Automatically include repositories for the tools
                repositories.maven(repo -> {
                    repo.setName("Neoforge");
                    repo.setUrl("https://maven.neoforged.net/releases/");
                    repo.metadataSources(sources -> sources.gradleMetadata());
                    repo.content(content -> {
                        content.includeGroupAndSubgroups("net.neoforged");
                    });
                });
                repositories.maven(repo -> {
                    repo.setName("Mojang Meta");
                    repo.setUrl("https://maven.neoforged.net/mojang-meta/");
                    repo.metadataSources(sources -> sources.gradleMetadata());
                    repo.content(content -> {
                        content.includeModule("net.neoforged", "minecraft-dependencies");
                    });
                });
                repositories.mavenCentral();
                repositories.maven(repo -> {
                    repo.setName("Mojang Minecraft Libraries");
                    repo.setUrl(URI.create("https://libraries.minecraft.net/"));
                    repo.metadataSources(sources -> sources.mavenPom());
                });
//                repositories.maven(repo -> {
//                    repo.setName("Maven for PR #91"); // https://github.com/neoforged/NeoFormRuntime/pull/91
//                    repo.setUrl(URI.create("https://prmaven.neoforged.net/NeoFormRuntime/pr91"));
//                    repo.content(content -> {
//                        content.includeModule("net.neoforged", "neoform-runtime");
//                    });
//                });
            });
        });

        // Add tool repositories once the settings have been evaluated
        settings.getGradle().settingsEvaluated(ignored -> {
            addToolRepository(settings.getDependencyResolutionManagement(), neoForm.getPreProcessJar());
            addToolRepository(settings.getDependencyResolutionManagement(), neoForm.getDecompiler());
        });

        var workspaceDir = new File(settings.getRootDir(), "workspace");
        if (workspaceDir.isDirectory()) {
            settings.include("workspace");
        }

        settings.getGradle().getLifecycle().beforeProject(project -> {
            configureNFRT(project);

            if (project.getPath().equals(":workspace")) {
                project.getPlugins().apply(NeoFormWorkspacePlugin.class);
            } else {
                project.getPlugins().apply(NeoFormProjectPlugin.class);
            }
        });
    }

    private static void configureNFRT(Project project) {
        var neoForm = NeoFormExtension.fromProject(project);
        project.getPlugins().apply(NeoFormRuntimePlugin.class);
        var neoFormRuntime = project.getExtensions().getByType(NeoFormRuntimeExtension.class);
        neoFormRuntime.getVersion().set(neoForm.getNeoFormRuntimeVersion());
        neoFormRuntime.getLauncherManifestUrl().set(neoForm.getMinecraftLauncherManifestUrl());
    }

    private static void addToolRepository(DependencyResolutionManagement resolutionManagement, ToolSettings toolSettings) {
        var repositoryUrl = toolSettings.getRepositoryUrl().getOrNull();
        if (repositoryUrl == null) {
            return;
        }

        var repositories = resolutionManagement.getRepositories();
        if (repositories.stream().anyMatch(r -> {
            return r instanceof MavenArtifactRepository mavenRepo && mavenRepo.getUrl().equals(URI.create(repositoryUrl));
        })) {
            return; // already present
        }
        var repo = repositories.maven(maven -> {
            maven.setName("Tool Repository " + repositoryUrl);
            maven.setUrl(repositoryUrl);
        });
        repositories.remove(repo);
        repositories.addFirst(repo);
    }

}
