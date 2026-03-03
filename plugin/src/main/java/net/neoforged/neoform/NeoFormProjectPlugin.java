package net.neoforged.neoform;

import net.neoforged.gradleutils.GradleUtilsExtension;
import net.neoforged.gradleutils.PomUtilsExtension;
import net.neoforged.neoform.dsl.NeoFormExtension;
import net.neoforged.neoform.tasks.CreateConfig;
import net.neoforged.neoform.tasks.DeduplicateSources;
import net.neoforged.neoform.tasks.DownloadVersionArtifacts;
import net.neoforged.neoform.tasks.UpdateMinecraft;
import net.neoforged.neoform.tasks.UpdateTools;
import net.neoforged.nfrtgradle.DownloadAssets;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JvmEcosystemPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.inject.Inject;

public abstract class NeoFormProjectPlugin implements Plugin<Project> {

    private TaskProvider<DownloadVersionArtifacts> downloadVersionArtifacts;
    private TaskProvider<Task> cleanPatchWorkspace;
    private TaskProvider<Task> createPatchWorkspace;
    private TaskProvider<Task> createPatches;
    private TaskProvider<DeduplicateSources> deduplicateSources;
    private TaskProvider<DownloadAssets> downloadAssets;
    private TaskProvider<CreateConfig> createConfig;
    private TaskProvider<UpdateTools> updateTools;
    private Provider<RegularFile> dataZip;
    private NamedDomainObjectProvider<ConsumableConfiguration> neoformData;
    private NamedDomainObjectProvider<ConsumableConfiguration> neoformRuntimeElements;
    private NamedDomainObjectProvider<ConsumableConfiguration> neoformApiElements;

    @Inject
    protected abstract JavaToolchainService getJavaToolchains();

