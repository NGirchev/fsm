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

// Reuse the standalone editor sources; only the entry mode and resource base differ.
val editorDirectory = rootProject.layout.projectDirectory.dir("fsm-visual-editor")
val editorOutput = layout.buildDirectory.dir("editor")
val installEditorDependencies by tasks.registering(Exec::class) {
    workingDir(editorDirectory)
    commandLine("npm", "ci", "--no-audit", "--no-fund")
    inputs.files(editorDirectory.file("package.json"), editorDirectory.file("package-lock.json"))
    outputs.file(editorDirectory.file("node_modules/.package-lock.json"))
}
val buildEditor by tasks.registering(Exec::class) {
    dependsOn(installEditorDependencies)
    workingDir(editorDirectory)
    commandLine("npm", "run", "build", "--", "--mode", "example", "--base", "./",
        "--outDir", editorOutput.get().asFile.absolutePath, "--emptyOutDir")
    inputs.dir(editorDirectory.dir("src"))
    inputs.files(editorDirectory.file("index.html"), editorDirectory.file("vite.config.ts"),
        editorDirectory.file("tsconfig.json"), editorDirectory.file("package-lock.json"))
    outputs.dir(editorOutput)
}
tasks.processResources {
    dependsOn(buildEditor)
    from(editorOutput) { into("static/fsm-editor") }
}
