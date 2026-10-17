# Katarina Vision Lab v0.8 — OCR sederhana untuk uji perangkat

Scope release ini: **pembacaan teks pada gambar milik pengguna**, bukan Worker 2Captcha otomatis.

- `Jalankan demo OCR offline`: menghasilkan gambar sintetis kata TEST di dalam aplikasi dan menjalankan OCR ML Kit tanpa internet atau kunci Cloudflare.
- `Pilih gambar` untuk foto atau screenshot pengguna, `Baca teks lokal (offline)` atau `Hybrid (lokal lalu Cloudflare bila OCR tidak cocok kriteria panjang)`.
- `Salin hasil OCR` untuk digunakan secara manual.
- Cloudflare Vision tetap menjadi fallback opsional dan **memerlukan pairing perangkat / token yang disetujui**; aplikasi tidak membagikan Device Key GitHub.
- Tombol akses 2Captcha resmi tetap tersedia di bagian bawah, tetapi **tidak ada task polling / submit Worker otomatis**.
- Demo otomatis di emulator memvalidasi ML Kit benar-benar membaca TEST dan mencatat `OCR_DEMO_PASS`. Uji startup saja bukan lagi satu-satunya QA.
- Backend Cloudflare terakhir terbukti mengembalikan tepat `TEST` untuk gambar sintetis pada run https://github.com/alfaauliau-katarinaAI/capca-susun/actions/runs/39040212827.
- Ini masih **debug APK**, bukan release Google Play atau jaminan semua gambar berhasil. Uji telepon fisik tetap diperlukan.

## Bukti build dan QA (17 Oktober 2026)
- APK Android 0.8.0: [GitHub Actions build sukses](https://github.com/alfaauliau-katarinaAI/capca-susun/actions/runs/39041115339).
- Emulator AOSP Android API 30: [startup + ML Kit OCR hasil persis TEST lulus](https://github.com/alfaauliau-katarinaAI/capca-susun/actions/runs/39042115492), log `OCR_DEMO_PASS` dan `SMOKE_PASS`.
- Cloudflare AI: [OCR real gambar sintetis bertuliskan TEST menghasilkan TEST persis](https://github.com/alfaauliau-katarinaAI/capca-susun/actions/runs/39040212827).
- File APK hasil build diverifikasi isi ZIP dan berkasnya tersedia sebagai artifact GitHub.
- Android versi sebelumnya pernah memperlihatkan kegagalan pada skrip uji offscreen dan ANR sistem emulator Google; kedua masalah **pengujian lingkungan** telah ditangani dengan validasi log OCR dan emulator AOSP. QA terbaru sukses.
- OCR gambar pemilik perangkat adalah fitur siap uji. Pengambilan/submit tugas Worker 2Captcha masih belum tersedia; jangan menyebut rilis ini auto-worker penghasil uang.
- Build ini **debug-signed**; karena sertifikat debug CI dapat berubah antar-build, pembaruan dari APK debug lama mungkin memerlukan uninstall dahulu (data lokal ikut terhapus).
