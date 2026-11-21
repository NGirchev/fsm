plugins {
    java
    id("org.springframework.boot") version "4.1.3"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.3"))
    implementation(project(":fsm-spring-boot-starter"))
    implementation("io.github.ngirchev:dotenv:1.0.5")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:4.1.3"))
    annotationProcessor("org.projectlombok:lombok")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.3"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // TestRestTemplate lives in its own module and builds on RestTemplateBuilder.
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
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
