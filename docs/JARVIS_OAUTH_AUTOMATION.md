# J.A.R.V.I.S. OAuth automation setup

This repository is public, so the signing keystore must never be committed into Git.

## Configure the private signing bundle

1. Download the private `JARVIS-GitHub-Signing-Bundle.b64.txt` file shared separately in this conversation.
2. Open the GitHub repository settings for `japaguimarassi-create/AdminHubV4`.
3. Open **Secrets and variables → Actions → New repository secret**.
4. Use the name `JARVIS_DEBUG_SIGNING_BUNDLE_B64`.
5. Paste the full single-line file contents as the secret value, save, and never add it to repository source.

The bundle is base64-encoded JSON containing a PKCS#12 keystore and its signing metadata. The CI workflow decodes it inside the ephemeral runner, masks its passwords, signs the debug build and discards it when the runner exits.

## Configure Google's Android OAuth client

Create an Android OAuth client in the same Google Cloud project as the existing Web OAuth client:

- Application type: Android
- Package name: `com.jarvis.assistant.debug`
- SHA-1 fingerprint: `5B:2E:28:1B:18:5E:4E:FA:50:0F:C7:18:89:5D:1B:7F:B0:71:20:38`

Keep the Web OAuth client ID in the Android build configuration. Do not replace the Web client ID with the Android client ID: `setServerClientId` and backend token audience must use the Web client ID. Google expects both a Web client and an Android client, with the latter tied to the package and signing SHA-1: https://developers.google.com/identity/sign-in/android/legacy-gsi-start?hl=en

## Trigger the automatic pipeline

Open **Actions → Build and verify J.A.R.V.I.S. APK → Run workflow** or push a commit to `main`. The pipeline runs unit tests, imports the private signing key when configured, verifies the package and certificate fingerprint, probes Supabase, writes an OAuth diagnostic report, and uploads the APK only when stable signing is enabled.

If the secret is missing, the workflow publishes a clearly named intermediate CI APK only so it can be re-signed before delivery. Do not register the intermediate runner certificate as the Android OAuth client. If sign-in still fails, the report and on-device diagnostic display the exact package/SHA-1 values to compare with Google Cloud.


## Server-issued nonce and error 16

Version 15 asks the Supabase endpoint for a single-use nonce before opening the Google account selector. The token is sent back without a client-generated nonce; the server verifies the token and atomically consumes the matching unexpired nonce. The client tries authorized accounts, then all device accounts, and finally the Google button flow. It no longer clears saved credential state during a failed sign-in.

The shipped APK uses package `com.jarvis.assistant.debug` and the stable private signing key whose SHA-1 is `5B:2E:28:1B:18:5E:4E:FA:50:0F:C7:18:89:5D:1B:7F:B0:71:20:38`. Register that exact package and SHA-1 in an **Android** OAuth client under the same Google Cloud project as the Web client. Keep the Web Client ID for serverClientId and token audience.
