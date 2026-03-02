package net.neoforged.neoform;

import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.moddevgradle.internal.NeoDevFacade;
import net.neoforged.neoform.dsl.NeoFormExtension;
import net.neoforged.neoform.dsl.NeoFormSideExtension;
import net.neoforged.neoform.dsl.ToolSettings;
import net.neoforged.neoform.tasks.CreatePatchWorkspace;
import net.neoforged.neoform.tasks.CreatePatches;
import net.neoforged.neoform.tasks.Decompile;
import net.neoforged.neoform.tasks.DeduplicateSources;
import net.neoforged.neoform.tasks.DownloadVersionArtifacts;
import net.neoforged.neoform.tasks.GenerateRunClientClass;
import net.neoforged.neoform.tasks.PrepareJarForDecompiler;
import net.neoforged.neoform.tasks.TestWithEclipseCompiler;
import net.neoforged.neoform.tasks.TestWithNeoFormRuntime;
import net.neoforged.neoform.tasks.ToolAction;
import net.neoforged.nfrtgradle.DownloadAssets;
import net.neoforged.nfrtgradle.NeoFormRuntimeTask;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public abstract class NeoFormWorkspacePlugin implements Plugin<Project> {

    private TaskProvider<CreatePatches> createPatches;

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        project.getPlugins().apply(MinecraftDependenciesPlugin.class);
        var neoForm = NeoFormExtension.fromProject(project);
        NeoFormSideExtension side = project.getExtensions().create("neoForm", NeoFormSideExtension.class, neoForm);

        var layout = project.getLayout();
        var tasks = project.getTasks();
        var configurations = project.getConfigurations();
        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        var sourceSets = java.getSourceSets();
        NeoFormProjectPlugin rootPlugin = project.getRootProject().getPlugins().getPlugin(NeoFormProjectPlugin.class);
        String sideName = project.getName();
        Provider<Directory> inputsDir = project.getRootProject().getLayout().getBuildDirectory().dir("neoform/inputs/" + neoForm.getMinecraftVersion().get() + "/" + sideName);

        java.toolchain(spec -> {
            spec.getLanguageVersion().set(neoForm.getJavaVersion().map(JavaLanguageVersion::of));
        });

        tasks.withType(JavaCompile.class).configureEach(task -> {
            Collections.addAll(task.getOptions().getCompilerArgs(), "-Xmaxerrs", "9999");
            // There are just too many, and we use a lot of raw casts to fix compile errors.
            task.getOptions().getCompilerArgs().addAll(neoForm.getJavaCompilerOptions().get());
            task.getOptions().forkOptions(options -> options.setMemoryMaximumSize("2g"));
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
        var neoFormLibraries = configurations.dependencyScope("neoFormLibraries");
        var neoFormLibrariesClasspath = configurations.resolvable("neoFormLibrariesClasspath");
        var neoFormTools = configurations.dependencyScope("neoFormTools");
        project.afterEvaluate(tmp -> {
            configurations.named("implementation").configure(spec -> {
                for (Project contained : side.getContains().get()) {
                    spec.getDependencies().add(dependencyFactory.create(contained));
                }
            });

            configurations.named("compileOnly").configure(spec -> {
                var classpath = side.getAdditionalCompileDependencies();
                spec.getDependencies().addAllLater(classpath.map(notations -> {
                    var result = new ArrayList<Dependency>(notations.size());
                    for (var notation : notations) {
                        result.add(dependencyFactory.create(notation));
                    }
                    return result;
                }));
            });
            configurations.named("runtimeOnly").configure(spec -> {
                var classpath = side.getAdditionalRuntimeDependencies();
                spec.getDependencies().addAllLater(classpath.map(notations -> {
                    var result = new ArrayList<Dependency>(notations.size());
                    for (var notation : notations) {
                        result.add(dependencyFactory.create(notation));
                    }
                    return result;
                }));
            });
            // Configuration that declares any additional compile-time libraries needed by
            // NeoForm
            neoFormLibraries.configure(spec -> {
                spec.getDependencies().addAllLater(side.getAdditionalCompileDependencies().map(depStrings -> {
                    var deps = new ArrayList<Dependency>();
                    for (var depString : depStrings) {
                        deps.add(dependencyFactory.create(depString));
                    }
                    return deps;
                }));
                spec.setTransitive(false);
            });
            neoFormLibrariesClasspath.configure(spec -> {
                spec.extendsFrom(neoFormLibraries.get());
                spec.setTransitive(false);
            });
            neoFormTools.configure(spec -> {
                spec.setDescription("Tools that are declared in this projects NeoForm config file.");
                if (side.getPreProcessJar() != null) {
                    spec.getDependencies().addAll(createDependencies(project, side.getPreProcessJar()));
                }
                spec.getDependencies().addAll(createDependencies(project, neoForm.getDecompiler()));
            });
        });

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Pre Process
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////

        RegularFileProperty processed = project.getObjects().fileProperty();
        project.afterEvaluate(tmp -> {
            if (side.getPreProcessJar() != null) {
                var prepareJarForDecompiler = tasks.register("prepareJarForDecompiler", PrepareJarForDecompiler.class, task -> {
                    task.setGroup("neoform/internal");
                    task.getClient().unset();
                    if (side.getUseClient().get()) {
                        task.getClient().set(rootPlugin.downloadVersionArtifacts().flatMap(DownloadVersionArtifacts::getClient));
                    }
                    if (side.getUseServer().get()) {
                        task.getServer().set(rootPlugin.downloadVersionArtifacts().flatMap(DownloadVersionArtifacts::getServer));
                    }
                    task.getOutput().set(inputsDir.map(dir -> dir.file("pre_processed.jar")));
                });
                ToolAction.configure(project, prepareJarForDecompiler, side.getPreProcessJar());
                processed.set(prepareJarForDecompiler.flatMap(PrepareJarForDecompiler::getOutput));
            } else if (side.getUseClient().get() && !side.getUseServer().get()) {
                processed.set(rootPlugin.downloadVersionArtifacts().flatMap(DownloadVersionArtifacts::getClient));
            } else if (side.getUseServer().get() && !side.getUseClient().get()) {
                processed.set(rootPlugin.downloadVersionArtifacts().flatMap(DownloadVersionArtifacts::getServer));
            } else {
                throw new GradleException("preProcessJar not set and either none or both sides are used for " + project.getDisplayName());
            }
        });

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Decompile
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var minecraftLibrariesClasspath = MinecraftLibraries.createConfiguration(project);
        var decompile = tasks.register("decompile", Decompile.class, task -> {
            task.setGroup("neoform/internal");
            task.getInput().set(processed);
            task.getInputClasspath().from(minecraftLibrariesClasspath);
            task.getOutput().set(inputsDir.map(dir -> dir.file("sources.zip")));
        });
        ToolAction.configure(project, decompile, neoForm.getDecompiler());

        TaskProvider<DeduplicateSources> deduplicateSources = rootPlugin.getDeduplicateSources();
        Provider<RegularFile> decompiled = decompile.flatMap(Decompile::getOutput);
        Provider<RegularFile> deduplicatedProvider = inputsDir.map(dir -> dir.file("deduplicated.jar"));
        deduplicateSources.configure(task -> {
            DeduplicateSources.Source deduplicateSource = task.getSource(project.getPath());
            deduplicateSource.getInput().set(decompiled);
            deduplicateSource.getOutput().set(deduplicatedProvider);
            deduplicateSource.getContains().set(side.getContains().map(list -> list.stream().map(Project::getPath).toList()));
            deduplicateSource.getRequired().set(side.getUseClient().map(client -> client ? List.of("/assets/") : List.of()));
        });
        Provider<RegularFile> deduplicated = deduplicateSources.flatMap(task -> task.getSides().getting(project.getPath()).flatMap(DeduplicateSources.Source::getOutput));

        Directory sources = project.getLayout().getProjectDirectory().dir("src/main/java");
        Directory resources = project.getLayout().getProjectDirectory().dir("src/main/resources");
        Directory patches = project.getRootProject().getLayout().getProjectDirectory().dir("src/" + sideName + "/patches");
        var cleanPatchWorkspace = tasks.register("cleanSources", Delete.class, task -> {
            task.setGroup("neoform");
            task.setDescription("Removes the current patch workspace, if it exists.");
            task.delete(sources, resources);
        });
        var createPatchWorkspace = tasks.register("createSources", CreatePatchWorkspace.class, task -> {
            task.setGroup("neoform");
            task.setDescription("Creates a Gradle subproject under 'workspace' that contains the patched Minecraft sources to work on modifying the patches easily. Run the createPatches task to generate new patches from this workspace.");
            task.getSourcesZip().set(deduplicated);
            task.getWorkspace().set(project.getLayout().getProjectDirectory());
            task.getPatchesDir().set(patches);
        });
        createPatches = tasks.register("createPatches", CreatePatches.class, task -> {
            task.setGroup("neoform");
            task.getPatchesDir().set(patches);
            task.getSourcesZip().set(deduplicated);
            task.getModifiedSources().set(sources);
            task.mustRunAfter(createPatchWorkspace);
        });

        project.afterEvaluate(tmp -> {
            if (side.getPatchSource().isPresent()) {
                TaskProvider<CreatePatches> patchSource = side.getPatchSource().get().getPlugins().findPlugin(NeoFormWorkspacePlugin.class).getCreatePatches();
                createPatchWorkspace.configure(task -> {
                    // TODO: May need to fuzzily apply client patches but as of 26.1-snapshot-7 no
                    // patches are close enough to an OnlyIn annotation
                    task.getPatchesDir().set(patchSource.get().getPatchesDir().get());
                    task.mustRunAfter(patchSource);
                });
                createPatches.configure(task -> {
                    task.dependsOn(createPatchWorkspace);
                });
            }
        });

        rootPlugin.getCleanPatchWorkspace().configure(task -> task.dependsOn(cleanPatchWorkspace));
        rootPlugin.getCreatePatchWorkspace().configure(task -> task.dependsOn(createPatchWorkspace));
        rootPlugin.getCreatePatches().configure(task -> task.dependsOn(createPatches));

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Testing Tasks
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var neoFormRuntimeLibrariesClasspath = configurations.resolvable("neoFormRuntimeLibrariesClasspath", spec -> {
            spec.setDescription("Classpath used to pre-resolve tools and libraries for NFRT");
            spec.extendsFrom(neoFormTools.get());
            spec.extendsFrom(neoFormLibraries.get());
        });

        Action<NeoFormRuntimeTask> nfrtConfigurer = task -> {
            task.addArtifactsToManifest(neoFormRuntimeLibrariesClasspath.get());
            task.addArtifactsToManifest(minecraftLibrariesClasspath.get());
        };

        Provider<RegularFile> dataZip = rootPlugin.getDataZip();

        // Explicitly test with a variety of different JDKs, this tests both running the
        // decompile & recompile using that JDK.
        var javaVersions = Stream.concat(Stream.of(neoForm.getJavaVersion().get()), neoForm.getTestJavaVersions().get().stream()).toList();
        for (int testJavaVersion : javaVersions) {
            String taskName = testJavaVersion == neoForm.getJavaVersion().get() ? "testData" : "testDataJava" + testJavaVersion;

            var testData = tasks.register(taskName, TestWithNeoFormRuntime.class, task -> {
                task.setGroup("verification");
                task.setDescription("Tests that the data produced by this project can be consumed by NFRT to generate compilable sources and compile them.");
                nfrtConfigurer.execute(task);
                task.getDist().set(sideName);
                task.getNeoFormDataArchive().set(dataZip);
                task.getResultsDirectory().set(project.getLayout().getBuildDirectory().dir("test-results/" + taskName));
                task.getJavaExecutable().convention(getJavaToolchains()
                        .launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(neoForm.getJavaVersion().get())))
                        .map(javaLauncher -> javaLauncher.getExecutablePath().getAsFile().getAbsolutePath()));
            });
            tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(task -> task.dependsOn(testData));
        }
        configureEclipseTestTask(project, neoForm, dataZip, neoFormLibrariesClasspath.get(), minecraftLibrariesClasspath.get(), nfrtConfigurer, side);

        project.getRootProject().getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(task -> task.dependsOn(tasks.named("check")));
        rootPlugin.getCreateConfig().configure(task -> task.addSide(sideName, side));
        rootPlugin.getUpdateTools().configure(task -> task.addSubProject(project));
        project.afterEvaluate(tmp -> {
            if (side.getUseClient().get()) {
                // Create the machinery to download assets and announce their availability
                // through a file on the classpath to the startup class.
                var mainSourceSet = sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME);
                var clientSourceSet = sourceSets.register("client", spec -> {
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
                var versionManifest = rootPlugin.downloadVersionArtifacts().flatMap(DownloadVersionArtifacts::getVersionManifest);

                var generateRunClientClass = tasks.register("generateRunClientClass", GenerateRunClientClass.class, task -> {
                    task.setGroup("neoform/internal");
                    task.setDescription("Generates the class to launch Minecraft.");
                    task.getOutput().set(layout.getProjectDirectory().dir("src/client/java"));
                    task.getAssetProperties().set(rootPlugin.getDownloadAssets().flatMap(DownloadAssets::getAssetPropertiesFile));
                    task.getVersionManifest().set(versionManifest);
                });
                NeoDevFacade.runTaskOnProjectSync(project, generateRunClientClass);

                // Move the ide sync task into neoform/internal
                tasks.named("neoForgeIdeSync").configure(task -> {
                    task.setGroup("neoform/internal");
                });

                tasks.register("runClient", JavaExec.class, task -> {
                    task.setGroup("neoform");
                    task.setDescription("Runs the client for testing.");
                    task.getMainClass().set("StartClient");
                    task.setClasspath(clientSourceSet.get().getRuntimeClasspath());
                });
            }
        });

        rootPlugin.getNeoformData().configure(configuration -> configuration.getDependencies().addAllLater(neoFormTools.map(Configuration::getAllDependencies)));
        rootPlugin.getNeoformRuntimeElements().configure(configuration -> configuration.getDependencies().addAllLater(neoFormLibraries.map(Configuration::getAllDependencies)));
        rootPlugin.getNeoformApiElements().configure(configuration -> configuration.getDependencies().addAllLater(neoFormLibraries.map(Configuration::getAllDependencies)));
    }

    private void configureEclipseTestTask(Project project,
            NeoFormExtension neoForm,
            Provider<RegularFile> dataZip,
            Configuration additionalCompileDependencies,
            Configuration minecraftLibraries,
            Action<NeoFormRuntimeTask> nfrtConfigurer,
            NeoFormSideExtension side) {

        var eclipseCompiler = project.getConfigurations().dependencyScope("eclipseCompiler", spec -> {
            spec.getDependencies().add(project.getDependencyFactory().create("org.eclipse.jdt:ecj:3.44.0"));
        });
        var eclipseCompilerClasspath = project.getConfigurations().resolvable("eclipseCompilerClasspath", spec -> {
            spec.extendsFrom(eclipseCompiler.get());
        });

        var tasks = project.getTasks();

        var testTask = tasks.register("testDataWithEclipseCompiler", TestWithEclipseCompiler.class, task -> {
            task.setGroup("verification");
            task.getSources().from(project.file("src/main/java"));
            task.getSources().from(side.getContains().map(contains -> contains.stream().map(contained -> contained.file("src/main/java")).toList()));
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

    private static List<? extends Dependency> createDependencies(Project project, ToolSettings toolSettings) {
        var dependencyFactory = project.getDependencyFactory();
        return toolSettings.getClasspath().get().stream().map(dependencyFactory::create).toList();
    }

    public TaskProvider<CreatePatches> getCreatePatches() {
        return createPatches;
    }

    @Inject
    protected abstract JavaToolchainService getJavaToolchains();
}
