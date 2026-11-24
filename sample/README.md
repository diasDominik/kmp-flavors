# kmp-flavors sample

A Compose Multiplatform app for **Android, iOS, desktop and web** with one flavor dimension, `store`, in
[`settings.gradle.kts`](settings.gradle.kts):

- `play` (default): may use closed-source Google SDKs. On Android it scans QR codes **and text** with Google ML Kit.
- `oss`: open-source dependencies only. On Android it scans QR codes with zxing-cpp; no text recognition.

Build types are Android's `debug` and `release`, so the variants are `playDebug`, `playRelease`, `ossDebug` and
`ossRelease`.

## Files

| | |
|---|---|
| `shared/src/commonMain/…/App.kt` | the whole UI, common code: store name, active variant, a sample image and a *Scan this image* button |
| `shared/src/commonMain/…/Scanner.kt` | `interface Scanner` and `expect fun platformScanner()` |
| `shared/src/commonPlayMain/…/Store.kt`, `commonOssMain/…/Store.kt` | common code per store: name, text, colour |
| `shared/src/androidPlayMain/…/Scanner.android.kt` | ML Kit scanner (`actual` for play) |
| `shared/src/androidOssMain/…/Scanner.android.kt` | zxing-cpp scanner (`actual` for oss) |
| `shared/src/iosMain/…/Scanner.ios.kt` | zxing-cpp on iOS (open source, so every variant) |
| `shared/src/desktopMain`, `wasmJsMain` | no scanner |
| `shared/build.gradle.kts` | `"androidPlayMainImplementation"(ML Kit)`, `"androidOssMainImplementation"(zxing-cpp)`; `BuildConfig` with `VARIANT` / `FLAVOR_STORE` |
| `androidApp/build.gradle.kts` | no flavors declared (copied from settings); `check<Variant>NoProprietaryDependencies` fails an oss build that contains `com.google.mlkit`, `com.google.android.gms`, `com.google.firebase`, … and runs before `assembleOss*` and with `check` |
| `iosApp/Configuration/Config.xcconfig` | `KMP_FLAVOR_STORE = oss` (no Google Play on iOS); Debug/Release pick the build type |

ML Kit is only on the classpath of `play` variants, so oss code cannot even compile against it.

## Running it

Open this `sample` folder in Android Studio or IntelliJ IDEA; it uses the plugin from the parent folder
(`includeBuild("..")`). Build one platform at a time.

| Platform | Command |
|---|---|
| Android | `./gradlew :androidApp:installOssDebug` / `installPlayDebug`, or pick a variant in *Build Variants* and Run |
| Desktop | `./gradlew :desktopApp:run -Pkmp.variant=ossDebug` |
| Web | `./gradlew :webApp:wasmJsBrowserDevelopmentRun -Pkmp.flavor.store=oss` |
| iOS | open `iosApp/iosApp.xcodeproj` and Run |
| Any | `./gradlew kmpVariants` lists the variants and marks the active one |

The `play` APK is much larger (~74 MB vs ~18 MB) because ML Kit bundles its models for every ABI.

## In the IDE

With the [kmp-flavors IDE plugin](../README.md#ide-plugin) installed, the status bar shows the active variant.
Switch to `ossDebug` and the IDE syncs: `commonOssMain` and `androidOssMain` become source folders and the play
folders are greyed out. Opening a play file then shows a banner with *Switch to playDebug*.
Without the plugin, put `kmp.variant=ossDebug` in `local.properties` and sync.
