plugins {
    kotlin("jvm") version "1.9.20"
    application
    kotlin("plugin.serialization") version "1.9.10"
    id("io.ktor.plugin") version "2.3.5"
}

group = "dvm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        // Only required if using EAP releases
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    }
}
val ktorVersion = "2.3.5"
val mongodbVersion = "4.10.1"
dependencies {
    testImplementation(kotlin("test"))
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // MongoDB Kotlin driver dependency
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:$mongodbVersion")
}

tasks.test {
    useJUnitPlatform()
}


kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}