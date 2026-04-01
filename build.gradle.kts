import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Kotlin version bumped to be compatible with the Maven Publish plugin and Gradle 8.10
    kotlin("jvm") version "2.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("maven-publish")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.34.0"
    id("net.researchgate.release") version "3.1.0"
    jacoco
}

group = "io.github.ngirchev"
version = project.properties["version"] as String

repositories {
    mavenCentral()
}

dependencies {
    // SLF4J API only — consumers choose their own logging implementation
    implementation("org.slf4j:slf4j-api:2.0.17")
    compileOnly("ch.qos.logback:logback-classic:1.5.32")
    testImplementation("ch.qos.logback:logback-classic:1.5.32")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("io.mockk:mockk:1.14.3")
    testImplementation("org.junit.jupiter", "junit-jupiter-params", "5.12.2")
    testImplementation(kotlin("test"))
    
    // Jackson for JSON serialization
    api("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")
}

java {
    // Compile library to Java 11 bytecode while running Gradle/tests on a newer JDK (e.g. 21)
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "fsm"

        pom {
            name.set("fsm")
            description.set("Finite state machine utilities")
            url.set("https://github.com/NGirchev/fsm")

            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }

            developers {
                developer {
                    id.set("NGirchev")
                    name.set("Nikolay Girchev")
                }
            }

            scm {
                connection.set("scm:git:git://github.com/NGirchev/fsm.git")
                developerConnection.set("scm:git:ssh://github.com:NGirchev/fsm.git")
                url.set("https://github.com/NGirchev/fsm")
            }
        }
    }
    repositories {
        // enable install to ~/.m2 via task publishToMavenLocal
        mavenLocal()
    }
}

// Signing configuration for Maven Central
// Equivalent to Maven maven-gpg-plugin in profile "release"
// GPG settings are read from gradle.properties or ~/.gradle/gradle.properties:
//   signing.gnupg.keyName
//   signing.gnupg.passphrase  – passphrase for this key
//   signing.gnupg.executable  – path to gpg binary (optional, defaults to "gpg")
signing {
    val isLocalPublish = project.gradle.startParameter.taskNames.any { 
        it == "publishToMavenLocal" 
    }
    if (!isLocalPublish) {
        useGpgCmd()
        // Sign all Maven publications (including the one used by vanniktech plugin for Central)
        sign(publishing.publications)
    }
}

detekt {
    config.setFrom("$projectDir/detekt.yml") // point to your custom config defining rules to run, overwriting default behavior
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true) // observe findings in your browser with structure and code snippets
        md.required.set(true) // simple Markdown format
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            element = "BUNDLE"
            excludes = listOf(
                "io.github.ngirchev.fsm.diagram.*",
                "io.github.ngirchev.fsm.exception.*"
            )
            limit {
                minimum = "0.69".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            excludes = listOf(
                "*.diagram.*",
                "*.exception.*"
            )
            limit {
                counter = "BRANCH"
                minimum = "0.69".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

// Kotlin DSL
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "11"
}
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "11"
}

// Release plugin configuration
release {
    versionPropertyFile = "gradle.properties"
    versionProperties = listOf("version")
    tagTemplate = "v\${version}"
    git {
        requireBranch.set("master|main|release/.*")
        pushToRemote.set("origin")
    }
}

// Task to create GitHub release
tasks.register<Exec>("githubRelease") {
    group = "publishing"
    description = "Creates GitHub release with CHANGELOG and uploads artifacts"
    
    // Get the latest git tag (created by release plugin) and use it for GitHub release
    commandLine("sh", "-c", 
        "RELEASE_TAG=\$(git describe --tags --abbrev=0) && " +
        "gh release create \$RELEASE_TAG -F CHANGELOG.md && " +
        "gh release upload \$RELEASE_TAG build/libs/*.jar build/libs/*.jar.asc --clobber"
    )
}