<div align="center">

  <h1>Jellio TV</h1>

  <p>
    The Android TV client for Jellio: a Nuvio lookalike, backed by a
    Jellio-Plugin server (Jellyfin + Gelato) instead of any local media.
  </p>

</div>

## Get Jellio TV

Sideload the APK from the [latest release](https://github.com/NoahSKipp/Jellio-TV/releases/latest).
No Play Store listing; this is a self-hosted companion app, same distribution
model Jellio-Plugin itself already uses.

## Build from source

```bash
git clone https://github.com/NoahSKipp/Jellio-TV.git
cd Jellio-TV
./gradlew :app:assembleDebug
```

Jellio TV is built with Kotlin, Jetpack Compose, TV Material 3, and Media3,
the same real stack [NuvioTV](https://github.com/NuvioMedia/NuvioTV) uses.
Development requires a JDK and the Android SDK.

## License

GNU General Public License v3.0
