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
| `testJdks`                       | Checks that the source code can be compiled with various JDKs.                                                                                                |

## GitHub Actions Variables

Some workflows need to know which release-version the current series of snapshots will result in.

We set the GitHub Actions variable `TARGET_MC_VERSION` for this purpose in
the [project settings](https://github.com/neoforged/NeoForm/settings/variables/actions).
This project provides the tooling and workflow needed to provide a
decompile, patch and recompile workflow for modding Minecraft.

If we suspect that the current series of snapshots will be released as Minecraft 1.21.2 for example, this variable
should be set to `1.21.2`.
The steps taken with NeoForm to produce source code that can be modified
and then recompiled is:

## GitHub Actions Workflows

* `Check For New Snapshots` - Runs every 30 minutes from 6am to 7pm UTC and checks for when Mojang releases a new
  version and runs the `Update` workflow
* `Update` - Runs the `update` task and checks for any library updates along with copying over the patches from the old
  version to the new version then attempts to run the `Check` workflow. Parameters:
    * `Old branch` - The branch name where the old version is
    * `Old version` - `<type>/<ver>/<version>` of the old version
    * `Patches version` (deprecated) - `<type>/<ver>/<version>` of the version to take the patches from (defaults to
      `Old version`)
    * `New branch` - The branch name of where the new version will be (defaults to `Old branch`)
    * `New version` - `<type>/<ver>/<version>` of the new version
    * `Fix known error(s) in mapping file` (deprecated) - Was used for when Jammer would generate incorrect names for
      fields or methods
    * `Copy extras` - When changing branches copy over files outside of the `versions` and `.github` directories
    * `Publish` - Passed to `Check` workflow
* `Check` - Required to run from the branch with the version being checked and checks if patches successfully apply and
  runs `:<version>:testJdks` and optionally publishes to the [NeoForge maven](https://maven.neoforged.net/). Parameters:
    * `Version` - `<type>/<ver>/<version>` of the version
    * `Push changed patches` - If enabled and running `:<version>:projectApplyAll` then `:<version>:projectMakeAll`
      changes patches then it pushes the updated patches
    * `Publish` whether to publish to the [NeoForge maven](https://maven.neoforged.net/)
