plugins {
    kotlin("jvm") version "1.9.20"
    application
    kotlin("plugin.serialization") version "1.9.20"
    id("io.ktor.plugin") version "2.3.5"
}


kotlin {
    jvmToolchain(17)
}

group = "dvm"
version = "1.0-SNAPSHOT"

repositories {
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
        artifactUrls("https://repo.maven.apache.org/maven2/")
    }
    maven {
        url = uri("https://maven.aliyun.com/repository/gradle-plugin")
        artifactUrls("https://plugins.gradle.org/m2/")
    }
}

dependencies {
    val ktorVersion: String by project
    val mongodbVersion: String by project
    val logbackVersion: String by project
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("com.charleskorn.kaml:kaml:0.54.0")

    // MongoDB Kotlin driver dependency
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:$mongodbVersion")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}



application {
    mainClass.set("MainKt")
}