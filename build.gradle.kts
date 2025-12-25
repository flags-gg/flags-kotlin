plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
    alias(libs.plugins.dokka)
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

            pom {
                name.set("Flags.gg Kotlin Client")
                description.set("Kotlin client library for the flags.gg feature flag management system")
                url.set("https://github.com/flags-gg/kotlin-flags")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("flags-gg")
                        name.set("Flags.gg Team")
                        email.set("support@flags.gg")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/flags-gg/kotlin-flags.git")
                    developerConnection.set("scm:git:ssh://github.com/flags-gg/kotlin-flags.git")
                    url.set("https://github.com/flags-gg/kotlin-flags")
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

signing {
    val signingKeyId = System.getenv("SIGNING_KEY_ID")
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(publishing.publications["maven"])
}