// Shared audit-logging contract every service depends on.
// Not a bounded context — see the "audit-trail-lib" note in CONTEXT-MAP.md
// and docs/adr/0005-centralized-observability.md.

plugins {
    id("java-library")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
    }
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
