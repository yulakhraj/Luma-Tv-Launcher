# Luma Home project prompt

Use this prompt to continue or recreate this particular Android TV launcher. Replace `<TV_IP>` with the TV's current address. Review device-specific changes before applying them to a different TV.

> Build and maintain **Luma Home** for a Mi TV 4I running Android TV 9. The laptop and TV are on the same network; ADB debugging is enabled. Connect with `adb connect <TV_IP>:5555` and set the app as the default HOME launcher after installing it.
>
> Make the interface remote-first and similar in feel to a modern Apple TV launcher: a full-screen mountain landscape, large rounded app cards, clear focus outlines, and no autoplay or continuous animation. The top-left header should say “Good morning”, “Good afternoon”, “Good evening”, or “Good night” according to the TV's local time, with **Patna, Bihar** below it. The top-right header should show current Patna weather and a live clock. Use fixed Patna coordinates, not IP geolocation or Android location permissions. Use a no-key weather provider and cache the last successful reading.
>
> Favorites must appear in this order: **YouTube, JioHotstar, Apple TV**. Play Store belongs only in the horizontally scrollable **All apps** row. Newly installed launchable apps should appear in All apps automatically. When focus moves between app cards, the large title and description should show the selected app's name and details. Hide the **Live TV** tile from All apps, but keep `com.android.tv` enabled because the **Inputs** button needs its input activity. Preserve AV and HDMI input functionality. Keep **Inputs**, **Wi-Fi**, and **Settings** shortcuts in the header.
>
> Do not add IMDb ratings or any feature requiring an API key. Do not display a “luma” wordmark or an Apps tab in the header.
>
> Provide a plain Java Android codebase, `build.sh`, `install.sh`, a README, and a screenshot. Keep private signing keys, built APKs, and TV-specific secrets out of Git. Do not uninstall or disable TV packages as part of launcher installation.

## Device decisions already applied

These are a record of the owner's choices, separate from the install script:

- **Default home:** Luma Home (`com.ansub.tvhome.stylish`). The TV opens Home from the remote's power/standby cycle rather than going to AV.
- **Power:** animations and automatic sleep are disabled; the TV stays awake while plugged in.
- **Removed for the current TV user:** original TV Detox Home (`com.ansub.tvhome`), Sony LIV (`com.sonyliv`), and TV Guide (`co.sensara.tv.mitv` plus `co.sensara.tv.mitv.res`).
- **Disabled:** Android TV Leanback Launcher (`com.google.android.leanbacklauncher`), Basic Android screensaver (`com.android.dreams.basic`), Android TV Backdrop (`com.google.android.backdrop`), and Mi TV Manager (`com.xiaomi.mitv.tvmanager`). Google TV Launcher (`com.google.android.tvlauncher`) was already disabled.
- **Kept:** YouTube, JioHotstar, Apple TV, Play Store, Chromecast built-in, Android TV Remote service, Android TV settings, and the TV's input/hardware services.
- **Live TV:** its tile is hidden, but `com.android.tv` remains installed and enabled. The Inputs menu lists AV and HDMI 1–3; HDMI 1 was tested successfully.

The requested UI and TV configuration were verified on the device on 19 October 2026. The installer should not replay the removal/disable list on other televisions.
