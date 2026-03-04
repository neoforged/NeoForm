package net.neoforged.neoform.tasks;

import net.neoforged.neoform.dsl.NeoFormExtension;
import net.neoforged.neoform.dsl.NeoFormSideExtension;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import javax.inject.Inject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class UpdateTools extends DefaultTask {
    @Inject
    public UpdateTools(Project project) {
        setGroup("neoform");
        setDescription("Update the tool versions used in the project.");

        var neoForm = NeoFormExtension.fromProject(project);
        getScripts().add(new File(project.getRootDir(), "settings.gradle"));

        var dependencyFactory = project.getDependencyFactory();
        addToLatestVersions(neoForm.getDecompiler().getClasspath().map(i -> i.stream().map(dependencyFactory::create).toList()).get());
    }

    public void addSubProject(Project project) {
        DependencyFactory dependencyFactory = project.getDependencyFactory();
        NeoFormSideExtension side = project.getExtensions().getByType(NeoFormSideExtension.class);
        if (side.getPreProcessJar() != null) {
            addToLatestVersions(side.getPreProcessJar().getClasspath().get().stream().map(dependencyFactory::create).toList());
        }
        getScripts().add(new File(project.getProjectDir(), "build.gradle"));
    }

    private void addToLatestVersions(List<ExternalModuleDependency> tools) {
        getCurrentVersions().putAll(tools.stream().collect(Collectors.toMap(
                tool -> tool.getModule().toString(),
                Dependency::getVersion
        )));

        // Slightly tricky. We use a configuration to figure out the latest versions of all tools
        var latestToolsConfiguration = getProject().getConfigurations().detachedConfiguration(
                tools.stream().map(this::getLatestVersionDependency).toArray(Dependency[]::new)
        );
        latestToolsConfiguration.setTransitive(false);
        getLatestVersions().putAll(latestToolsConfiguration.getIncoming().getArtifacts().getResolvedArtifacts().map(resolvedArtifacts -> {
            var result = new HashMap<String, String>();
            for (var resolvedArtifact : resolvedArtifacts) {
                var id = resolvedArtifact.getId();
                if (id instanceof ModuleComponentArtifactIdentifier artifactIdentifier) {
                    // This branch is taken when the dependency is for a classifier since we reference an artifact explicitly.
                    result.put(artifactIdentifier.getComponentIdentifier().getModuleIdentifier().toString(), artifactIdentifier.getComponentIdentifier().getVersion());
                } else if (id instanceof ModuleComponentIdentifier moduleIdentifier) {
                    // This branch is taken if the dependency is for a non-classifier.
                    result.put(moduleIdentifier.getModuleIdentifier().toString(), moduleIdentifier.getVersion());
                } else {
                    throw new GradleException("Unclear how to process tool version " + resolvedArtifact);
                }
            }
            return result;
        }));
    }

    private ExternalModuleDependency getLatestVersionDependency(ExternalModuleDependency dependency) {
        dependency = dependency.copy();
        dependency.version(spec -> spec.require("+"));
        return dependency;
    }

    @Internal
    public abstract ListProperty<File> getScripts();

    /**
     * Map from group:artifact to the latest version we can find of that.
     */
    @Input
    public abstract MapProperty<String, String> getLatestVersions();

    @Input
    public abstract MapProperty<String, String> getCurrentVersions();

    @TaskAction
    public void update() throws Exception {
        for (var entry : getLatestVersions().get().entrySet()) {
            var latestVersion = entry.getValue();
            var currentVersion = getCurrentVersions().get().get(entry.getKey());
            if (!Objects.equals(currentVersion, latestVersion)) {
                getLogger().lifecycle("Updating {} from {} to {}", entry.getKey(), currentVersion, latestVersion);
            } else {
                getLogger().lifecycle("No update available for {}. Latest version is {}.", entry.getKey(), latestVersion);
            }
        }
        for (Path path : getScripts().get().stream().map(File::toPath).toList()) {
            String settingsScript = Files.readString(path);
            String originalSettingsScript = settingsScript;

            var currentVersions = getCurrentVersions().get();
            var latestVersions = getLatestVersions().get();

            // Perform tool updates
            for (var entry : latestVersions.entrySet()) {
                var latestVersion = entry.getValue();
                var currentVersion = currentVersions.get(entry.getKey());
                if (!Objects.equals(currentVersion, latestVersion)) {
                    settingsScript = settingsScript.replace(entry.getKey() + ":" + currentVersion, entry.getKey() + ":" + latestVersion);
                }
            }

            if (!settingsScript.equals(originalSettingsScript)) {
                Files.writeString(path, settingsScript);
            } else {
                setDidWork(false);
            }
        }
    }
}