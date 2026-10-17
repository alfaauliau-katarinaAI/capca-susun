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
