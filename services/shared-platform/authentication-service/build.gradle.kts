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
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.postgresql:postgresql")
    // Spring Boot 4.x moved Flyway autoconfiguration out of spring-boot-autoconfigure
    // into its own starter (mirroring the Jackson split) — flyway-core alone isn't enough.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    // JWT signing/JWKS: already a transitive dependency of the oauth2-resource-server
    // starter above, declared explicitly since this service (unlike a resource server)
    // uses its signing/JWK-building API directly, not just its decoding API.
    implementation("com.nimbusds:nimbus-jose-jwt")
    // RFC 6238 TOTP — a real, verified implementation rather than hand-rolled HMAC
    // code, same rationale as using Apache POI/Camel instead of hand-rolled equivalents.
    implementation("dev.samstevens.totp:totp:1.7.1")

    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4.x split MockMvc's test autoconfiguration (@AutoConfigureMockMvc) out
    // of spring-boot-starter-test into its own web-specific starter.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
