# NeoForm
NeoForm provides steps to create reproducible and recompilable Minecraft source code.

# How to Use
Development versions of Minecraft for a release version will be on a branch named `<version>-dev`

Definitions:
* `type` can be either
    * `release` (release versions)
    * `pre` (pre-releases and release candidates)
    * `snapshot` (snapshots)
* `ver` is a major version of Minecraft without the ending `.<minor_version>` (e.g. the value for 1.21.1 is 1.21)
* `version` is a specific version of Minecraft

options in `local.properties` can be set to filter for which versions are loaded by gradle:
* `type` (defaults to `release`)
* `ver` (defaults to all major versions)

Gradle tasks:
* `:<version>:projectApplyAll` - Creates Minecraft source code of that version with patches applied
* `:<version>:projectMakeAll` - Generates patches for the specified version
* `update` - Requires `old_version` and `new_version` to be specified in `gradle.properties` in the format of `<type>/<ver>/<version>`.
Sets up the new version based on the old version without the patches from the old version
* `:<version>:testJdks` - Checks that the generated source code is consistant across java versions along with compilation

GitHub Actions Variables
* `TARGET_MC_VERSION` - Should be set at the start of a new snapshot series to the name of the release version

# GitHub Actions Workflows
* `Check For New Snapshots` - Runs every 30 minutes from 6am to 7pm UTC and checks for when Mojang releases a new version and runs the `Update` workflow
* `Update` - Runs the `update` task and checks for any library updates along with copying over the patches from the old version to the new version then attempts to run the `Check` workflow. Parameters:
    * `Old branch` - The branch name where the old version is
    * `Old version` - `<type>/<ver>/<version>` of the old version
    * `Patches version` (deprecated) - `<type>/<ver>/<version>` of the version to take the patches from (defaults to `Old version`)
    * `New branch` - The branch name of where the new version will be (defaults to `Old branch`)
    * `New version` - `<type>/<ver>/<version>` of the new version
    * `Fix known error(s) in mapping file` (deprecated) - Was used for when Jammer would generate incorrect names for fields or methods
    * `Copy extras` - When changing branches copy over files outside of the `versions` and `.github` directories
    * `Publish` - Passed to `Check` workflow
* `Check` - Required to run from the branch with the version being checked and checks if patches successfully apply and runs `:<version>:testJdks` and optionally publishes to the [NeoForge maven](https://maven.neoforged.net/). Parameters:
    * `Version` - `<type>/<ver>/<version>` of the version
    * `Push changed patches` - If enabled and running `:<version>:projectApplyAll` then `:<version>:projectMakeAll` changes patches then it pushes the updated patches
    * `Publish` whether to publish to the [NeoForge maven](https://maven.neoforged.net/)

# Mojang Mappings
This project uses the obfuscation logs provided by Mojang for Minecraft in order to generate its data. That means that the data here could be considered a derivative work of those mappings. So you should be fully versed in the license associated with those official mappings. You can read more about our interpretation of them [here.](https://github.com/neoforged/NeoForm/blob/main/Mojang.md)
