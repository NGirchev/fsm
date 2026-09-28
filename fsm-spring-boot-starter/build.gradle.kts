plugins {
    kotlin("jvm") version "2.2.0"
    `java-library`
    `maven-publish`
    id("com.vanniktech.maven.publish") version "0.34.0"
    jacoco
}

group = rootProject.group
version = rootProject.version

jacoco { toolVersion = "0.8.15" }

repositories { mavenCentral() }

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    api(project(":fsm"))
    api("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-tx")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.springframework.security:spring-security-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
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
    }
}
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}
tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "fsm-spring-boot-starter"
        pom {
            name.set("fsm-spring-boot-starter")
            description.set("Spring Boot auto-configuration and bean-backed JSON serialization for FSM")
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
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
}

// Package the same portable static editor used on GitHub Pages, without a domain build mode.
val editorDirectory = rootProject.layout.projectDirectory.dir("fsm-visual-editor")
val editorOutput = layout.buildDirectory.dir("editor")

// GUI-launched IDEs on macOS may omit Homebrew from PATH. npm also needs node on PATH.
fun Exec.editorNpm(vararg arguments: String) {
    val inheritedPath = System.getenv("PATH").orEmpty()
    val windows = System.getProperty("os.name").startsWith("Windows")
    val npmName = if (windows) "npm.cmd" else "npm"
    val nodeName = if (windows) "node.exe" else "node"
    val macDirectories = if (System.getProperty("os.name").startsWith("Mac")) {
        listOf("/opt/homebrew/bin", "/usr/local/bin")
    } else emptyList()
    val nodeDirectory = (inheritedPath.split(File.pathSeparator) + macDirectories)
        .filter { it.isNotBlank() }
        .map { File(it).absoluteFile }
        .firstOrNull { File(it, npmName).isFile && File(it, nodeName).isFile }
        ?: throw GradleException("Node.js and npm are required to build the editor. Add their bin directory to the Gradle process PATH.")
    environment("PATH", "${nodeDirectory.absolutePath}${File.pathSeparator}$inheritedPath")
    val npm = File(nodeDirectory, npmName).absolutePath
    if (windows) commandLine("cmd", "/c", npm, *arguments)
    else commandLine(npm, *arguments)
}

val installEditorDependencies by tasks.registering(Exec::class) {
    description = "Install dependencies for the universal FSM editor."
    workingDir(editorDirectory)
    doFirst { editorNpm("ci", "--no-audit", "--no-fund") }
    inputs.files(editorDirectory.file("package.json"), editorDirectory.file("package-lock.json"))
    outputs.file(editorDirectory.file("node_modules/.package-lock.json"))
}
val buildEditor by tasks.registering(Exec::class) {
    description = "Build the universal FSM editor for the starter."
    dependsOn(installEditorDependencies)
    workingDir(editorDirectory)
    doFirst {
        editorNpm("run", "build", "--", "--outDir", editorOutput.get().asFile.absolutePath, "--emptyOutDir")
    }
    inputs.dir(editorDirectory.dir("src"))
    inputs.files(editorDirectory.file("index.html"), editorDirectory.file("vite.config.ts"),
        editorDirectory.file("tsconfig.json"), editorDirectory.file("package-lock.json"))
    outputs.dir(editorOutput)
}
tasks.processResources {
    dependsOn(buildEditor)
    from(editorOutput) { into("fsm-admin/editor") }
}
