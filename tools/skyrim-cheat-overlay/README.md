# Skyrim Cheat Overlay 0.3
Standalone Android overlay for Skyrim 2011 console commands.
The original Skyrim APK, package, game files, and digital signature are not modified.

Previous v0.2 was an Android IME and could not reach SDL2-based game's native keyboard event path reliably.

New v0.3 requires Shizuku (https://shizuku.rikka.app/) to inject real Android input key events as the adb shell user. No root needed on Android 11+ with Wireless debugging, but Shizuku needs to be started on-device after each reboot.

Steps: install and start Shizuku, grant app access, allow display over other apps, start overlay, switch to Skyrim, tap floating SKY button, open the console and send test command tgm. The app has separate buttons for speed, shout cooldown, carry weight, resources, skills, unlock and psb.

APK is installable once signed by Gradle, but actual compatibility with this Skyrim port requires device testing. Shell input exit code does not prove the game interpreted its events. PSB requires double press; commands cannot read the game state. We cannot auto-undo one-shot resource and skill changes.

New application ID com.kebrio.skyrimcheats.overlay; can coexist with the previous keyboard app.