    public void apply(Project project) {
        if (project.getRootProject() != project) {
            throw new InvalidUserCodeException("This plugin should only be applied to the root project.");
        }

        // This plugin is required to have resolving jars via configurations work
        // (it adds the required attribute disambiguation rules)
        project.getPlugins().apply(BasePlugin.class);
        project.getPlugins().apply(JvmEcosystemPlugin.class);

        var objects = project.getObjects();
        var tasks = project.getTasks();
        var configurations = project.getConfigurations();
        var buildDir = project.getLayout().getBuildDirectory();

        var neoForm = NeoFormExtension.fromProject(project);
        var inputsDir = buildDir.dir("neoform/inputs/" + neoForm.getMinecraftVersion().get());
        var minecraftVersion = neoForm.getMinecraftVersion();

        project.setGroup("net.neoforged");
        TagBasedVersioning.configureVersion(project, minecraftVersion.get());

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Download Version Manifest and Artifacts using NFRT
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        downloadVersionArtifacts = tasks.register("downloadVersionArtifacts", DownloadVersionArtifacts.class, task -> {
            task.setGroup("neoform/internal");
            task.setDescription("Downloads the version manifest, client and server jar for the Minecraft version");
            task.getMinecraftVersion().set(minecraftVersion);
            task.getVersionManifest().set(inputsDir.map(dir -> dir.file("version.json")));
            task.getClient().set(inputsDir.map(dir -> dir.file("client.jar")));
            task.getServer().set(inputsDir.map(dir -> dir.file("server.jar")));
        });

        deduplicateSources = tasks.register("deduplicateSources", DeduplicateSources.class, task -> {
            task.setGroup("neoform/internal");
        });

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Workflow Tasks
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var workspaceDir = project.getLayout().getProjectDirectory().dir("workspace");
        cleanPatchWorkspace = tasks.register("cleanPatchWorkspace", task -> {
            task.setGroup("neoform");
            task.setDescription("Removes the current patch workspace, if it exists.");
        });
        createPatchWorkspace = tasks.register("createPatchWorkspace", task -> {
            task.setGroup("neoform");
            task.setDescription("Creates a Gradle subproject under 'workspace' that contains the patched Minecraft sources to work on modifying the patches easily. Run the createPatches task to generate new patches from this workspace.");
        });
        createPatches = tasks.register("createPatches", task -> {
            task.setGroup("neoform");
        });
        var assetsResourceDir = project.getLayout().getBuildDirectory().dir("assets");
        downloadAssets = tasks.register("downloadAssets", DownloadAssets.class, task -> {
            task.setGroup("neoform/internal");
            task.setDescription("Download the client-side assets to be able to run the game.");
            task.getMinecraftVersion().set(neoForm.getMinecraftVersion());

            task.getAssetPropertiesFile().set(assetsResourceDir.map(dir -> dir.file("neoform_assets.properties")));
            task.getOutputs().dir(assetsResourceDir);
        });
        createConfig = tasks.register("createConfig", CreateConfig.class, task -> {
            task.setGroup("neoform");
            task.setDescription("Creates the NeoForm config JSON for use by tooling from the configuration in the current project");
            task.getOutput().set(project.getLayout().getBuildDirectory().file("neoform/config.json"));
            task.getMinecraftVersion().set(neoForm.getMinecraftVersion());
            task.getDecompiler().set(neoForm.getDecompiler());
            task.getEncoding().set("UTF-8");
            task.getJavaVersion().set(neoForm.getJavaVersion());
        });
        var createDataZip = tasks.register("createDataArchive", Zip.class, task -> {
            task.setGroup("neoform");
            task.setDescription("Builds the data archive containing NeoForm patches and configuration");
            task.from(createConfig, spec -> spec.into("/").rename(".*", "config.json"));
            task.from(project.files("src/client/patches"), spec -> spec.into("/patches/client"));
            task.from(project.files("src/joined/patches"), spec -> spec.into("/patches/joined"));

            task.from(project.files("src/server/patches"), spec -> spec.into("/patches/client"));
            task.from(project.files("src/server/patches"), spec -> spec.into("/patches/joined"));
            task.from(project.files("src/server/patches"), spec -> spec.into("/patches/server"));
            task.getArchiveBaseName().set("neoform");
            task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("libs"));
        });
        tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure(task -> task.dependsOn(createDataZip));
        dataZip = createDataZip.flatMap(Zip::getArchiveFile);
        tasks.register("updateMinecraft", UpdateMinecraft.class);
        updateTools = tasks.register("updateTools", UpdateTools.class);

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Publishing Tasks
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        neoformData = configurations.consumable("neoformData", configuration -> {
            configuration.getAttributes().attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, neoForm.getJavaVersion());
            configuration.getOutgoing().capability("net.neoforged:neoform:" + project.getVersion());
        });
        project.getArtifacts().add(neoformData.getName(), createDataZip);

        MinecraftLibraries.createConfiguration(project);
        neoformRuntimeElements = configurations.consumable("neoformRuntimeElements", configuration -> {
            configuration.attributes(attributes -> {
                attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, neoForm.getJavaVersion());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
            });
            configuration.getOutgoing().capability("net.neoforged:neoform-dependencies:" + project.getVersion());
            configuration.extendsFrom(configurations.named(MinecraftLibraries.DEPENDENCY_SCOPE).get());
        });
        neoformApiElements = configurations.consumable("neoformApiElements", configuration -> {
            configuration.attributes(attributes -> {
                attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, neoForm.getJavaVersion());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_API));
            });
            configuration.getOutgoing().capability("net.neoforged:neoform-dependencies:" + project.getVersion());
            configuration.extendsFrom(configurations.named(MinecraftLibraries.DEPENDENCY_SCOPE).get());
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

    public TaskProvider<DownloadVersionArtifacts> downloadVersionArtifacts() {
        return downloadVersionArtifacts;
    }

    public TaskProvider<Task> getCleanPatchWorkspace() {
        return cleanPatchWorkspace;
    }

    public TaskProvider<Task> getCreatePatchWorkspace() {
        return createPatchWorkspace;
    }

    public TaskProvider<Task> getCreatePatches() {
        return createPatches;
    }

    public TaskProvider<DeduplicateSources> getDeduplicateSources() {
        return deduplicateSources;
    }

    public TaskProvider<DownloadAssets> getDownloadAssets() {
        return downloadAssets;
    }

    public TaskProvider<CreateConfig> getCreateConfig() {
        return createConfig;
    }

    public TaskProvider<UpdateTools> getUpdateTools() {
        return updateTools;
    }

    public Provider<RegularFile> getDataZip() {
        return dataZip;
    }

    public NamedDomainObjectProvider<ConsumableConfiguration> getNeoformData() {
        return neoformData;
    }

    public NamedDomainObjectProvider<ConsumableConfiguration> getNeoformRuntimeElements() {
        return neoformRuntimeElements;
    }

    public NamedDomainObjectProvider<ConsumableConfiguration> getNeoformApiElements() {
        return neoformApiElements;
    }

    @Inject
    protected abstract SoftwareComponentFactory getComponentFactory();
}
