plugins {
    // 開発者の端末に Java 21 が無い場合に、ツールチェーンをダウンロードできるようにする
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend"
