package net.neoforged.neoform.tasks;

import io.codechicken.diffpatch.cli.PatchOperation;
import io.codechicken.diffpatch.util.Input;
import io.codechicken.diffpatch.util.LogLevel;
import io.codechicken.diffpatch.util.Output;
import io.codechicken.diffpatch.util.PatchMode;
import net.neoforged.neoform.DirectoryCleaner;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public abstract class CreatePatchWorkspace extends DefaultTask {

    public static final ProblemGroup PROBLEM_GROUP = ProblemGroup.create("neoform", "NeoForm");

    private static final ProblemId PATCH_FAILED = ProblemId.create("patch-failed", "Patch failed to apply", PROBLEM_GROUP);
    private static final ProblemId PATCH_TARGET_MISSING = ProblemId.create("patch-target-missing", "Patch targets missing file", PROBLEM_GROUP);

    @InputFile
    public abstract RegularFileProperty getSourcesZip();

    // TODO: temporary hack since CG doesn't preserve the resources in the jar...
    @InputFile
    public abstract RegularFileProperty getResourcesZip();

    @InputDirectory
    public abstract DirectoryProperty getPatchesDir();

    @org.gradle.api.tasks.Input
    public abstract Property<Boolean> getUpdateMode();

    @OutputDirectory
    public abstract DirectoryProperty getWorkspace();

    private final ProblemReporter reporter;

    @Inject
    public CreatePatchWorkspace(Problems problems) {
        this.reporter = problems.getReporter();
        this.getUpdateMode().convention(false);
    }

    record Patch(Path patchPath, byte[] content) {
    }

    @TaskAction
    public void createWorkspace() throws IOException {
        boolean updateMode = getUpdateMode().get();
        if (updateMode) {
            getLogger().lifecycle("************************************************************************");
            getLogger().lifecycle("RUNNING IN UPDATE MODE");
            getLogger().lifecycle("************************************************************************");
        }

        var workspace = getWorkspace().getAsFile().get().toPath();

        // Set up and clear rejects directory
        var rejectsDir = workspace.resolve("rejects");
        if (Files.isDirectory(rejectsDir)) {
            DirectoryCleaner.cleanDirectory(rejectsDir, f -> f.getFileName().toString().endsWith(".patch"));
            try {
                Files.delete(rejectsDir);
            } catch (IOException ignored) {
                // Ignore if not emtpy
            }
        }

        var sourcesDir = workspace.resolve("src/main/java");
        var resourcesDir = workspace.resolve("src/main/resources");
        Files.createDirectories(sourcesDir);
        Files.createDirectories(resourcesDir);

        // Gather all patches
        Map<String, Patch> patches = new HashMap<>();
        var patchesBase = getPatchesDir().getAsFile().get().toPath();
        for (var file : getPatchesDir().getAsFileTree().getFiles()) {
            if (!file.getName().endsWith(".patch")) {
                getLogger().warn("Found non-patch file in patch folder: {}", file);
                continue;
            }

            var patchPath = file.toPath();
            var targetPath = patchesBase.relativize(patchPath).toString().replace('\\', '/').replaceAll("\\.patch$", "");
            var patchContent = Files.readAllBytes(patchPath);
            patches.put(targetPath, new Patch(patchPath, patchContent));
        }

        List<Problem> problems = new ArrayList<>();
        var failedPatches = new HashSet<String>();
        var successfulPatches = 0;
        var dirsCreated = new HashSet<Path>();
        for (var inzip : List.of(getSourcesZip(), getResourcesZip())) {
            try (var zip = new ZipFile(inzip.getAsFile().get())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }

                    // TODO: CG should normalize the paths to / when writing the resulting zip entries
                    var entryName = entry.getName().replace('\\', '/');

                    Path destination;
                    if (entry.getName().endsWith(".java")) {
                        if (inzip == getResourcesZip()) {
                            // Hackkkk
                            continue;
                        }
                        destination = sourcesDir;
                    } else {
                        destination = resourcesDir;
                    }
                    destination = destination.resolve(entry.getName());

                    if (dirsCreated.add(destination.getParent())) {
                        Files.createDirectories(destination.getParent());
                    }

                    try (var input = zip.getInputStream(entry)) {
                        var patch = patches.remove(entryName);
                        if (patch != null) {
                            var rejectsOutput = new ByteArrayOutputStream();
                            var builder = PatchOperation.builder()
                                    .logTo(line -> getLogger().lifecycle("{}", line))
                                    .baseInput(Input.SingleInput.pipe(input, entryName))
                                    .patchesInput(Input.SingleInput.pipe(new ByteArrayInputStream(patch.content), patch.patchPath.toString()))
                                    .patchedOutput(Output.SingleOutput.path(destination))
                                    .level(LogLevel.WARN)
                                    .mode(PatchMode.OFFSET);

                            if (updateMode) {
                                builder.mode(PatchMode.OFFSET)
                                        .level(io.codechicken.diffpatch.util.LogLevel.INFO)
                                        .rejectsOutput(Output.SingleOutput.pipe(rejectsOutput));
                            }

                            var result = builder.build().operate();

                            if (result.exit != 0) {
                                problems.add(reporter.create(PATCH_FAILED, problem -> {
                                    problem
                                            .details("Applying the patch to " + entryName + " failed.")
                                            .fileLocation(patch.patchPath.toAbsolutePath().toString())
                                            .severity(Severity.ERROR);
                                }));
                                getLogger().error("Applying the patch to {}} failed.", entryName);

                                if (updateMode && rejectsOutput.size() > 0) {
                                    Path rejectsPath = rejectsDir.resolve(entryName + ".patch");
                                    if (dirsCreated.add(rejectsPath.getParent())) {
                                        Files.createDirectories(rejectsPath.getParent());
                                    }
                                    Files.write(rejectsPath, rejectsOutput.toByteArray());
                                }

                                failedPatches.add(entryName);
                            } else {
                                successfulPatches++;
                            }
                        } else {
                            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        }

        // Report patches we didn't use as unused
        for (var patch : patches.values()) {
            var patchPath = patch.patchPath.toAbsolutePath().toString();
            problems.add(reporter.create(PATCH_TARGET_MISSING, problem -> problem
                    .details("The file targeted by " + patchPath + " does not exist.")
                    .fileLocation(patchPath)
                    .severity(Severity.ERROR)));
            failedPatches.add(patchPath);
            getLogger().error("The file targeted by {} does not exist.", patchPath);
        }

        if (!problems.isEmpty()) {
            reporter.report(problems);
            var totalApplied = failedPatches.size() + successfulPatches;
            throw new GradleException(failedPatches.size() + " out of " + totalApplied + " patches failed to apply.");
        }
    }
}
