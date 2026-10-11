# J.A.R.V.I.S. 10.0.0

J.A.R.V.I.S. now includes a bundled offline knowledge pack and an internal restricted sandbox alongside its Android assistant, conversation history, Google login flow, and local command interpreter.

## Embedded knowledge

The APK bundles `app/src/main/assets/jarvis_knowledge.json` with 2,145 indexed records at build time. It contains curated reference cards across physics, mathematics, biology, chemistry, astronomy, geography, history, computing, security, and AI, plus precomputed multiplication (1–50 × 1–30), addition (0–20), and square (1–100) facts. Searches work offline; unsupported or ambiguous queries may still require an explicit Web search. This collection is a reference index, not a generative LLM, and individual cards should not be treated as exhaustive or current for fast-changing subjects.

## Internal sandbox

`JarvisInternalSandbox` is an internal module, not a screen in the user interface. Its restricted command interface supports:

- `knowledge.search <query>`
- `math.eval <expression>`
- `apps list`
- `memory status`
- `system status`
- `plan.open <app name>`
- `plan.web <query>`

It is not an unrestricted shell: it never invokes bash, `ProcessBuilder`, `Runtime.exec`, root access, direct file-system commands, or arbitrary network commands. Math inputs are parsed with length, recursion-depth and numeric bounds. Plans report intended effects without executing them. This provides an internal terminal-like planning and inspection layer without secret access to other apps, passwords, files, or credentials.

## Google identity backend

Supabase project `JARVIS-Auth` is provisioned in São Paulo. The `jarvis-auth` Edge Function is deployed, and the database tables and atomic first-owner claim migration are applied. To enable real Google sign-in, a Web OAuth Client ID still must be created in Google Cloud and configured as `JARVIS_GOOGLE_WEB_CLIENT_ID` in GitHub Actions Variables and `GOOGLE_WEB_CLIENT_ID` in Supabase Edge Function Secrets. Do not place service-role keys in the Android app. Until that OAuth configuration is done, Google sign-in will display a setup error rather than pretending to authenticate.

## Build

GitHub Actions runs local unit tests, assembles `com.jarvis.assistant.debug10`, validates the package/signature and uploads `jarvis-android-v10-debug-apk`. The debug APK is not a Play Store release signed with a permanent key.
