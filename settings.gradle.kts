import java.lang.Runtime

pluginManagement {
    includeBuild("./plugin")
}

plugins {
    id("net.neoforged.neoform")
}

val threadCount = providers.gradleProperty("vineflowerThreads").orNull?.toInt()
    ?: Runtime.getRuntime().availableProcessors()

neoForm {
    minecraftVersion = "26.3-snapshot-6"

    additionalCompileDependencies = listOf(
        "net.neoforged:mergetool:2.0.7:api",
        "com.google.code.findbugs:jsr305:3.0.2",
        "org.jetbrains:annotations:26.1.0",
        // In the Minecraft libraries list, this is MacOS X specific since it only contains runtime dependencies
        // But the MacOS X specific code referencing this will be compiled on all platforms.
        "ca.weblite:java-objc-bridge:1.1"
    )

    javaVersion = 25
    testJavaVersions = listOf() // Additional Java versions to test with

    preProcessJar {
        classpath = listOf("net.neoforged.installertools:installertools:4.0.17:fatjar")
        args = listOf("--task", "PROCESS_MINECRAFT_JAR", "--input", "{inputClientJar}", "--input", "{inputServerJar}", "--output", "{output}", "--no-mod-manifest", "--no-dist-annotations")
    }

    decompiler {
        classpath = listOf(
            "org.vineflower:vineflower:1.12.0",
            "net.neoforged:vineflower-plugins:0.1.6"
        )
        mainClass = "org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler"
        jvmArgs = listOf("-Xmx4g")
        args = listOf(
            "--decompile-inner",
            "--remove-bridge",
            "--decompile-generics",
            "--ascii-strings",
            "--remove-synthetic",
            "--include-classpath",
            "--ignore-invalid-bytecode",
            "--bytecode-source-mapping",
            "--dump-code-lines",
            "--indent-string=    ",
            "--log-level=WARN",
            "--thread-count=$threadCount",
            "-cfg={inputLibraries}",
            "{input}",
            "{output}"
        )
    }
}
