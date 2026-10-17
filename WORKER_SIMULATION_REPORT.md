# Katarina Worker E2E — simulasi dan syarat rilis

**Status:** lingkungan uji sintetis (dry-run) dapat dijalankan otomatis lewat `cloudflare/test/worker-simulator.test.mjs`. **Belum integrasi akun Worker 2Captcha**.

### Cakupan uji
- Ambil gambar sintetis, OCR lewat **mock** binding Cloudflare Workers AI, terima jawaban melalui **mock** server tugas, catat *simulatedCredits* (bukan saldo asli).
- Validasi tenggat, duplikasi tugas, jawaban tidak valid, AI gagal, kegagalan submit, input tidak didukung, akses live ditolak.
- Modul `worker-simulator.mjs` tidak memiliki domain 2Captcha, sesi Worker, API login, task polling, atau panggilan yang menggunakan akun pengguna; semua konektor diinjeksi sebagai **simulasi**.
- Cloudflare AI real smoke dari versi sebelumnya diuji terpisah; mock unit tests **bukan** bukti tasks dari platform dibayar.

### Pembatas yang belum tuntas
- Belum ada akses API Worker resmi untuk menerima/submit tugas berbayar atas nama Katarina. Jalur pihak ketiga yang ditemukan di Github menggunakan reverse-engineering dan tidak otomatis boleh digunakan (lihat `WORKER_GITHUB_AUDIT.md`).
- Login dengan GitHub untuk aplikasi Android memerlukan pendaftaran OAuth app/GitHub App secara terpisah dan credential resmi. Belum ada client ID dan authorization callback konfigurasi terverifikasi.
- Fitur login Worker, pengambilan task asli, submit dan verifikasi saldo asli tidak dapat dijalankan tanpa akses/persetujuan pihak platform.

### Rilis
Jangan sebarkan APK sebagai Worker otomatis dan jangan klaim saldo bertambah karena *simulatedCredits*. Simulasi hanyalah uji ketahanan logika. Tahap berikutnya membutuhkan jalur resmi dari platform.
