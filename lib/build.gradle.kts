import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvm()

    js {
        browser()
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    macosX64()
    macosArm64()

    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()

    linuxX64()
    linuxArm64()
    mingwX64()

    android {
        namespace = "cn.enaium.axios"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        linuxMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        mingwMain.dependencies {
            implementation(libs.ktor.client.winhttp)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

group = "cn.enaium.axios"
version = "1.0.0"

mavenPublishing {
    coordinates(project.group.toString(), rootProject.name, project.version.toString())

    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("Axios KMP")
        description.set("A Kotlin Multiplatform port of axios with Ktor HttpClient as the default adapter.")
        inceptionYear.set("2026")
        url.set("https://github.com/Enaium/axios-kmp")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("enaium")
                name.set("Enaium")
                url.set("https://github.com/Enaium")
            }
        }

        scm {
            url.set("https://github.com/Enaium/axios-kmp")
            connection.set("scm:git:git://github.com/Enaium/axios-kmp.git")
            developerConnection.set("scm:git:ssh://git@github.com/Enaium/axios-kmp.git")
        }
    }
}
