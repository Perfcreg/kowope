// Root build file. Declares plugin versions once (apply false) so every
// module resolves the same Spring Boot / dependency-management version;
// each module's own build.gradle.kts applies what it needs and declares
// its own dependencies. See docs/adr/0007-backend-stack-and-repo-layout.md.

plugins {
    id("java")
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.uba.mbp"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
