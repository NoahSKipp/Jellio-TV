<div align="center">

  <h1>Jellio TV</h1>

  <p>
    The Android TV client for Jellio: a Nuvio lookalike, backed by a
    Jellio-Plugin server (Jellyfin + Gelato) instead of any local media.
  </p>

</div>

## Get Jellio TV

Jellio TV isn't in the Play Store or Amazon Appstore. You install the APK from
the [latest release](https://github.com/NoahSKipp/Jellio-TV/releases/latest)
with the free **Downloader** app by AFTVnews. The steps are the same on a Fire
TV Stick and on Google TV or Android TV; only the settings menus differ.

You also need a Jellyfin server running the
[Jellio plugin](https://github.com/NoahSKipp/Jellio).

### 1. Install Downloader

- **Fire TV:** search for "Downloader" on the home screen and install the one
  by AFTVnews (orange icon).
- **Google TV / Android TV:** open the Google Play Store, search for
  "Downloader" and install the one by AFTVnews.

### 2. Allow Downloader to install apps

**Fire TV**

1. Open **Settings > My Fire TV > Developer options**.
   If there's no Developer options entry, go to **Settings > My Fire TV > About**,
   select your device name 7 times until it says you're a developer, then go back.
2. Open **Install unknown apps** and turn **Downloader** on.

**Google TV / Android TV**

1. Open **Settings > System > About** and select **Android TV OS build** 7 times
   until it says you're a developer. Some devices skip this step.
2. Open **Settings > Apps > Security & restrictions > Unknown sources** (on some
   devices **Settings > Apps > Special app access > Install unknown apps**) and
   turn **Downloader** on.

### 3. Download and install Jellio TV

1. Open Downloader and allow it access to storage if asked.
2. In the address box, type
   `github.com/NoahSKipp/Jellio-TV/releases/latest` and select **Go**.
3. The release page opens in Downloader's browser. Scroll down to **Assets** and
   select the file ending in `.apk` (for example `jellio-tv-0.28.2.apk`).
4. When the download finishes, select **Install**, then **Open**.
5. Downloader offers to delete the APK afterwards. Select **Delete**; it isn't
   needed any more.

### 4. Sign in

Enter your Jellyfin server address (for example `http://192.168.1.10:8096` or
`https://jellyfin.example.com`), then your Jellyfin username and password.

### Updates

Jellio TV checks for new versions by itself when it opens and every few hours
while it's running, and offers to download and install them. You can also check
from **Settings > Check for Updates**. The first time, Android asks you to allow
Jellio TV to install apps: turn it on the same way you did for Downloader, then
select **Install** again. You don't need Downloader for updates.

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
