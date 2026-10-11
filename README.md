# J.A.R.V.I.S. 9.0.0

Android personal assistant with login-first UI, local conversation history, a voice orb, and Android global-assistant integration. Local commands use a deterministic interpreter; Web search is explicit and opens in an in-app WebView.

## Live backend provisioned

- Supabase project: `JARVIS-Auth`
- Region: `sa-east-1` (São Paulo)
- Project ref: `hgcjnkxpsnrjrofvwrzp`
- Project URL: `https://hgcjnkxpsnrjrofvwrzp.supabase.co`
- Edge Function deployed: `jarvis-auth` at `https://hgcjnkxpsnrjrofvwrzp.supabase.co/functions/v1/jarvis-auth`
- Database migration applied: profile table, single global-owner row, atomic first-owner claim, and permissions that block direct access by public/anonymous roles.
- The function validates the Google ID token's signature, issuer, audience, expiration and nonce before recording a user. It has custom token validation, so Supabase gateway JWT verification is disabled for this function; requests still need a valid Google ID token and matching nonce.

## One remaining setup step: Google OAuth Client IDs

The backend is active, but real Google sign-in is intentionally not marked ready until an OAuth client ID is configured. No fake login or client-side owner assignment is used.

1. Open Google Cloud Console: https://console.cloud.google.com/auth/clients and select or create a project.
2. Configure the Google Auth Platform branding/audience/consent setup as required.
3. Create a **Web application OAuth client**. That client ID is used by the Android Credential Manager flow as `serverClientId` and by the server to validate the token audience.
4. Register an **Android OAuth client** for the exact APK being tested. Use the package name `com.jarvis.assistant.debug9` and the SHA-1 fingerprint printed in the successful GitHub Actions run's `Verify APK package and signature` step. Debug APKs signed by the hosted CI may use a different certificate on a future build; a production app should use a stable signing key.
5. In GitHub, open **Settings → Secrets and variables → Actions → Variables** and add `JARVIS_GOOGLE_WEB_CLIENT_ID` with the Web client ID.
6. In Supabase, open **Edge Functions → Secrets** for this project and add `GOOGLE_WEB_CLIENT_ID` with the same Web client ID. Supabase injects its own `SUPABASE_URL` and service-role variables; never expose a service-role key in the APK or GitHub source.
7. Re-run the GitHub Actions workflow to build an APK with the client ID.

Supabase function secrets guide: https://supabase.com/docs/guides/functions/secrets
Android Credential Manager guide: https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation

## Global owner

The first successfully verified Google account to complete sign-in after the OAuth configuration is live will claim the global owner row. The SQL uses a singleton table and atomic insert, so two first-login requests cannot both become owner. Log in with the intended owner account first. Local mode never becomes global owner.

The database tables have RLS enabled and no policies for public/anonymous access by design. The Edge Function's privileged server key is used only on the server.

## Assistant reliability

- The system-assistant voice flow shows the recognized command and asks for confirmation before execution.
- Low-confidence voice transcripts require another attempt.
- Unknown commands don't launch an arbitrary search or trigger a guessed action.
- The app-name parser separates an installed app name from an extra instruction and reports unsupported actions honestly.
- Cross-app automation requires the user to activate Android Accessibility manually and only works with controls exposed to the accessibility framework. Game automation is not universal.

The local intent classifier is not a generative LLM. Google login and global-owner assignment depend on the real OAuth/backend configuration described above.

## Build

GitHub Actions runs unit tests, assembles the debug APK, verifies the package and signature and uploads `jarvis-android-v9-debug-apk`. The debug artifact is not a Play Store release signed with a permanent production key.
