"""Verify the Android instrumentation package BEFORE spending time booting an emulator.

A Gradle assembleDebugAndroidTest success does not guarantee that the declared
androidx.test.runner.AndroidJUnitRunner is dexed into the test APK.
"""
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
apk_dir = root / "app/build/outputs/apk/androidTest"
candidates = sorted(apk_dir.rglob("*androidTest.apk"))
assert candidates, f"Missing Android instrumentation APK in {apk_dir}"

for path in candidates:
    with ZipFile(path) as apk:
        dex = [n for n in apk.namelist() if n.startswith("classes") and n.endswith(".dex")]
        assert dex, f"No classes.dex in instrumentation APK: {path}"
        assert any(
            b"androidx/test/runner/AndroidJUnitRunner" in apk.read(n)
            for n in dex
        ), f"AndroidJUnitRunner was not bundled in {path}"
    print(f"PASS bundled AndroidJUnitRunner: {path.name}, {len(dex)} dex modules")
