# J.A.R.V.I.S. 14.0.0

J.A.R.V.I.S. includes an offline reference library, a restricted internal sandbox, local command handling, and Google sign-in through Credential Manager plus a server-side Supabase token validator.

## Automated build and OAuth diagnosis

GitHub Actions runs unit tests, compiles the Android package, verifies its signature, probes the auth function, and writes an OAuth diagnostic report containing the exact package and SHA-1. APK publication is deliberately skipped until stable signing is configured, to avoid shipping a build whose Google Android OAuth registration breaks on the next CI run.

Workflow: https://github.com/japaguimarassi-create/AdminHubV4/actions/workflows/build-apk.yml

## One-time setup for stable signing

1. Download the private `JARVIS-GitHub-Signing-Bundle.b64.txt` file shared separately in this conversation. Do not commit it to this public repository.
2. In GitHub open **Settings → Secrets and variables → Actions → New repository secret**.
3. Name it `JARVIS_DEBUG_SIGNING_BUNDLE_B64` and paste the entire contents of the provided `.b64.txt` file as the secret value.
4. In the same Google Cloud project as the Web OAuth client, create an **Android OAuth client** with package `com.jarvis.assistant.debug` and SHA-1 `A1:44:4B:B5:A9:DF:7E:A2:E8:87:CC:CB:4B:B6:4C:91:E9:58:28:31`.
5. Run the GitHub Actions workflow manually or push to `main`. The workflow decodes the protected secret, uses the same keystore, verifies package/signature, probes the Supabase endpoint, and publishes the APK.

The bundle is private signing material. Keep it only in a private download and in the GitHub Actions secret; never add it to source control or an issue. It includes its own passwords, so only one secret needs to be stored. It is a development signing key and must not be reused as a Play Store production signing key.

## OAuth configuration

Web client ID (used as `setServerClientId` and token audience): `188208581214-dtshluh0fkclfq8mnupaf2nn35l1flrc.apps.googleusercontent.com`

Supabase auth endpoint: `https://hgcjnkxpsnrjrofvwrzp.supabase.co/functions/v1/jarvis-auth`

The native Google Credential Manager flow also needs an Android OAuth client registration. The Web Client ID alone is insufficient. Error `[16] Account reauth failed` now produces a diagnostic message showing the package and SHA-1 of the installed APK. The CI report shows the same information for each build.

## Embedded offline knowledge and sandbox

The APK embeds 2,145 local reference records and a restricted internal sandbox for `knowledge.search`, `math.eval`, `apps list`, `memory status`, `system status`, `plan.open` and `plan.web`. The sandbox blocks arbitrary shell execution and direct access to private files or credentials; plans do not execute side effects.

The local knowledge index is not a generative LLM. Voice input depends on the Android speech model and permissions. Cross-app automation requires manual Android Accessibility activation.


## Login repair 15.0.0

The client now obtains a one-time nonce from Supabase before requesting a Google ID token, matching the Edge Function's replay-protected protocol. Credential Manager tries previously authorized accounts, then all accounts, then the dedicated Google button flow. It does not clear saved account state during a failed attempt. Error details include the installed package and signing SHA-1.

The distributable package is `com.jarvis.assistant.debug`, signed with the stable key fingerprint `5B:2E:28:1B:18:5E:4E:FA:50:0F:C7:18:89:5D:1B:7F:B0:71:20:38`. Create an Android OAuth client for that exact package/fingerprint in the same Google Cloud project as the Web client. The Web client ID remains the ID-token audience; it is not replaced with the Android client ID.
