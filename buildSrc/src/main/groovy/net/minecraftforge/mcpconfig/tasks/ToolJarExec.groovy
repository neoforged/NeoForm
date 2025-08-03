package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

import javax.inject.Inject

abstract class ToolJarExec extends JavaExec {
    def config(def cfg, def configuration) {
        classpath = configuration
        args = cfg.args
        jvmArgs = cfg.jvmargs

        if (cfg.java_version != null) {
            javaLauncher.set(javaToolchainService.launcherFor {
                it.languageVersion = JavaLanguageVersion.of(cfg.java_version)
            })
        }
    }

    ToolJarExec() {
        def javaTarget = project.ext.JAVA_TARGET
        if (javaTarget != null) {
            javaLauncher.convention(javaToolchainService.launcherFor {
                it.languageVersion = JavaLanguageVersion.of(javaTarget)
            })
        }
    }

    @Inject
    JavaToolchainService getJavaToolchainService() {
        throw new UnsupportedOperationException()
    }

    @Override
    public final void exec() {
        this.preExec()
        super.exec()
        this.postExec()
    }
    
    protected void preExec(){}
    protected void postExec(){}
}