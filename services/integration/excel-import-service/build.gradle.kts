// ADR-0011: Apache Camel is the integration framework for this context.
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.apache.camel.springboot:camel-spring-boot-bom:4.22.0")
        mavenBom("org.apache.camel:camel-bom:4.22.0")
    }
}

dependencies {
    implementation(project(":libs:audit-trail-lib"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation("org.apache.camel.springboot:camel-spring-boot-starter")
    implementation("org.apache.camel.springboot:camel-kafka-starter")

    // Real OOXML (.xlsx) parsing — no mocked/hand-rolled reader (US8/US9).
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4.x split MockMvc's test autoconfiguration (@AutoConfigureMockMvc) out
    // of spring-boot-starter-test into its own web-specific starter.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.apache.camel:camel-test-spring-junit5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
