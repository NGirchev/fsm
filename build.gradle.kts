plugins {
    base
    kotlin("jvm") version "2.2.0" apply false
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
    id("net.researchgate.release") version "3.1.0"
}

group = "io.github.ngirchev"
version = project.properties["version"] as String

// The root is an aggregator; lifecycle tasks also cover all JVM modules.
tasks.named("assemble") {
    dependsOn(subprojects.map { "${it.path}:assemble" })
}
tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}
tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}
tasks.named("clean") {
    dependsOn(subprojects.map { "${it.path}:clean" })
}

release {
    versionPropertyFile = "gradle.properties"
    versionProperties = listOf("version")
    tagTemplate = "v\${version}"
    git {
        requireBranch.set("master|main|release/.*")
        pushToRemote.set("origin")
    }
}

tasks.register<Exec>("githubRelease") {
    group = "publishing"
    description = "Creates GitHub release with CHANGELOG and uploads artifacts"

    commandLine("sh", "-c",
        "RELEASE_TAG=\$(git describe --tags --abbrev=0) && " +
        "gh release create \$RELEASE_TAG -F CHANGELOG.md && " +
        "gh release upload \$RELEASE_TAG fsm/build/libs/*.jar fsm/build/libs/*.jar.asc --clobber"
    )
}
