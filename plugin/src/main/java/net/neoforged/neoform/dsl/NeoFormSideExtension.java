package net.neoforged.neoform.dsl;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class NeoFormSideExtension {

    @Inject
    public NeoFormSideExtension(NeoFormExtension parent, ObjectFactory objects) {
        setPreProcessJar(objects.newInstance(ToolSettings.class));
        getPreProcessJar().getJavaVersion().convention(parent.getJavaVersion());
        getUseClient().convention(false);
        getUseServer().convention(false);
    }

    public void preProcessJar(Action<? super ToolSettings> action) {
        action.execute(getPreProcessJar());
    }

    public abstract ListProperty<String> getAdditionalCompileDependencies();

    public abstract ListProperty<String> getAdditionalRuntimeDependencies();

    public abstract ToolSettings getPreProcessJar();

    public abstract void setPreProcessJar(ToolSettings preProcessJar);

    public abstract Property<Boolean> getUseClient();

    public abstract Property<Boolean> getUseServer();

    public abstract ListProperty<Project> getContains();

    public abstract Property<Project> getPatchSource();

}
