# KATARINA AI — 2Captcha Worker Release Gate
Updated: 2026-10-17

## RELEASE STATUS: BLOCKED — NOT READY FOR INSTALL / NOT A PAID WORKER

The current Android Vision Lab v0.7 is a working prototype for image OCR,
Cloudflare model access, anonymized diagnostics and device pairing. Its APK
build / emulator smoke results are **not** proof of 2Captcha Worker operation.
No third-party user should install it expecting automated paid task completion.

## What official 2Captcha documentation confirms
- Android Worker uses the official 2Captcha Bot and the Worker Client Key or QR.
  https://2captcha.com/support/workers-software/rucaptcha-bot
- Public API createTask/getTaskResult is for the *customer* submitting paid
  challenges and reading solutions, not a Worker receiving paid tasks:
  https://2captcha.com/api-docs/create-task
  https://2captcha.com/api-docs/get-task-result
- The service terms require authorization and restrict copying/recreating API
  clients without explicit consent:
  https://2captcha.com/terms-of-service
- No supported third-party Worker receive-task/submit-answer API has been
  established in the reviewed official documents. This is an external blocker,
  **not** a missing Android permission or Cloudflare secret.

## Mandatory acceptance tests before ANY claim of readiness
- [ ] Official supported Worker integration route, including third-party
      automation permission/documentation from 2Captcha, verified.
- [ ] GitHub login properly registered/configured; Android and backend complete
      an authenticated production OAuth/App approval flow.
- [ ] Worker account login verified through the authorized 2Captcha route.
- [ ] A real paid Worker task is obtained via the explicitly authorized route,
      not simulated or intercepted from another application.
- [ ] Correct output is submitted through the authorized Worker route and accepted.
- [ ] Earnings / credited Worker balance verified through the official service.
- [ ] End-to-end failure cases pass (rate limit, offline/retry, missing key,
      expired session, incorrect result, reauthentication).
- [ ] Android physical-device acceptance test passes after emulator tests.

## Completed, but NOT sufficient for Worker readiness
- [x] Android Vision Lab v0.7 APK build:
  https://github.com/alfaauliau-katarinaAI/capca-susun/actions/runs/39036752498
- [x] Cloudflare Worker v0.7 deployed:
  https://github.com/alfaauliau-katarinaAI/capca-susun/actions/runs/39036752520
- [x] Backend Worker AI synthetic image recognition previously passed on v0.6.
- [x] Privacy-preserving diagnostic reporting deployed.
- [x] v0.7 device pairing unit tests passed.

## Engineering controls
- Do not add unofficial reverse-engineered 2Captcha Worker endpoints.
- Do not confuse the 2Captcha customer billing API with Worker earnings API.
- Do not store 2Captcha passwords, Worker Client Keys or GitHub credentials
  in source files, GitHub Actions logs or app screenshots.
- Do not run 2Captcha paid-task submissions without confirmed permission,
  a supported Worker API and an explicit acceptance test scope.
- Keep GitHub Actions usage minimal: no repeated queue runs, no unnecessary
  Android releases while the external Worker integration remains blocked.

Next dependency: obtain official supported Worker integration permission/API
or adopt 2Captcha's official Worker application without claiming Katarina
independently processes paid tasks.

Historical v0.7 pairing/OCR implementation remains in repository; it is not
the requested fully automated earnings system.
