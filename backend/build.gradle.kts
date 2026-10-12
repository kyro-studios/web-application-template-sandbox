plugins {
    java
    application
}

group = "com.example"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val javalinVersion = "6.7.0"
val bouncyCastleVersion = "1.86"

dependencies {
    // Web
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("io.javalin.community.ssl:ssl-plugin:$javalinVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")

    // ログ
    implementation("org.slf4j:slf4j-api:2.0.20")
    implementation("ch.qos.logback:logback-classic:1.5.38")

    // データベース
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("org.flywaydb:flyway-core:13.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:13.10.0")
    implementation("org.jdbi:jdbi3-core:3.55.0")
    implementation("org.jdbi:jdbi3-postgres:3.55.0")

    // パスワードのハッシュ (Argon2id)
    implementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion")
    constraints {
        // SSL プラグインが PEM の読み込みに使う bcpkix と bcutil を、bcprov と同じ版にそろえる (版が違うと起動時に失敗する)
        implementation("org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion")
        implementation("org.bouncycastle:bcutil-jdk18on:$bouncyCastleVersion")
    }

    // 試験
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.javalin:javalin-testtools:$javalinVersion")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    // 試験の中で一時的な自己署名の証明書を作るため
    testImplementation("org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion")
}

application {
    mainClass = "com.example.app.Main"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all"))
}

tasks.test {
    useJUnitPlatform()
}
