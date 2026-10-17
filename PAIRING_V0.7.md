# Katarina Android v0.7 — Pairing Tanpa Device Key Lama

Kunci `KATARINA_DEVICE_KEY` di GitHub Secrets hanya dapat **digunakan** oleh GitHub Actions. Nilainya **tidak bisa ditampilkan lagi**, dan tidak boleh disalin ke chat. **Tidak perlu membuat ulang kunci.**

## Cara menyambungkan HP
1. Instal Katarina v0.7 dan tekan **Buat kode pairing**.
2. Kirim **kode 12 karakter** yang tampil di HP ke percakapan Katarina. Kode itu bukan password dan berlaku 10 menit.
3. Administrator menyetujui kode melalui workflow [Katarina Approve Phone Pairing](https://github.com/alfaauliau-katarinaAI/capca-susun/actions/workflows/katarina-approve-phone-pairing.yml), yang menggunakan secret GitHub tanpa membacanya ke chat. Alternatifnya, administrator dapat mengubah file `.github/pairing/approval-code.txt` untuk menjalankan workflow.
4. Setelah disetujui, tekan **Selesaikan koneksi** di APK. Android mengambil **token per-perangkat yang dienkripsi ke public key RSA perangkat**, menyimpannya dengan Android Keystore, dan tidak menampilkan Device Key lama.

## Keamanan
- RSA-OAEP digunakan untuk distribusi token; private key RSA dan token tersimpan dalam Android Keystore dan tidak dikirim ke GitHub.
- Server memerlukan GitHub-secret-derived admin key untuk menyetujui pairing. Hanya pemegang claim token di HP yang bisa mengambil token terenkripsi.
- Pairing pending kedaluwarsa 10 menit. Jika habis, buat kode baru. Pairing tidak memberikan akses admin pada HP.
- APP_DEVICE_KEY yang lama dipertahankan untuk kompatibilitas backend, tetapi tidak perlu diberikan kepada pengguna.
- Endpoint token ditujukan untuk OCR dan pengiriman kode error; tidak dapat menarik pekerjaan Worker 2Captcha atau menghasilkan uang.
- Cloudflare KV menyimpan hash token perangkat, bukan token plaintext. Tidak mengirim kata sandi 2Captcha.

## Diagnostik
- Peringatan error otomatis bisa diaktifkan di HP dan tercatat sebagai agregat Cloudflare/GitHub setiap hari.
- Ini bukan kendali jarak jauh ke layar HP.
