plugins {
    application
}

group = "com.example"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

val javalinVersion = "6.4.0"
val flywayVersion = "11.1.0"
val jdbiVersion = "3.47.0"

dependencies {
    // Web (Javalin と、HTTPS の終端に使う SSL プラグイン)
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("io.javalin.community.ssl:ssl-plugin:$javalinVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    // アプリケーションのログ
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // データベース
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.jdbi:jdbi3-core:$jdbiVersion")
    implementation("org.jdbi:jdbi3-postgres:$jdbiVersion")

    // パスワードのハッシュ (Argon2id)
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.javalin:javalin-testtools:$javalinVersion")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

application {
    mainClass.set("com.example.app.Main")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}
