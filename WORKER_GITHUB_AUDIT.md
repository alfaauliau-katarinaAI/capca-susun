# Audit Referensi Worker 2Captcha — 2026-10-17

## Hasil verifikasi repositori
1. **Resmi**: https://github.com/2captcha/rucaptcha-bot — repository milik organisasi GitHub `2captcha` yang terverifikasi. Git tree branch `master` hanya berisi `README.md`, `updates.json`, dan `example/updates.json`. Tidak ada source code Worker API, engine penerimaan tugas, atau kode Android bot dalam Git tree publik tersebut.
2. **Pihak ketiga**: https://github.com/icemellow-me/2captcha-worker — `worker.py` berisi metode autentikasi Worker, penerimaan tugas, pemrosesan CAPTCHA, pengiriman jawaban dan skip. Header `worker.py` secara eksplisit menyatakan bahwa API cabinet Worker di-*reverse-engineer* dari JavaScript web 2Captcha. Ini bukan API resmi yang didokumentasikan 2Captcha.
3. **API pelanggan**: https://2captcha.com/api-docs/quick-start — `createTask` dan `getTaskResult` mengirim permintaan CAPTCHA milik pelanggan dan membaca hasil, bukan proses Worker berbayar menerima tugas.
4. **Android Worker resmi**: https://2captcha.com/support/workers-software/rucaptcha-bot — menggunakan Client Key atau QR dari dashboard.
5. **Aturan layanan**: https://2captcha.com/terms-of-service — penggunaan/modifikasi/reproduksi bagian klien API membutuhkan persetujuan eksplisit dari 2Captcha.

## Feasibility: Worker task → Cloudflare AI
Secara konseptual, alur `Worker receive image → Cloudflare Workers AI inference → answer validation → Worker submit` **memungkinkan**, tetapi hanya komponen Cloudflare AI image OCR yang sudah diuji di infrastruktur Katarina. Google reCAPTCHA v2/Turnstile tidak setara dengan OCR satu gambar dan dapat membutuhkan browser state, token, serta alur tambahan.

Masih belum ada bukti end-to-end bahwa sebuah tugas Worker nyata diterima melalui jalur yang diizinkan, dijawab dengan benar, lalu saldo Worker di akun resmi bertambah. Penghitungan reward dalam kode pihak ketiga bukan bukti saldo resmi.

## Gerbang sebelum deployment nyata
- [x] Repo resmi dan repo pihak ketiga diperiksa.
- [x] Struktur teknis menerima dan mengirim tugas pihak ketiga dikonfirmasi melalui source `worker.py`.
- [x] Cloudflare Vision API Katarina sebelumnya lulus live smoke image (bukan tes akurasi Worker).
- [ ] Dapatkan otorisasi 2Captcha untuk implementasi/penggunaan API Worker pihak ketiga atau dokumentasi API Worker resmi.
- [ ] Tentukan bentuk credential Worker yang didukung; jangan bagikan Client Key, password, thash, atau sesi akun di GitHub/chat.
- [ ] Uji satu tugas nyata, termasuk accepted/rejected/timeout/rate limit dan verifikasi kredit Worker **di layanan resmi**.
- [ ] Uji kelayakan penjadwalan Cloudflare (Workers bukan daemon 24/7 permanen) dan biaya model AI dibanding kredit tugas.
- [ ] Baru lakukan build APK Worker yang siap uji dan kirim ke pengguna. Jangan klaim penghasilan sebelum diverifikasi.

## Keputusan release
**Belum boleh menganggap proyek Worker otomatis siap diuji pada akun asli.** Dapat mempelajari desain teknis repo pihak ketiga; tidak boleh menganggap reverse-engineering dan lisensi source code otomatis memberi izin menggunakan endpoint privat atau bertindak sebagai klien resmi 2Captcha.

Tidak ada credential yang diubah, tidak ada tugas dibayar yang dikirim, dan tidak ada workflow tambahan.
