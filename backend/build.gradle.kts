plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.example.app.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.javalin:javalin:6.7.0")
    implementation("io.javalin.community.ssl:ssl-plugin:6.7.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    implementation("ch.qos.logback:logback-classic:1.5.38")
    implementation("org.flywaydb:flyway-core:13.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:13.10.0")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.jdbi:jdbi3-core:3.55.0")
    implementation("org.jdbi:jdbi3-postgres:3.55.0")
    runtimeOnly("org.postgresql:postgresql:42.7.14")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.javalin:javalin-testtools:6.7.0")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
