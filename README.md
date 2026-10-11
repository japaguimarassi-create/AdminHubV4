# J.A.R.V.I.S. 12.0.0

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

Supabase project `JARVIS-Auth` is provisioned in São Paulo. The `jarvis-auth` Edge Function is deployed, and the database tables and atomic first-owner claim migration are applied. A Web OAuth Client ID has been supplied and wired into the Android build as public configuration: `188208581214-dtshluh0fkclfq8mnupaf2nn35l1flrc.appsgoogleusercontent.com`. The deployed Edge Function now uses `GOOGLE_WEB_CLIENT_ID` from Supabase secrets first and falls back to the same public audience embedded in this build. Never place a Google Client Secret or Supabase service-role key in the APK or repository. Never place a Google Client Secret or Supabase service-role key in the APK or repository. The first account to complete real token validation claims the global owner row.

## Build

GitHub Actions runs local unit tests, assembles `com.jarvis.assistant.debug12`, validates the package/signature and uploads `jarvis-android-v12-debug-apk`. The debug APK is not a Play Store release signed with a permanent key.
