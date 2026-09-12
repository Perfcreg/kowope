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
    // No spring-boot-starter-web: this adapter has no REST endpoint (pure
    // timer + Kafka publisher) — the web starter would only stand up an
    // empty, unauthenticated Tomcat for nothing (enterprise-review finding).
    // camel-http's outbound HTTP calls to Fineract don't need it. The
    // autoconfigured Jackson 3 ObjectMapper this route depends on normally
    // comes in transitively via starter-web -> starter-json, so pull the
    // JSON starter directly instead, without the servlet container.
    implementation("org.springframework.boot:spring-boot-starter-json")

    implementation("org.apache.camel.springboot:camel-spring-boot-starter")
    implementation("org.apache.camel.springboot:camel-http-starter")
    implementation("org.apache.camel.springboot:camel-kafka-starter")
    // JSON handled directly via Spring's autoconfigured Jackson 3 ObjectMapper
    // (see memo-balance's own Jackson-3-vs-camel-jackson lesson) — no camel-jackson.

    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.camel:camel-test-spring-junit5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.wiremock:wiremock-junit5:4.0.0-beta.38")
    testImplementation("org.wiremock:wiremock-jetty:4.0.0-beta.38")
    testImplementation("org.wiremock:wiremock-httpclient-apache5:4.0.0-beta.38")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
