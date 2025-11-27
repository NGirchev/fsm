import org.jetbrains.kotlin.gradle.tasks.KotlinCompile



plugins {
    kotlin("jvm") version "1.6.21"
    id("io.gitlab.arturbosch.detekt") version "1.21.0-RC2"
    id("org.jmailen.kotlinter") version "3.6.0"
    id("maven-publish")
}

group = "ru.girchev"
version = "0.2.0a-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")
    implementation("ch.qos.logback:logback-classic:1.5.19")
    testImplementation("org.mockito.kotlin:mockito-kotlin:3.2.0")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("org.junit.jupiter", "junit-jupiter-params","5.8.1")
    testImplementation(kotlin("test"))
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"]) // works for Kotlin/JVM too
            pom {
                name.set("fsm")
                description.set("Finite state machine utilities")
            }
        }
    }
    repositories {
        // enable install to ~/.m2 via task publishToMavenLocal
        mavenLocal()
        // Example of a remote repo (optional). Replace URL/creds or remove block.
        // maven {
        //     name = "myRepo"
        //     url = uri("https://your.repo.url/repository/maven-releases/")
        //     credentials {
        //         username = findProperty("repoUser") as String? ?: System.getenv("REPO_USER")
        //         password = findProperty("repoPassword") as String? ?: System.getenv("REPO_PASSWORD")
        //     }
        // }
    }
}

kotlinter {
    ignoreFailures = false
    reporters = arrayOf("html")
    experimentalRules = false
    disabledRules = arrayOf("no-wildcard-imports", "import-ordering", "indent", "final-newline", "no-multi-spaces")
}

detekt {
    config = files("$projectDir/detekt.yml") // point to your custom config defining rules to run, overwriting default behavior
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true) // observe findings in your browser with structure and code snippets
        md.required.set(true) // simple Markdown format
    }
}

tasks.test {
    useJUnitPlatform()
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