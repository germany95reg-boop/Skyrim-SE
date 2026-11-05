# Persistent Android development signing certificate

**Private test-build key only — not suitable for public production distribution.**

`skyrimse-mobile-test.jks` is pinned for all future GitHub Actions **debug** APKs. Keep it private and backed up securely. The Gradle build configuration in source snapshots uses this key. Do not regenerate this file or change the alias, keystore password, package ID, or certificate, or Android will reject upgrades.

Alias: `androiddebugkey`. SHA-256 fingerprint:
`16:B9:F1:0C:71:FE:29:0E:44:55:DF:39:B9:34:4B:9B:8B:28:24:79:F5:B0:A1:35:F3:58:49:7E:59:3B:B2:D7`.

The passphrase in this **development** configuration is the conventional Android debug password. Every repository reader with this keystore can sign a replacement APK; restrict repository access. Before any public release, migrate to a protected production key managed through GitHub secrets or a dedicated signing system, and plan the user migration.

Old v1.7 APKs were signed with temporary GitHub-hosted debug keys; moving to this certificate will require a **one-time uninstall and reinstall** if the old key cannot be retrieved. v1.8 and subsequent APKs signed with this same fixed key update in place, provided `applicationId` is unchanged and `versionCode` increases.
