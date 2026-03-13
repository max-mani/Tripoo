# Play Store Signing Key

## Required certificate fingerprint

Play Console expects your AAB to be signed with:
**SHA1: 85:3E:EE:4F:C8:6E:9A:69:B3:82:D5:C3:8B:7E:A8:78:39:87:A9:C9**

## Current keystore

`tripoo-release-key.jks` has SHA1: DD:C4:04... (different key). Do **not** use this for Play Store uploads.

## What to do

1. **Find the keystore** used for the first Play Store upload (the one with SHA1 85:3E:EE...).
2. Copy it to the project root and name it `upload-keystore.jks`.
3. Update `key.properties`:
   - `storeFile=upload-keystore.jks`
   - Set `storePassword` and `keyPassword` to match that keystore
   - Set `keyAlias` to the alias used in that keystore

## Check a keystore's fingerprint

```bash
keytool -list -v -keystore YOUR_KEYSTORE.jks -alias YOUR_ALIAS
```

Look for the SHA1 line. It must be `85:3E:EE:4F:C8:6E:9A:69:B3:82:D5:C3:8B:7E:A8:78:39:87:A9:C9`.

## If you lost the keystore

Contact [Google Play support](https://support.google.com/googleplay/android-developer/answer/9842756) about resetting the upload key.
