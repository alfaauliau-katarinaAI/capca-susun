# Katarina Hybrid Vision Lab v0.3 — 17 Oktober 2026

- [x] Repo `alfaauliau-katarinaAI/capca-susun`, Android OCR ML Kit dan Cloudflare Workers AI.
- [x] APK Android v0.3 dibangun: https://github.com/alfaauliau-katarinaAI/capca-susun/actions/runs/39028263058
- [x] Uji startup emulator Android lulus: https://github.com/alfaauliau-katarinaAI/capca-susun/actions/runs/39028734561
- [x] Diagnosis privat di aplikasi dan screenshot/logcat di GitHub.
- [x] Ketiga GitHub repository secret diverifikasi oleh workflow, tanpa membuka nilainya.
- [x] Cloudflare Worker **berhasil dideploy**: https://github.com/alfaauliau-katarinaAI/capca-susun/actions/runs/39029907494
- [x] Device Key dipasang sebagai secret Cloudflare `APP_DEVICE_KEY`.
- [x] Endpoint: https://katarina-vision-lab.alfaaulia8.workers.dev/health -> HTTP 200.
- [ ] Workers AI image inference belum berhasil: POST /v1/ocr merespons 502 `ai_unavailable` pada synthetic image. Tes: https://github.com/alfaauliau-katarinaAI/capca-susun/actions/runs/39030020897
- [ ] Penyebab terverifikasi: Meta Llama 3.2 11B Vision model memerlukan persetujuan Meta License terlebih dahulu. Direct Cloudflare API melaporkan error code 5016 `Model Agreement`. Diagnostik: https://github.com/alfaauliau-katarinaAI/capca-susun/actions/runs/39030077684
- [ ] Pengguna perlu membaca dan menerima ketentuan Meta Llama 3.2 Vision. Workflow opt-in manual `Accept Meta Llama Vision License (manual only)` telah tersedia pada tab GitHub Actions dan tidak akan dijalankan tanpa tindakan pengguna.
- [ ] Setelah lisensi diterima, rerun workflow `Cloudflare AI Image Smoke Test` dan periksa keberhasilan OCR.
- [ ] APK Android di HP fisik belum diuji.
- [ ] Integrasi Worker 2Captcha tidak tersedia/diaktifkan, metode izin masih perlu verifikasi.
- [ ] Pendapatan terverifikasi: Rp0.

Keamanan: workflow deployment Cloudflare hanya dapat dijalankan secara manual. Tidak ada secret yang ditampilkan di source; workflow persetujuan lisensi hanya berjalan jika pengguna menandai persetujuan eksplisit.
