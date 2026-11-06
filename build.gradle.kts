// AGP 9 Kotlin'i kendisi derliyor (built-in Kotlin); sürümü buradan sabitliyoruz.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
