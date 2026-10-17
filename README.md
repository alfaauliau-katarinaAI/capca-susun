# Katarina Hybrid Vision Lab v0.3

**Android offline OCR + Cloudflare Workers AI (optional fallback), tanpa VPS.**

> Status: source sudah di GitHub; debug APK dibangun otomatis oleh GitHub Actions; Cloudflare Worker belum dideploy. **Bukan** bot 2Captcha Worker dan **belum menghasilkan uang**.

## Ruang lingkup
- Android memakai Google ML Kit OCR on-device (bundled).
- Foto dipilih secara eksplisit dari penyimpanan menggunakan pemilih dokumen Android.
- Tombol Local menjalankan OCR di HP; Hybrid menjalankan OCR lokal lalu cloud hanya jika hasil tampak tidak sesuai panjang yang diharapkan.
- Cloudflare Workers AI opsional untuk gambar JPEG/PNG maksimal 350 KB. Gambar tidak disimpan dalam database Worker.
- Header `X-Katarina-Key` memerlukan **Device Key khusus Cloudflare**, yang disimpan dengan Android Keystore di HP. **Jangan masukkan 2Captcha Client Key di aplikasi ini.**
- Tidak menyediakan login 2Captcha, polling tugas, pengiriman solusi atau penarikan dana.

## Build APK — GitHub Actions
1. Buka tab **Actions** pada repository ini.
2. Pilih **Build Katarina Android APK**, lalu **Run workflow** (juga berjalan pada push perubahan Android).
3. Tunggu job `build` selesai; unduh artifact **Katarina-Vision-Lab-debug-APK**.
4. Ekstrak ZIP artifact tersebut untuk memperoleh `app-debug.apk`, kemudian instal di Android.
5. Izinkan instalasi APK dari sumber terkait sesuai pengaturan HP.
6. Uji **Pilih gambar** → **Baca teks lokal**; tidak memerlukan Cloudflare.

Build memakai Java 17, Android SDK 35 dan Gradle 8.7 pada GitHub Actions. APK debug bukan rilis Play Store. Jika Action gagal, buka log build terlebih dahulu.

## Cloudflare — deployment manual, tanpa token di repo
```bash
cd cloudflare
npm test
npm install
npx wrangler login
npx wrangler secret put APP_DEVICE_KEY
npx wrangler deploy
```

- Buat `APP_DEVICE_KEY` unik minimal 24 karakter yang **tidak sama** dengan Client Key 2Captcha.
- Lisensi model Meta `@cf/meta/llama-3.2-11b-vision-instruct` mungkin memerlukan persetujuan di Cloudflare. Periksa dukungan model pada saat deploy.
- Cloudflare memberikan HTTPS URL `*.workers.dev`; masukkan URL dan Device Key ke aplikasi lalu tekan **Simpan pengaturan**.
- Cek `GET /health`, lalu tes AI lewat **Coba AI Cloudflare**.
- Batasi penggunaan melalui pengaturan Cloudflare (rate limiting, kuota AI). Jangan publish Device Key.

## Pengujian
- `cd cloudflare && npm test` — tujuh unit test menggunakan AI **mock**, bukan panggilan Workers AI berbayar.
- OCR di perangkat dan interoperabilitas model Cloudflare perlu diuji setelah APK terpasang dan Worker dideploy.
- Tidak ada bukti pembayaran/pendapatan dari kode ini.

## Struktur
- `android/`: Android Java + Google ML Kit + penyimpanan kunci aman.
- `cloudflare/`: endpoint `/health` dan `/v1/ocr` menggunakan Worker AI.
- `.github/workflows/android-debug.yml`: otomatis build APK debug.
- `.github/workflows/cloudflare-test.yml`: unit test Worker di CI.

**Privasi:** Repository ini bersifat publik. Jangan commit Client Key, cookie sesi, API token, ataupun data pelanggan.

## Debugging dari ChatGPT melalui GitHub
- Workflow **Android emulator smoke and diagnostics** menjalankan app pada emulator Android 11, memeriksa tampilan utama, memotret layar emulator dan menyimpan logcat ke artifact privat/akun GitHub. Status workflow dan log dapat diperiksa dari ChatGPT lewat koneksi GitHub.
- Aplikasi HP memiliki tombol **Tes koneksi Cloudflare** (cek HTTP `/health`) dan **Bagikan laporan diagnostik**. Laporan hanya menyimpan kode kejadian dan versi; user memilih penerima, tidak dikirim otomatis.
- ChatGPT tidak memiliki kendali real-time terhadap HP atau kemampuan melihat aplikasi sebenarnya tanpa koneksi akses remote yang dipasang dan disetujui pengguna.
- Baca [DIAGNOSTICS.md](DIAGNOSTICS.md) untuk batas keamanan dan langkah audit.

**Batas status:** Build emulator ≠ pengujian HP fisik. Tes `/health` ≠ inferensi model berhasil. Untuk deploy Cloudflare tetap perlu akses akun/izin pengguna.
