# Tubelight (Android)

A YouTube client that opens m.youtube.com in a WebView and blocks ads.

## Build

1. Install Android Studio (Koala or newer).
2. Choose File > Open and select this folder. Let Gradle sync finish.
3. Connect an Android 8.0+ phone with USB debugging on, then press Run.
4. For an installable file: Build > Build Bundle(s) / APK(s) > Build APK(s).

## Features

- Ad blocking in three layers (network, player data, page), in `AdBlocker.kt`.
- Background play with a notification, in `PlaybackService.kt` and `MainActivity.kt`.
- Floating player (picture-in-picture).
- Sleep timer (15 to 60 minutes, or end of video).
- Speed control and loop one.
- Hide Shorts and hide comments.
- Full screen video with landscape rotation.
- Opens youtube.com and youtu.be links.

All options are in the round menu button at the bottom right of the screen.

## Keeping ad blocking working

YouTube changes its page often. If ads return:

- Update the CSS selectors and the player data keys (`adPlacements`, `playerAds`, `adSlots`) in `AdBlocker.SCRIPT`.
- Update the ad host and path lists at the top of `AdBlocker.kt`.

## Notes

- Blocking ads breaks YouTube's terms of service. YouTube can block the app, and Google Play may refuse to list it. Use it for personal installs.
- Change the package name `com.example.tubelight` and the app name before sharing it.
- This project has not been compiled or run yet. Expect to fix small build errors on the first sync.
