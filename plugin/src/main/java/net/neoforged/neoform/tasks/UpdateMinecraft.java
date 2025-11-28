package net.neoforged.neoform.tasks;

import net.neoforged.neoform.dsl.NeoFormExtension;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;

public abstract class UpdateMinecraft extends DefaultTask {
    private String newVersion;

    @Inject
    public UpdateMinecraft(Project project) {
        setGroup("neoform");
        setDescription("Update the Minecraft version in this project.");

        var neoForm = NeoFormExtension.fromProject(project);
        getCurrentVersion().set(neoForm.getMinecraftVersion());
        getSettingsScript().set(new File(project.getRootDir(), "settings.gradle"));

        getOutputs().upToDateWhen(task -> {
            var updateTask = (UpdateMinecraft) task;
            return updateTask.getCurrentVersion().get().equals(newVersion);
        });
    }

    @Input
    public abstract Property<String> getCurrentVersion();

    @Input
    @Optional
    public String getNewVersion() {
        return newVersion;
    }

    @Option(option = "version", description = "The Minecraft version to update to")
    public void setNewVersion(String version) {
        this.newVersion = version;
    }

    @Internal
    public abstract RegularFileProperty getSettingsScript();

    @TaskAction
    public void update() throws Exception {
        if (newVersion == null) {
            throw new InvalidUserCodeException("Pass the version to update  to using --version");
        }

        String currentVersion = getCurrentVersion().get();

        getLogger().lifecycle("================================================================================");
        getLogger().lifecycle("Updating Minecraft from " + currentVersion + " to " + newVersion);
        getLogger().lifecycle("================================================================================");

        var settingsScriptPath = getSettingsScript().get().getAsFile().toPath();
        String settingsScript = Files.readString(settingsScriptPath);

        // Replace the Minecraft version in settings.gradle, which is a simple search&replace
        settingsScript = settingsScript.replace(currentVersion, newVersion);

        Files.writeString(settingsScriptPath, settingsScript);

    }
}
