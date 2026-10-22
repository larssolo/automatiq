# Google Play store assets

| File | Play Console field | Size |
|---|---|---|
| `icon-512.png` | App icon | 512×512 |
| `feature-1024x500.png` | Feature graphic | 1024×500 |
| `screenshots/01-autopilot.jpg` … `05-log.jpg` | Phone screenshots (in this order) | 1080×1920 |

The screenshots are real captures of the `play` release build 1.6 on an Android 16 emulator (Pixel 7) with fictional demo macros, framed with a caption. They show no location or device triggers, which the `play` build doesn't have.

## Videos (not in git — too large)

Recorded on the same emulator; kept locally in `~/Downloads/automatiq-store/videos/`. Upload to YouTube (unlisted) or Google Drive and paste the link where Play Console asks:

| File | Use | Length |
|---|---|---|
| `declaration-device-automation.mp4` | **SMS/Call Log declaration (use this one), `play` 1.6 (versionCode 7), 1080×1920 9:16, 30 fps:** the macro list → SMS permission in Phone setup → the user's "Out of office" trigger and reply → an incoming SMS fires the auto-reply, shown sent in Messages → the call-log/phone permission dialogs when opening "Missed call text-back" → a missed call texts the caller back (notification + Messages) → Pills / Ping / Quiet mode run other parts of the OS → Log with every run SUCCESS and both SMS "Delivered" | 54 s |
| `declaration-device-automation-v1.mp4` | Old version (no permission dialogs, no editor for the SMS macros). Not used | 43 s |
| `intro-guide.mp4` | Intro guide from the `play` 1.6 build (no location trigger). Promo / store listing video, or for sharing | 37 s |
