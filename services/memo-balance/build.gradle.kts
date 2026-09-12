plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":libs:audit-trail-lib"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.postgresql:postgresql")
    implementation("org.springframework.kafka:spring-kafka")
    // Spring Boot 4.x moved Flyway autoconfiguration out of spring-boot-autoconfigure
    // into its own starter (mirroring the Jackson split) — flyway-core alone isn't enough.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
