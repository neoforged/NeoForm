plugins {
    java
    `java-gradle-plugin`
    `maven-publish`
}

group = "net.neoforged.neoform"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    implementation(libs.gson)
    implementation(libs.gradleDownloadTask)
    implementation(libs.diffpatch)
    implementation(libs.gradleutils)
    implementation(libs.foojay)
    implementation(libs.moddev)
}

dependencyLocking {
    lockMode = LockMode.STRICT
}

gradlePlugin {
    website = "https://neoforged.net"
    vcsUrl = "https://github.com/neoforged/NeoForm"
    plugins {
        register("neoFormPlugin") {
            id = "net.neoforged.neoform"
            implementationClass = "net.neoforged.neoform.NeoFormSettingsPlugin"
            displayName = "NeoForm"
            description = "Provides recompilable Minecraft source code by decompiling Minecraft jars and applying patches"
            tags = listOf("minecraft", "neoforge", "decompiler", "patches")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<ValidatePlugins>().configureEach {
    enableStricterValidation = true
}
