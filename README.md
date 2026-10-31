# TV Lock

A simple PIN lock for apps on Android TV / Google TV. Pick apps (like YouTube) from a list, and they ask for a PIN every time they are opened.

## Build the APK

1. Install Android Studio (free) on a computer.
2. Choose **File > Open** and select this `TVLock` folder. Let Gradle sync finish (it downloads what it needs and creates the Gradle wrapper).
3. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. When it finishes, click **locate**. The file is `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the TV

Easiest: the "Send Files to TV" app (on both the TV and your phone) or a USB stick with a file manager app on the TV. Or with ADB:

```
adb connect <TV-IP-address>
adb install app-debug.apk
```

(Turn on Developer options on the TV: Settings > Device Preferences > About > press Build several times. Then enable USB/network debugging. Allow "Install unknown apps" for your file manager if you use one.)

## First-time setup on the TV

1. Open **TV Lock**. Create a PIN of 4 to 6 digits, then enter it again to confirm.
2. In the list screen, press the button **Turn on the app lock service** and switch **TV Lock** on in the Accessibility list.
3. Press **Allow TV Lock to appear on top of apps** and allow it (if the TV asks).
4. Go through the list with the D-pad and press OK on every app that should need a PIN, for example YouTube. The switch turns on and the row says "Needs PIN".

The status box at the top turns green when everything is switched on.

## How it behaves

- Opening TV Lock always asks for the PIN first.
- Opening a locked app shows the PIN screen on top of it. Correct PIN opens the app. "Back to home" or the Back button goes to the home screen.
- Leaving the app and opening it again asks for the PIN again. So does standby.
- 5 wrong PINs in a row pauses entry (30 s, then longer).
- The PIN is stored as a salted hash, not as plain text.
- There is no PIN recovery. If you forget it: uninstall and reinstall TV Lock.
- Tip: also lock **Settings** in the list, so your kid cannot switch the service off.

## Limits

This is a simple lock, not unbreakable security:

- A kid who can open Settings can disable the service or uninstall TV Lock. Lock the Settings app and, if your TV allows it, restrict installs/uninstalls.
- Some TV brands add their own restrictions on accessibility services or overlays. Standard Android TV and Google TV are the target.
- Casting to the TV (e.g. from a phone) is not blocked.

## Project layout

- `PinStore.kt` PIN hash, locked-app list, wrong-attempt pause
- `PinPad.kt` the on-screen number pad (also accepts number keys on the remote)
- `MainActivity.kt` create PIN / enter PIN
- `AppListActivity.kt` app list and status
- `AppLockService.kt` detects when a locked app opens
- `LockActivity.kt` the PIN screen shown over a locked app

## Get the APK without Android Studio

Create a free GitHub account, make a new repository, and upload the contents of this folder (including the hidden `.github` folder). Open the repository's **Actions** tab, wait for "Build APK" to finish (about 3 minutes), open the run and download **TVLock-apk**. The zip inside contains `app-debug.apk`.
