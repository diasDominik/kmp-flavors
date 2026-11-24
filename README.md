# kmp-flavors

Android-style **flavor dimensions, product flavors and build types** for Kotlin Multiplatform, in any project.
Declare flavors once in `settings.gradle.kts`; every module (KMP, Kotlin/JVM, Android app/library) picks them up.

Requires Gradle 9.x, Kotlin Gradle plugin 2.x, AGP 9.x (only for Android modules), JDK 17+.

**[`sample/`](sample/README.md)** is an app for Android, iOS, desktop and web with a `play` flavor that uses closed-source Google ML Kit and an `oss` flavor that uses open-source zxing-cpp instead.

## Setup

```kotlin
// settings.gradle.kts
pluginManagement { includeBuild("../kmp-flavors") } // or a published version

plugins { id("uk.dominikdias.kmp-flavors.settings") }

kmpFlavors {
    flavorDimensions("tier", "store")
    productFlavors {
        register("free") { dimension = "tier"; isDefault = true }
        register("paid") { dimension = "tier"; applicationIdSuffix = ".paid"; extras["iapKey"] = "abc" }
        register("play") { dimension = "store" }
        register("foss") { dimension = "store" }
    }
    buildTypes { // debug and release exist already
        getByName("debug") { applicationIdSuffix = ".debug" }
        register("staging") { initWith(getByName("release")); isDebuggable = true }
    }
    variantFilter { ignore = flavors["tier"] == "paid" && flavors["store"] == "foss" }
}
```

The project plugin is applied to every project automatically (`autoApply = false` to opt out and apply
`id("uk.dominikdias.kmp-flavors")` yourself).

## What you get

| Android | kmp-flavors |
|---|---|
| `flavorDimensions`, `productFlavors`, `buildTypes`, `isDefault`, `initWith` | same, in `settings.gradle.kts` |
| `src/free/`, `src/freeDebug/`, `src/debug/` | `src/commonFreeMain/`, `src/iosPaidMain/`, `src/jvmDebugMain/`, `src/commonFreePlayReleaseMain/`, `src/commonFreeTest/` … for **every** KMP source set (Kotlin/JVM modules: `src/free/`, `src/testFree/`) |
| `src/free/res`, `src/debug/assets` overriding `main` | Compose `src/commonFreeMain/composeResources/`, Android `src/androidFreeMain/res/` and `…/assets/` in `com.android.kotlin.multiplatform.library` modules, see [Resources](#resources) |
| `freeImplementation(…)` | `"commonFreeMainImplementation"(…)`, `"iosDebugMainApi"(…)`, `"jvmPaidMainCompileOnly"(…)`, `"commonFreeTestImplementation"(…)` (Kotlin/JVM modules: `"freeImplementation"(…)`) |
| `buildConfigField` on default config / flavor / build type | `kmpFlavors { buildConfig { … } }` → a `const val` object in `commonMain` |
| `BuildConfig.DEBUG`, `FLAVOR`, `BUILD_TYPE` | `DEBUG`, `FLAVOR`, `BUILD_TYPE`, `VARIANT`, `FLAVOR_<DIMENSION>` |
| `variantFilter` / `beforeVariants { enable = false }` | `variantFilter { ignore = … }` |
| `applicationId(Suffix)`, `versionNameSuffix`, `versionCode/Name`, `matchingFallbacks`, `manifestPlaceholders`, `isMinifyEnabled` | declared once, copied onto `com.android.application` / `com.android.library` modules |

Source-set priority follows Android: variant > build type > multi-flavor > flavor (earlier dimension first) > main.
Like Android, Kotlin classes cannot be overridden between them; declare a function or class in each flavor
folder instead, the same way `expect`/`actual` code is split per platform.

### Resources

Resource folders follow the source-set naming above and override like Android: variant > build type >
multi-flavor > flavor (earlier dimension first) > main.

- **Compose Multiplatform** (`org.jetbrains.compose`): `src/<sourceSet with component>/composeResources/`, e.g.
  `src/commonDevMain/composeResources/values/strings.xml` or `src/iosProdMain/composeResources/drawable/logo.png`.
  A value (`string`, `plurals`, `string-array`) replaces the one with the same name in the same `values*` folder,
  leaving the rest of the lower file in place; drawables and fonts replace by name whatever the extension; `files/`
  by path. For each source set with flavor resources the merged result is handed to `compose.resources.customDirectory`,
  so don't call `customDirectory` for that source set yourself.
- **Android `res/` and `assets/`** in `com.android.kotlin.multiplatform.library` modules: `src/androidDevMain/res/`,
  `src/androidDebugMain/assets/`, … Values override by type and name, files by name (`icon.9.png` replaces `icon.png`),
  assets by path. Java resources (`src/androidDevMain/resources/`) are added too.
- **Android manifests** in `com.android.kotlin.multiplatform.library` modules: `src/androidDevMain/AndroidManifest.xml`,
  `src/androidDebugMain/AndroidManifest.xml`, … That plugin ignores manifest overlays, so kmp-flavors transforms its
  merged manifest with Android's own manifest merger (the `manifest-merger` release matching your AGP, resolved from
  `google()`), with the usual priorities and `tools:` rules. `${applicationId}` and other placeholders are left for
  the app, as for any library. `com.android.application` /
  `com.android.library` modules use Android's own flavor folders (`src/dev/res`) through the copied flavors.

### BuildConfig

