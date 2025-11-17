package net.neoforged.neoform;

import net.neoforged.neoform.dsl.NeoFormExtension;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.attributes.Attribute;

import java.util.ArrayList;

final class MinecraftLibraries {
    private MinecraftLibraries() {
    }

    public static NamedDomainObjectProvider<ResolvableConfiguration> createConfiguration(Project project) {
        var configurations = project.getConfigurations();
        var neoForm = NeoFormExtension.fromProject(project);

        var minecraftLibraries = configurations.dependencyScope("minecraftLibraries", spec -> {
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
        return configurations.resolvable("minecraftLibrariesClasspath", spec -> {
            spec.extendsFrom(minecraftLibraries.get());
            spec.attributes(attributes -> {
                attributes.attribute(Attribute.of("org.gradle.jvm.environment", String.class), "standard-jvm");
                attributes.attribute(
                        Attribute.of("net.neoforged.distribution", String.class), "client"
                );
                attributes.attribute(
                        Attribute.of("net.neoforged.operatingsystem", String.class), "windows"
                );
            });
        });
    }

}
