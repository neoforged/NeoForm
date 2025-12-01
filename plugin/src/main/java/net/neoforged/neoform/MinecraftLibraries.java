package net.neoforged.neoform;

import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.neoform.dsl.NeoFormExtension;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvableConfiguration;

import java.util.ArrayList;

final class MinecraftLibraries {
    static final String DEPENDENCY_SCOPE = "minecraftLibraries";

    private MinecraftLibraries() {
    }

    public static NamedDomainObjectProvider<ResolvableConfiguration> createConfiguration(Project project) {
        // This is needed to set up the attribute disambiguation rules
        project.getPlugins().apply(MinecraftDependenciesPlugin.class);

        var configurations = project.getConfigurations();
        var neoForm = NeoFormExtension.fromProject(project);

        var minecraftLibraries = configurations.dependencyScope(DEPENDENCY_SCOPE, spec -> {
            var dependencyFactory = project.getDependencyFactory();
            var classpath = neoForm.getMinecraftDependencies();
            spec.getDependencies().addAllLater(classpath.map(notations -> {
                var result = new ArrayList<Dependency>(notations.size());
                for (var notation : notations) {
                    result.add(dependencyFactory.create(notation));
                }
                return result;
            }));
        });
        return configurations.resolvable("minecraftLibrariesClasspath", spec -> spec.extendsFrom(minecraftLibraries.get()));
    }

}
