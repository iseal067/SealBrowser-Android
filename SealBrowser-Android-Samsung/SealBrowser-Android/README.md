# Seal Browser — Android / Samsung

Native Android version of Seal Browser for Samsung Galaxy and other Android devices.

## Included
- Multiple tabs
- Restores tabs after closing/reopening the app
- English Google searches with UK localisation
- YouTube / Shorts-friendly Android WebView media settings
- Fullscreen HTML5 video support
- New-window links open as new Seal tabs
- Minimal dark blue mobile UI
- Share/copy page actions
- Android 8.0+ (API 26+)

## Fastest way to get an APK with GitHub
1. Create a new GitHub repository, e.g. `SealBrowser-Android`.
2. Extract this ZIP on your PC.
3. Upload the CONTENTS of the extracted folder to the root of the repo, including `.github`.
4. Commit.
5. Open **Actions > Build Seal Browser Android APK > Run workflow**.
6. When it is green, open the run and download the artifact **SealBrowser-Samsung-APK**.
7. Extract the artifact ZIP. Inside is `SealBrowser-Samsung.apk`.
8. Move the APK to the Samsung phone and open it. Android may ask you to allow installs from that app/source.

Unlike the iPhone build, the GitHub debug APK is already signed for testing, so Sideloadly is not needed.

## Android Studio
Open the project folder in Android Studio and use **Build > Build APK(s)**.
