plugins {
    id("net.researchgate.release") version "3.1.0"
}

group = "io.github.ngirchev"
version = project.properties["version"] as String

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

    commandLine(
        "sh",
        "-c",
        "RELEASE_TAG=\$(git describe --tags --abbrev=0) && " +
            "gh release create \$RELEASE_TAG -F CHANGELOG.md && " +
            "gh release upload \$RELEASE_TAG fsm-core/build/libs/*.jar fsm-core/build/libs/*.jar.asc --clobber"
    )
}
