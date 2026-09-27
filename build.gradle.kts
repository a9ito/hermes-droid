// Top-level build file — plugins declared here (apply false) and applied per-module.
// No kotlin-android plugin: AGP 9.x ships built-in Kotlin support.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
