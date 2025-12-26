plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.central.publish)
    `maven-publish`
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = "gg.flags"
            artifactId = "flags-kotlin"
            version = project.version.toString()

            pom {
                name.set("Flags Kotlin SDK")
                description.set("Kotlin SDK for Flags.gg feature flag service")
                url.set("https://github.com/flags-gg/flags-kotlin")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("Keloran")
                        name.set("Keloran")
                        email.set("keloran@chewedfeed.com")
                    }
                }

                scm {
                    url.set("https://github.com/flags-gg/flags-kotlin")
                    connection.set("scm:git:git://github.com/flags-gg/flags-kotlin.git")
                    developerConnection.set("scm:git:ssh://git@github.com/flags-gg/flags-kotlin.git")
                }
            }
        }
    }
}

mavenPublishing {
    publishingType = moe.karla.maven.publishing.MavenPublishingExtension.PublishingType.USER_MANAGED

    url = "https://github.com/flags-gg/flags-kotlin"
    developer("Keloran", "keloran@chewedfeed.com")
}