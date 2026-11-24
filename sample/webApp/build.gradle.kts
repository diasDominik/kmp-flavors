plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

kotlin {
    wasmJs {
        outputModuleName = "webApp"
        browser { commonWebpackConfig { outputFileName = "webApp.js" } }
        binaries.executable()
    }
    sourceSets {
        // Web-only flavor resources: src/wasmJsPlayMain/resources/store.css, src/wasmJsOssMain/resources/store.css.
        wasmJsMain.dependencies { implementation(project(":shared")) }
    }
}
