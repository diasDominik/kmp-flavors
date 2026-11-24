plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "sample.shared"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
    }
    jvm("desktop")
    wasmJs { browser() }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
        }
        // zxing-cpp is open source, so iOS uses it in every variant.
        iosMain.dependencies { implementation(libs.zxing.cpp.native) }
    }
}

// Dependencies per store flavor: each one is only on the classpath of that flavor's variants.
dependencies {
    // Scanning on Android. Play: Google ML Kit (closed source; text recognition and barcodes).
    // OSS: zxing-cpp (Apache-2.0; barcodes). androidApp's check<Variant>NoProprietaryDependencies
    // verifies that no Google code reaches an oss build.
    "androidPlayMainImplementation"(libs.mlkit.barcode.scanning)
    "androidPlayMainImplementation"(libs.mlkit.text.recognition)
    "androidOssMainImplementation"(libs.zxing.cpp.android)
}

compose.resources {
    packageOfResClass = "sample.shared.res"
}

kmpFlavors {
    buildConfig {
        // Generates sample.shared.BuildConfig with VARIANT, FLAVOR_STORE, BUILD_TYPE and DEBUG.
        packageName = "sample.shared"
    }
}
