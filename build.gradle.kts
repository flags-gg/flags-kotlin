plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.central.publish)
}

group = "gg.flags"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)

    // HTTP client
    implementation(libs.bundles.ktor.client)

    // SQLite
    implementation(libs.sqlite.jdbc)

    // Logging
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

mavenCentralPublish {
    workingDir = layout.buildDirectory.dir("maven-central-publish").get().asFile
    singleDevId = "flags-gg"
    singleDevName = "Flags.gg Team"
    singleDevEmail = "support@flags.gg"
    licenseMIT()

    projectUrl = "https://github.com/flags-gg/flags-kotlin"
    scmUrl = "https://github.com/flags-gg/flags-kotlin.git"

    publication {
        artifact(tasks.jar)
        artifact(tasks.named("sourcesJar"))
        artifact(tasks.named("javadocJar"))
    }
}