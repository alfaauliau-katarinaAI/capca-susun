# Automatic anonymous error reporting — Katarina v0.6

**Flow:** Android opt-in → Cloudflare `POST /v1/telemetry` → 7-day KV store → authenticated `GET /v1/diagnostics-summary` → GitHub Actions daily/manual summary.

- The Android user can enable/disable error reporting with a clearly labeled checkbox (default OFF for privacy).
- Reports contain ONLY diagnostic error codes, Android API level and app version. No images, OCR content, login credentials, device or network identifiers, stack traces, or personally identifying data.
- Errors survive app restart offline and are retried when the app opens and the configured Device Key is available. No Android foreground service or continuous surveillance.
- Endpoint requires X-Katarina-Key. KV keys expire after 7 days. Admin summary requires a separate derived admin secret never embedded in APK.
- `Cloudflare Diagnostic Summary` runs once daily or on-demand. GitHub Actions log shows only totals and error-code counts, enabling the project manager to inspect them from ChatGPT via GitHub connector without direct phone access.
- Cloudflare deployment uses a one-time idempotent namespace provisioning step; token must have **Workers KV Storage Write** in addition to Workers deployment permissions.
- The Worker has no 2Captcha Worker API or automatic paid CAPTCHA task integration.
- Real Android install and connectivity must be tested by the user. Real Cloudflare AI image inference remains separately unverified and requires permitted Meta model access.

Security note: the diagnostic data is not accessible via unauthenticated URLs; raw KV values are not published to GitHub.
