import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Kotlin version bumped to be compatible with the Maven Publish plugin and Gradle 8.10
    kotlin("jvm") version "1.9.25"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    id("org.jmailen.kotlinter") version "3.6.0"
    id("maven-publish")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.34.0"
    id("net.researchgate.release") version "3.0.2"
    jacoco
}

group = "io.github.ngirchev"
version = project.properties["version"] as String

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")
    implementation("ch.qos.logback:logback-classic:1.5.19")
    testImplementation("org.mockito.kotlin:mockito-kotlin:3.2.0")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("org.junit.jupiter", "junit-jupiter-params", "5.8.1")
    testImplementation(kotlin("test"))
}

java {
    // Compile library to Java 8 bytecode while running Gradle/tests on a newer JDK (e.g. 21)
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
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
    useGpgCmd()
    // Sign all Maven publications (including the one used by vanniktech plugin for Central)
    sign(publishing.publications)
}

kotlinter {
    ignoreFailures = false
    reporters = arrayOf("html")
    experimentalRules = false
    disabledRules = arrayOf(
        "no-wildcard-imports",
        "import-ordering",
        "indent",
        "final-newline",
        "no-multi-spaces",
        "no-trailing-spaces",
        "string-template"
    )
}

// Automatic formatting before checking
tasks.named("lintKotlinMain") {
    dependsOn("formatKotlinMain")
}
tasks.named("lintKotlinTest") {
    dependsOn("formatKotlinTest")
}

detekt {
    config =
        files("$projectDir/detekt.yml") // point to your custom config defining rules to run, overwriting default behavior
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
                minimum = "0.75".toBigDecimal()
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
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

// Kotlin DSL
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "1.8"
}
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "1.8"
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