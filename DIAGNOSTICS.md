# Katarina Vision Lab — Diagnostik dan Akses Uji

## Jalur debug yang aktif
1. GitHub Actions — **Android emulator smoke and diagnostics** membangun APK dan memasangnya pada emulator Android.
2. Emulator mencoba membuka `ai.katarina.visionlab/.MainActivity`, menyimpan `screenshot.png`, `window.xml`, `activity.txt` dan `logcat.txt` sebagai artifact **VisionLab-emulator-diagnostics**.
3. Asisten dengan koneksi GitHub dapat memeriksa hasil Actions dan log langsung dari chat saat diminta.

## Jalur debug di HP pengguna
- **Tes koneksi Cloudflare:** melakukan GET ke `/health`; hanya mengecek keterjangkauan endpoint. Tidak menguji AI dan tidak mengirim gambar.
- **Bagikan laporan diagnostik:** memanggil Android Share Sheet setelah disentuh; user memilih aplikasi penerima. Tidak ada upload otomatis.
- Laporan berisi: nomor versi aplikasi, level API Android, waktu, dan maksimum 80 **kode kejadian** terakhir.
- Tidak mencatat isi CAPTCHA/OCR, gambar, Client Key 2Captcha, Device Key Cloudflare, token, cookies, alamat URL, data pengguna, atau seluruh log sistem.
- Diagnostik internal hanya disimpan pada penyimpanan privat aplikasi, tanpa akses jarak jauh ke perangkat.

## Batasan akses
- Mengakses dan menguji APK pada **emulator GitHub** tidak sama dengan mengendalikan HP yang menginstal APK.
- Akses langsung ke HP memerlukan jalur remote debugging terpisah dengan persetujuan eksplisit pengguna. Aplikasi ini tidak membuka port debug, tidak mengaktifkan ADB, dan tidak membuat sambungan remote tersembunyi.
- Cloudflare belum dideploy. Tidak ada token Cloudflare yang disimpan di GitHub.

## Tindak lanjut bila error
1. Periksa tab [GitHub Actions](https://github.com/alfaauliau-katarinaAI/capca-susun/actions) — lihat build dan emulator.
2. Di aplikasi HP, tekan **Bagikan laporan diagnostik** dan pilih ChatGPT untuk mengirim laporan secara sukarela.
3. Untuk masalah Cloudflare, jalankan **Tes koneksi Cloudflare** setelah backend resmi terhubung.
