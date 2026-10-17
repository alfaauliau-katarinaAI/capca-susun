# Katarina Vision Lab — GitHub Actions policy

Repo: `alfaauliau-katarinaAI/capca-susun`

## Minimal Actions usage
- **Android build**: automatically on changes to `android/**`; can also be run manually.
- **Cloudflare unit tests**: automatically on changes to `cloudflare/**`; can also be run manually.
- **Android emulator**: manual only; expensive and not required for every edit.
- **Cloudflare live AI smoke test**: manual only; uses Cloudflare AI quota.
- **Cloudflare deployment**: manual only. Each deployment may incur requests/AI usage; do not trigger casually.
- **Meta model license**: explicit manual acceptance only if needed. No automatic acceptance.
- Removed duplicate **Diagnose Cloudflare AI Access** workflow (one-off investigation).

## Safeguards
- One concurrency group per workflow/branch. Build/tests cancel superseded runs.
- Deploy/license workflow does NOT auto-cancel, to avoid interrupting deployment or model agreement.
- Execution timeouts on all jobs; short artifact retention (APK 7d, emulator logs 3d).
- Avoid repeated manual runs while queued. Refresh the single workflow run UI rather than using repeated API polling.
- `Queued` is a GitHub Actions runner/scheduling state; not the same as HTTP 429 / secondary API rate limit.
- If GitHub returns 403/429 or a rate-limit warning, stop retries; follow the `Retry-After` header or rate-limit reset timestamp. Check connected token permissions and Actions budget independently.
- No blanket deletion of unrelated branches, commits, run history, repos, or artifacts.

## Current blockers
- The Cloudflare `/health` endpoint is online, but live Workers AI inference was last observed returning HTTP 502 `ai_unavailable`, originally due to a Meta model agreement requirement.
- Model approval success and a passing real image test are **not yet independently verified**.
- Existing queued Actions may persist after workflow cleanup. Removing a YAML file does not cancel historical queued runs.
