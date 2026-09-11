plugins {
    // Lets Gradle auto-provision a JDK 21 toolchain on any machine, regardless
    // of which JDK is already installed (this machine has JDK 26 on PATH).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "memo-balance-portal"

include(
    "libs:audit-trail-lib",
    "services:integration:write-off-detection-service",
    "services:integration:vision-etl-connector",
    "services:integration:icad-integration-adapter",
    "services:integration:excel-import-service",
    "services:shared-platform:authentication-service",
    "services:shared-platform:notification-service",
    "services:reference-data-config",
    "services:memo-balance",
    "services:account-verification",
    "services:clearance-orchestration",
    "services:case-engagement",
    "services:reporting",
)
