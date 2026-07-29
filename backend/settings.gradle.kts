plugins {
    // toolchain が要求する JDK がローカルに無いとき自動ダウンロードする
    // (Dockerfile / CI はイメージ・setup-java が JDK を用意するので使われない)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "cc-tasks"
