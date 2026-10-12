plugins {
    // 端末に Java 21 が無い場合に、ツールチェーンを自動でダウンロードする
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "backend"
