package net.neoforged.neoform;

import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.moddevgradle.internal.NeoDevFacade;
import net.neoforged.neoform.dsl.NeoFormExtension;
import net.neoforged.neoform.tasks.DownloadVersionManifest;
import net.neoforged.neoform.tasks.GenerateRunClientClass;
import net.neoforged.nfrtgradle.DownloadAssets;
import net.neoforged.nfrtgradle.NeoFormRuntimeExtension;
import net.neoforged.nfrtgradle.NeoFormRuntimePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.ArrayList;
import java.util.Collections;

public class NeoFormWorkspacePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(NeoFormRuntimePlugin.class);
        project.getPlugins().apply(JavaPlugin.class);
        project.getPlugins().apply(MinecraftDependenciesPlugin.class);

        var layout = project.getLayout();
        var tasks = project.getTasks();
        var configurations = project.getConfigurations();
        var sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
        var neoForm = NeoFormExtension.fromProject(project);

        tasks.withType(JavaCompile.class).configureEach(task -> {
            Collections.addAll(task.getOptions().getCompilerArgs(), "-Xmaxerrs", "9999");
            // There are just too many, and we use a lot of raw casts to fix compile errors.
            task.getOptions().getCompilerArgs().addAll(neoForm.getJavaCompilerOptions().get());
        });

        var dependencyFactory = project.getDependencyFactory();
        configurations.named("implementation").configure(spec -> {
            var classpath = neoForm.getMinecraftDependencies();
            spec.getDependencies().addAllLater(classpath.map(notations -> {
                var result = new ArrayList<Dependency>(notations.size());
                for (var notation : notations) {
                    result.add(dependencyFactory.create(notation));
                }
                return result;
            }));
        });
        configurations.named("compileOnly").configure(spec -> {
            var classpath = neoForm.getAdditionalCompileDependencies();
            spec.getDependencies().addAllLater(classpath.map(notations -> {
                var result = new ArrayList<Dependency>(notations.size());
                for (var notation : notations) {
                    result.add(dependencyFactory.create(notation));
                }
                return result;
            }));
        });
        configurations.named("runtimeOnly").configure(spec -> {
            var classpath = neoForm.getAdditionalRuntimeDependencies();
            spec.getDependencies().addAllLater(classpath.map(notations -> {
                var result = new ArrayList<Dependency>(notations.size());
                for (var notation : notations) {
                    result.add(dependencyFactory.create(notation));
                }
                return result;
            }));
        });

        // Create the machinery to download assets and announce their availability
        // through a file on the classpath to the startup class.
        var mainSourceSet = sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME);
        var assetsResourceDir = layout.getBuildDirectory().dir("generated/sources/assets");
        var clientSourceSet = sourceSets.register("client", spec -> {
            // Add the generated dir that contains the asset properties
            spec.getResources().srcDir(assetsResourceDir);

            // Make main source set output available
            spec.setCompileClasspath(spec.getCompileClasspath().plus(mainSourceSet.get().getOutput()));
            spec.setRuntimeClasspath(spec.getRuntimeClasspath().plus(mainSourceSet.get().getOutput()));

            // Configure the runtime+compilation classpath
            configurations.named(spec.getRuntimeClasspathConfigurationName()).configure(configSpec -> {
                configSpec.extendsFrom(configurations.getByName(mainSourceSet.get().getRuntimeClasspathConfigurationName()));
            });
            configurations.named(spec.getCompileClasspathConfigurationName()).configure(configSpec -> {
                configSpec.extendsFrom(configurations.getByName(mainSourceSet.get().getCompileClasspathConfigurationName()));
            });
        });

        var downloadAssets = tasks.register("downloadAssets", DownloadAssets.class, task -> {
            task.setGroup("neoform/internal");
            task.setDescription("Download the client-side assets to be able to run the game.");
            task.getMinecraftVersion().set(neoForm.getMinecraftVersion());

            task.getAssetPropertiesFile().set(assetsResourceDir.map(dir -> dir.file("neoform_assets.properties")));
            task.getOutputs().dir(assetsResourceDir);
        });

        var downloadManifest = tasks.register("downloadVersionManifest", DownloadVersionManifest.class, task -> {
            task.setGroup("neoform/internal");
            task.getMinecraftVersion().set(neoForm.getMinecraftVersion());
            task.getLauncherManifestUrl().set(neoForm.getMinecraftLauncherManifestUrl());
            task.getOutput().set(project.getLayout().getBuildDirectory().file("minecraft_version.json"));
        });
        var versionManifest = downloadManifest.flatMap(DownloadVersionManifest::getOutput);

        var generateRunClientClass = tasks.register("generateRunClientClass", GenerateRunClientClass.class, task -> {
            task.setGroup("neoform/internal");
            task.setDescription("Generates the class to launch Minecraft.");
            task.getOutput().set(layout.getProjectDirectory().dir("src/client/java"));
            task.getAssetProperties().set(downloadAssets.flatMap(DownloadAssets::getAssetPropertiesFile));
            task.getVersionManifest().set(versionManifest);
        });
        NeoDevFacade.runTaskOnProjectSync(project, generateRunClientClass);

        tasks.register("runClient", JavaExec.class, task -> {
            task.setGroup("neoform");
            task.setDescription("Runs the client for testing.");
            task.getMainClass().set("StartClient");
            task.setClasspath(clientSourceSet.get().getRuntimeClasspath());
        });
    }
}
