plugins {
    kotlin("jvm") version "2.2.0"
}

repositories { mavenCentral() }

dependencies {
    implementation(files("../../fsm/build/classes/kotlin/main"))
    implementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
sourceSets {
    main {
        java.srcDir(layout.buildDirectory.dir("generated/java"))
        kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
    }
}
tasks.test { useJUnitPlatform() }
