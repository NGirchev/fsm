plugins {
    java
    id("org.springframework.boot") version "3.5.16"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    implementation(project(":fsm-spring-boot-starter"))
    implementation("io.github.ngirchev:dotenv:1.0.5")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Core FSM DTOs are Kotlin classes; the example sources and tests are Java.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    annotationProcessor("org.projectlombok:lombok")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}

// Run browser tests against the example and the editor packaged by the starter.
val editorDirectory = rootProject.layout.projectDirectory.dir("fsm-visual-editor")

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

val exampleJar = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar")
tasks.register<Exec>("playwrightTest") {
    group = "verification"
    description = "Run editor browser tests against an isolated PostgreSQL and Spring application."
    dependsOn(exampleJar)
    workingDir(editorDirectory)
    // Pass the built artifact so the runner does not launch a nested Gradle build.
    doFirst {
        editorNpm("run", "test:e2e")
        environment("E2E_APP_JAR", exampleJar.get().archiveFile.get().asFile.absolutePath)
    }
}
