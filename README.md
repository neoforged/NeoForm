# NeoForm - Recompilable Minecraft Sources

NeoForm provides steps to create reproducible and recompilable Minecraft source code.

## Version Availability

The `main` branch contains the latest available version. Versions prior to the availability of
unobfuscated Minecraft (the first 1.21.12 snapshot) are contained in the `legacy` branch, and
corresponding `<mcversion>-dev` branches. Any version released for unobfuscated Minecraft will
have a corresponding tag as well.

## How to Use

A typical workflow to change the patches would be:

- Run `gradlew :createPatchWorkspace` in the root, when the patches are already correct for the targeted Minecraft
  version,
  or `gradlew :createPatchWorkspaceForUpdate` for performing an update.
- Reload the gradle project in your IDE, there will now be a subproject at `/workspace` containing the patched Minecraft
  code.
- Make any desired changes to the source code
- Run `gradlew :createPatches` to create new patch files from the sources currently contained in the workspace.

## Gradle Tasks

| Task                             | Description                                                                                                                                                   |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `:createPatchWorkspace`          | Decompile Minecraft, apply patches from `src/patches` and place the code in `workspace`.                                                                      |
| `:createPatchWorkspaceForUpdate` | Same as `:createPatchWorkspace`, but patches will be applied partially and rejects stored in the `rejects` folder. Used to update to a new Minecraft version. |
| `:workspace:runClient`           | Runs the client in the workspace subproject.                                                                                                                  |
| `:check`                         | Runs the data contained in the current branch through [NeoFormRuntime](https://github.com/neoforged/NeoFormRuntime/), as well as the Eclipse Compiler.        |

## GitHub Actions Workflows

| Workflow                                                           | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|--------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [check-for-updates.yml](./.github/workflows/check-for-updates.yml) | Our automation platform (n8n) regularly checks for Minecraft releases. It triggers this workflow a few minutes after it triggers building the corresponding [minecraft-dependencies](https://github.com/neoforged/GradleMinecraftDependencies). This workflow will then check for a Minecraft release, and invoke the `update.yml` workflow for the new version. If the version is detected to be a "special" snapshot (i.e. April Fools), the update will be performed in a new branch. |
| [update.yml](./.github/workflows/update.yml)                       | Performs an update to a new Minecraft version in the branch it is triggered in. Can be triggered manually. If the update completes successfully, a publish job is automatically started.                                                                                                                                                                                                                                                                                                 |
| [publish.yml](./.github/workflows/publish.yml)                     | Runs the tests, creates a Git tag (for releases) and publishes to the [NeoForge maven](https://maven.neoforged.net/). It can also publish `SNAPSHOT`-versions to the snapshot maven.                                                                                                                                                                                                                                                                                                     |
| [build.yml](./.github/workflows/build.yml)                         | Runs for pushes and pull requests. Essential for PR publishing.                                                                                                                                                                                                                                                                                                                                                                                                                          |

