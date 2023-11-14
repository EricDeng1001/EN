pluginManagement {
    repositories {
        gradlePluginPortal {
            uri("https://maven.aliyun.com/repository/gradle-plugin")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}


rootProject.name = "ExpressionNetwork"