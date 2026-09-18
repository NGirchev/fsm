plugins {
    kotlin("jvm") version "2.2.0"
    `java-library`
    `maven-publish`
    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = rootProject.group
version = rootProject.version

repositories { mavenCentral() }

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    api(project(":"))
    api("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}
tasks.test { useJUnitPlatform() }

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
