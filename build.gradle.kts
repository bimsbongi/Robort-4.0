plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gradleversions)
    alias(libs.plugins.jib)
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(libs.bundles.database)
    implementation(libs.bundles.ktor)
    implementation(libs.jda)
    implementation(libs.jda.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback)

}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}