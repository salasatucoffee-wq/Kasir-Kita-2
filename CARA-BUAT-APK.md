# Cara mendapatkan APK Salasatu Kasir

1. Buat akun GitHub (gratis) lalu buat repository baru, misalnya `salasatu-kasir`.
2. Unggah SEMUA isi folder ini ke repository, termasuk folder `.github`, `www`, dan `native`.
   Di web GitHub: Add file > Upload files. Pastikan `.github/workflows/build-apk.yml` ikut terunggah.
3. Buka tab **Actions**. Workflow "Build APK" jalan otomatis (sekitar 5 menit).
   Kalau tidak jalan: pilih "Build APK" > Run workflow.
4. Setelah centang hijau, buka run tersebut, bagian **Artifacts**, unduh `salasatu-kasir-apk`.
5. Ekstrak zip-nya, kirim `app-debug.apk` ke tablet, buka, izinkan "Instal dari sumber tidak dikenal".

## Menghubungkan printer ESC/POS (CD335)
Buka aplikasi > Owner > Pengaturan > Printer struk > Pilih printer. Daftar menampilkan dua bagian.

**Lewat kabel USB**
1. Colok kabel USB printer ke tablet (pakai kabel/adaptor OTG kalau port tablet USB-C atau micro-USB). Nyalakan printer.
2. Buka daftar printer, pilih perangkat di bagian "Kabel USB".
3. Saat Android bertanya izin akses USB, pilih Izinkan dan centang "selalu" bila ada.

**Lewat Bluetooth**
1. Nyalakan printer, pasangkan di Pengaturan > Bluetooth tablet (PIN biasanya 0000 atau 1234).
2. Buka daftar printer, pilih perangkat di bagian "Bluetooth".

Setelah memilih, atur lebar kertas (58 mm atau 80 mm), lalu tekan **Tes cetak**.
- Printer harus mendukung perintah ESC/POS. Bluetooth memakai profil serial (SPP).
- Kalau struk tercetak miring atau terpotong, ganti lebar kertas.
- Kalau CD335 Anda hanya mendukung jaringan LAN/WiFi, beri tahu saya.

## Unduh laporan PDF
Owner > Laporan harian atau Rekap bulanan > Unduh PDF. Lembar bagikan Android muncul: simpan ke File/Drive atau kirim lewat WhatsApp.

Catatan:
- Ini APK debug: bisa dipasang langsung. Untuk Google Play Store perlu APK/AAB bertanda tangan.
- targetSdk sengaja 30 agar Bluetooth tidak minta izin runtime tambahan di Android 12 ke atas.
- Data tersimpan di tablet itu saja (tidak sinkron antar perangkat).

## Menarik versi terbaru dari GitHub ke Android (Termux)
Setiap kali Anda mengunggah perubahan ke GitHub, APK baru terbit otomatis di tab **Releases**.
Untuk memasangnya di tablet:
1. Pasang **Termux** dari F-Droid (bukan Play Store, versi di sana usang).
2. Di Termux: `pkg install -y curl jq` lalu salin `update-kasir.sh` ke tablet.
3. Jalankan: `bash update-kasir.sh USERNAME/salasatu-kasir`
4. Pemasang Android terbuka. Ketuk Update. Data kasir tidak hilang karena tanda tangan APK tetap sama.
Repo privat: jalankan `export GITHUB_TOKEN=ghp_xxx` dulu (token dengan izin baca Contents).
Tanpa Termux: buka halaman Releases di browser tablet dan unduh `salasatu-kasir.apk`.

Penting: file `salasatu.keystore` jangan dihapus. Kalau berganti, APK baru tidak bisa menimpa yang lama.
Pakai repo privat karena keystore ini ikut tersimpan di repo.
