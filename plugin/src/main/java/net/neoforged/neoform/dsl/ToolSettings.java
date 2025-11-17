package net.neoforged.neoform.dsl;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class ToolSettings {
    public abstract Property<String> getVersion();

    public abstract ListProperty<String> getArgs();

    public abstract ListProperty<String> getJvmArgs();

    /**
     * Optional if the tool is available from our default repositories (Maven central, NeoForge).
     * This repository is automatically added to the NeoForm project.
     */
    public abstract Property<String> getRepositoryUrl();

    public abstract Property<Integer> getJavaVersion();
}