```kotlin
// any module
kmpFlavors {
    buildConfig {
        packageName = "com.example.core" // required; nothing is generated without it
        className = "BuildConfig"         // default
        internalVisibility = false        // default
        buildConfigField("BASE_URL", "https://api.example.com")
        buildConfigField("API_KEY", providers.environmentVariable("API_KEY"))
        buildConfigField("List<String>", "HOSTS", "listOf(\"a\", \"b\")") // raw Kotlin, generates a non-const val
        productFlavor("paid") { buildConfigField("ADS", false) }
        buildType("debug") { buildConfigField("BASE_URL", "https://dev.example.com") }
        variant("paidRelease") { buildConfigField("BILLING_ENV", "prod") }
    }
}
```

Generated into `build/generated/kmpFlavors/buildConfig`, also during IDE sync (`prepareKotlinIdeaImport`).

### Build logic

```kotlin
if (kmpFlavors.isActive("paid")) { /* … */ }
kmpFlavors.variant.flavor("store")   // "play"
kmpFlavors.variant.isDebuggable
kmpFlavors.extras["iapKey"]          // merged extras of the active flavors/build type
```

## Choosing the variant

Kotlin Multiplatform compiles each target once per build, so **one variant is active per Gradle invocation**.
Highest priority first:

1. `-Pkmp.variant=paidPlayRelease` (or env `KMP_VARIANT`)
2. `-Pkmp.flavor.<dimension>=paid` / `-Pkmp.buildType=release` (or env `KMP_FLAVOR_<DIMENSION>`, `KMP_BUILD_TYPE`)
3. Requested task names that contain a full variant name: `:androidApp:assemblePaidPlayRelease`,
   `installFreeFossDebug`, `testPaidPlayDebugUnitTest`. This is what makes Android Studio's *Run* use the right variant.
4. Inside Xcode: the `CONFIGURATION` build setting (`Debug`/`Release`) picks the build type with that name.
5. The IDE selection: `kmp.variant=…` in `local.properties`, written by the [IDE plugin](#ide-plugin) (or by hand).
   Unknown names are ignored with a warning.
6. Defaults: `isDefault`, else `debug` / alphabetically first (as in Android). If the defaults hit an ignored
   variant, the closest enabled one is used.

`./gradlew kmpVariants` lists all variants and marks the active one.

### iOS

Add a user-defined build setting `KMP_VARIANT` (or `KMP_FLAVOR_TIER`, …) per Xcode configuration/scheme.
Xcode exports build settings as environment variables to the Gradle run-script phase, so
`embedAndSignAppleFrameworkForXcode` builds the matching variant with no script changes. Debug and Release
already map onto the `debug`/`release` build types through `CONFIGURATION`.

### Android

Android modules get the flavors and build types copied into `android { }`, so Android Studio's Build Variants
panel, `assemblePaidRelease`, `applicationIdSuffix` and so on work as usual, and you can configure more on those
flavors in `android { productFlavors { named("paid") { … } } }`.
Because multiplatform modules are compiled for the active variant only, building another Android variant in the
same invocation (`assemble`, `assembleRelease` with flavors) fails in `pre<Variant>Build` with instructions.
Turn this off with `enforceActiveVariantOnAndroid = false` in settings.

## IDE plugin

`ide-plugin/` is an IntelliJ Platform plugin for Android Studio and IntelliJ IDEA 2026.2+:

- **Variant switcher** in the status bar and under *Tools | Select KMP Variant…*: writes `kmp.variant` to
  `local.properties` and syncs Gradle (Android Studio's own sync, or IntelliJ's Gradle reload).
- **Project view**: flavor folders the active variant doesn't use (`src/commonPaidMain`, `src/release`, …) are greyed
  out with "not in freePlayDebug".
- **Editor banner** on files in those folders, which the IDE doesn't analyse, with *Switch to paidPlayDebug*: the
  closest variant that uses the folder.

The Gradle plugin writes the model the IDE reads to `.gradle/kmp-flavors/model.properties` on every sync, so the
plugin needs no configuration. Build and install from disk:

```shell
./gradlew -p ide-plugin buildPlugin   # build/distributions/kmp-flavors-ide-*.zip
# compile against an installed IDE instead of downloading one:
./gradlew -p ide-plugin buildPlugin verifyPlugin "-Pide.localPath=/Applications/Android Studio.app"
./gradlew -p ide-plugin runIde "-Pide.localPath=/Applications/Android Studio.app"   # sandbox IDE with the plugin
```

Android Studio's Build Variants panel stays separate: it picks the Android app variant, and running it
builds that variant (task names win over the IDE selection), but the multiplatform code the editor analyses
follows the kmp-flavors variant.

## Limitations

- One variant per build: CI builds each variant in its own Gradle invocation (`-Pkmp.variant=…`).
- Switching the variant in the IDE means a Gradle sync, as with Android's Build Variants for KMP source sets.
- That plugin also puts every extra `res` folder in the same layer as `androidMain` (duplicates are errors, not
  overrides) and ranks generated folders below it. So when the active variant has flavor `res`/`assets`, kmp-flavors
  takes over `androidMain`'s Android resources: they are merged with the flavor layers into
  `build/generated/kmpFlavors/android/…`. Builds are unaffected, but the IDE may then show the generated folder
  rather than `src/androidMain/res` as the resource root.

## How it loads

The settings plugin lives in the settings classloader, which cannot see the Kotlin, Android or Compose Gradle
plugins that modules apply. Code that uses their APIs (package `integration`) is therefore loaded per module
through a small bridge classloader that combines this plugin with the classloaders of the plugins the module
applied, so no extra setup (`apply false` in settings, `buildscript` classpath) is needed.

## License

Copyright 2026 Dominik Dias. Licensed under the [Apache License, Version 2.0](LICENSE).
