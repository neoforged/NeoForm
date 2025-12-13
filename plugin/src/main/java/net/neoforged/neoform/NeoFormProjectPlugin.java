package net.neoforged.neoform;

import net.neoforged.gradleutils.GradleUtilsExtension;
import net.neoforged.gradleutils.PomUtilsExtension;
import net.neoforged.neoform.dsl.NeoFormExtension;
import net.neoforged.neoform.dsl.ToolSettings;
import net.neoforged.neoform.tasks.CheckForMinecraftUpdate;
import net.neoforged.neoform.tasks.CreateConfig;
import net.neoforged.neoform.tasks.CreatePatchWorkspace;
import net.neoforged.neoform.tasks.CreatePatches;
import net.neoforged.neoform.tasks.Decompile;
import net.neoforged.neoform.tasks.DownloadVersionArtifact;
import net.neoforged.neoform.tasks.DownloadVersionManifest;
import net.neoforged.neoform.tasks.PrepareJarForDecompiler;
import net.neoforged.neoform.tasks.TestWithEclipseCompiler;
import net.neoforged.neoform.tasks.TestWithNeoFormRuntime;
import net.neoforged.neoform.tasks.ToolAction;
import net.neoforged.neoform.tasks.UpdateMinecraft;
import net.neoforged.neoform.tasks.UpdateTools;
import net.neoforged.nfrtgradle.CreateMinecraftArtifacts;
import net.neoforged.nfrtgradle.NeoFormRuntimeTask;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public abstract class NeoFormProjectPlugin implements Plugin<Project> {

    @Inject
    protected abstract JavaToolchainService getJavaToolchains();

    public void apply(Project project) {
        if (project.getRootProject() != project) {
            throw new InvalidUserCodeException("This plugin should only be applied to the root project.");
        }

        var objects = project.getObjects();
        var tasks = project.getTasks();
        var configurations = project.getConfigurations();
        var buildDir = project.getLayout().getBuildDirectory();

        var neoForm = NeoFormExtension.fromProject(project);
        var inputsDir = buildDir.dir("neoform/inputs");
        var minecraftVersion = neoForm.getMinecraftVersion();

        project.setGroup("net.neoforged");
        TagBasedVersioning.configureVersion(project, minecraftVersion.get());

        // Configuration that declares any additional compile-time libraries needed by NeoForm
        var dependencyFactory = project.getDependencyFactory();
        var neoFormLibraries = configurations.dependencyScope("neoFormLibraries", spec -> {
            spec.getDependencies().addAllLater(neoForm.getAdditionalCompileDependencies().map(depStrings -> {
                var deps = new ArrayList<Dependency>();
                for (var depString : depStrings) {
                    deps.add(dependencyFactory.create(depString));
                }
                return deps;
            }));
            spec.setTransitive(false);
        });
        var neoFormLibrariesClasspath = configurations.resolvable("neoFormLibrariesClasspath", spec -> {
            spec.extendsFrom(neoFormLibraries.get());
            spec.setTransitive(false);
            spec.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            spec.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            spec.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
        });
        var neoFormTools = configurations.dependencyScope("neoFormTools", spec -> {
            spec.setDescription("Tools that are declared in this projects NeoForm config file.");
            spec.getDependencies().addAll(createDependencies(project, neoForm.getPreProcessJar()));
            spec.getDependencies().addAll(createDependencies(project, neoForm.getDecompiler()));
        });

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
        var workspaceDir = project.getLayout().getProjectDirectory().dir("workspace");
        var cleanPatchWorkspace = tasks.register("cleanPatchWorkspace", Delete.class, task -> {
            task.setGroup("neoform");
            task.setDescription("Removes the current patch workspace, if it exists.");
            task.delete(workspaceDir);
        });
        var createPatchWorkspace = tasks.register("createPatchWorkspace", CreatePatchWorkspace.class, task -> {
            task.setGroup("neoform");
            task.setDescription("Creates a Gradle subproject under 'workspace' that contains the patched Minecraft sources to work on modifying the patches easily. Run the createPatches task to generate new patches from this workspace.");
            task.getPatchesDir().set(project.getLayout().getProjectDirectory().dir("src/patches"));
            task.getSourcesZip().set(decompile.flatMap(Decompile::getOutput));
            task.getWorkspace().set(workspaceDir);
        });
        var createPatchWorkspaceForUpdate = tasks.register("createPatchWorkspaceForUpdate", CreatePatchWorkspace.class, task -> {
            task.setGroup("neoform");
            task.setDescription("Same as the createWorkspace task, but patches are applied fuzzily and rejected patches will be stored in the 'rejects' folder.");
            task.getPatchesDir().set(project.getLayout().getProjectDirectory().dir("src/patches"));
            task.getSourcesZip().set(decompile.flatMap(Decompile::getOutput));
            task.getWorkspace().set(workspaceDir);
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
        var dataZip = createDataZip.flatMap(Zip::getArchiveFile);
        tasks.register("updateMinecraft", UpdateMinecraft.class);
        tasks.register("updateTools", UpdateTools.class);
        tasks.register("checkForMinecraftUpdate", CheckForMinecraftUpdate.class);

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Testing Tasks
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var neoFormRuntimeLibrariesClasspath = configurations.resolvable("neoFormRuntimeLibrariesClasspath", spec -> {
            spec.setDescription("Classpath used to pre-resolve tools and libraries for NFRT");
            spec.extendsFrom(neoFormTools.get());
            spec.extendsFrom(neoFormLibraries.get());
            spec.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.SHADOWED));
        });
        var check = tasks.register("check", task -> task.setGroup("verification"));

        Action<NeoFormRuntimeTask> nfrtConfigurer = task -> {
            task.addArtifactsToManifest(neoFormRuntimeLibrariesClasspath.get());
            task.addArtifactsToManifest(minecraftLibrariesClasspath.get());
        };

        // Explicitly test with a variety of different JDKs, this tests both running the decompile & recompile using that JDK.
        var javaVersions = Stream.concat(Stream.of(neoForm.getJavaVersion().get()), neoForm.getTestJavaVersions().get().stream()).toList();
        for (int testJavaVersion : javaVersions) {
            String taskName = testJavaVersion == neoForm.getJavaVersion().get() ? "testData" : "testDataJava" + testJavaVersion;

            var testData = tasks.register(taskName, TestWithNeoFormRuntime.class, task -> {
                task.setGroup("verification");
                task.setDescription("Tests that the data produced by this project can be consumed by NFRT to generate compilable sources and compile them.");
                nfrtConfigurer.execute(task);
                task.getNeoFormDataArchive().set(dataZip);
                task.getResultsDirectory().set(project.getLayout().getBuildDirectory().dir("test-results/" + taskName));
                task.getJavaExecutable().convention(getJavaToolchains()
                        .launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(neoForm.getJavaVersion().get())))
                        .map(javaLauncher -> javaLauncher.getExecutablePath().getAsFile().getAbsolutePath()));
            });
            check.configure(task -> task.dependsOn(testData));
        }
        configureEclipseTestTask(project, neoForm, dataZip, neoFormLibrariesClasspath.get(), minecraftLibrariesClasspath.get(), nfrtConfigurer);

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Publishing Tasks
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var neoformData = configurations.consumable("neoformData", configuration -> {
            configuration.getAttributes().attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, neoForm.getJavaVersion());
            configuration.getOutgoing().capability("net.neoforged:neoform:" + project.getVersion());
            configuration.extendsFrom(neoFormTools.get());
        });
        project.getArtifacts().add(neoformData.getName(), createDataZip);

        var neoformRuntimeElements = configurations.consumable("neoformRuntimeElements", configuration -> {
            configuration.attributes(attributes -> {
                attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, neoForm.getJavaVersion());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
            });
            configuration.getOutgoing().capability("net.neoforged:neoform-dependencies:" + project.getVersion());
            configuration.extendsFrom(configurations.named(MinecraftLibraries.DEPENDENCY_SCOPE).get());
            configuration.extendsFrom(neoFormLibraries.get());
        });
        var neoformApiElements = configurations.consumable("neoformApiElements", configuration -> {
            configuration.attributes(attributes -> {
                attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, neoForm.getJavaVersion());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_API));
            });
            configuration.getOutgoing().capability("net.neoforged:neoform-dependencies:" + project.getVersion());
            configuration.extendsFrom(configurations.named(MinecraftLibraries.DEPENDENCY_SCOPE).get());
            configuration.extendsFrom(neoFormLibraries.get());
        });

        var neoformComponent = getComponentFactory().adhoc("neoform");
        project.getComponents().add(neoformComponent);
        neoformComponent.addVariantsFromConfiguration(neoformData, variant -> {
        });
        neoformComponent.addVariantsFromConfiguration(neoformRuntimeElements, variant -> {
        });
        neoformComponent.addVariantsFromConfiguration(neoformApiElements, variant -> {
        });

        project.getPlugins().apply(MavenPublishPlugin.class);
        project.getPlugins().apply("net.neoforged.gradleutils");
        var gradleUtilsExtension = project.getExtensions().getByType(GradleUtilsExtension.class);
        var publishing = project.getExtensions().getByType(PublishingExtension.class);
        publishing.getRepositories().maven(gradleUtilsExtension.getPublishingMaven());

        // Set common POM properties for all published artifacts
        publishing.getPublications().register("maven", MavenPublication.class, publication -> {
            publication.from(neoformComponent);
            publication.pom(spec -> {
                spec.getName().set("NeoForm");
                spec.getDescription().set("Data and patches required to produce recompilable Minecraft source code");
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

    private void configureEclipseTestTask(Project project,
                                          NeoFormExtension neoForm,
                                          Provider<RegularFile> dataZip,
                                          Configuration additionalCompileDependencies,
                                          Configuration minecraftLibraries,
                                          Action<NeoFormRuntimeTask> nfrtConfigurer) {

        var eclipseCompiler = project.getConfigurations().dependencyScope("eclipseCompiler", spec -> {
            spec.getDependencies().add(project.getDependencyFactory().create("org.eclipse.jdt:ecj:3.43.0"));
        });
        var eclipseCompilerClasspath = project.getConfigurations().resolvable("eclipseCompilerClasspath", spec -> {
            spec.extendsFrom(eclipseCompiler.get());
        });

        var tasks = project.getTasks();

        // Use NFRT to produce a source zip we can then compile with ECJ
        var sourcesZip = tasks.register("createSourceZip", CreateMinecraftArtifacts.class, task -> {
            task.setGroup("neoform/internal");
            task.setDescription("Creates a patched sources zip with NFRT to test further compilers.");
            nfrtConfigurer.execute(task);
            task.getNeoFormArtifact().set(dataZip.map(f -> f.getAsFile().getAbsolutePath()));
            task.getSourcesArtifact().set(project.getLayout().getBuildDirectory().file("test-data/sources.zip"));
        });

        var testTask = tasks.register("testDataWithEclipseCompiler", TestWithEclipseCompiler.class, task -> {
            task.setGroup("verification");
            task.getSourcesZip().set(sourcesZip.flatMap(CreateMinecraftArtifacts::getSourcesArtifact));
            task.getEclipseCompilerClasspath().from(eclipseCompilerClasspath);
            task.getCompileClasspath().from(minecraftLibraries);
            task.getCompileClasspath().from(additionalCompileDependencies);
            task.getJavaVersion().set(neoForm.getJavaVersion());
            task.getJavaExecutable().convention(getJavaToolchains()
                    .launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(neoForm.getJavaVersion().get())))
                    .map(javaLauncher -> javaLauncher.getExecutablePath().getAsFile().getAbsolutePath()));
        });
        project.getTasks().named("check").configure(check -> check.dependsOn(testTask));
    }

    static Provider<RegularFile> prefixFilenameWithVersion(NeoFormExtension neoForm, Provider<Directory> dirProvider, String suffix) {
        return dirProvider.zip(neoForm.getMinecraftVersion(), (dir, version) -> dir.file(version + "_" + suffix));
    }

    @Inject
    protected abstract SoftwareComponentFactory getComponentFactory();

    private static List<? extends Dependency> createDependencies(Project project, ToolSettings toolSettings) {
        var dependencyFactory = project.getDependencyFactory();
        return List.of(
                dependencyFactory.create(toolSettings.getVersion().get())
        );
    }
}
