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
    // Spring Boot 4.x defaults to Jackson 3 (the `tools.jackson` groupId), not
    // classic Jackson 2 (`com.fasterxml.jackson`) — match what's autoconfigured.
    // Jackson 3's jackson-databind bundles java.time (JSR-310) support directly;
    // the separate jackson-datatype-jsr310 module never shipped past 3.0.0-rc2.
    api("tools.jackson.core:jackson-databind")
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("ch.qos.logback:logback-classic")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
