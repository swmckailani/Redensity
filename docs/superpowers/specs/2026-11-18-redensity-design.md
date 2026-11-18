# Redensity — Design Doc

Tanggal: 2026-11-18
Status: Approved via brainstorming (rename dari "ScreenRes" ke **Redensity**;
repo GitHub private bernama **Redensity**; repo desktop eks-ADBMOD di-rename
**RedensityDesktop**)

## Intent (kenapa)

Redensity adalah aplikasi Android **publik** (nantinya) yang dibagikan lewat
GitHub Releases (bukan Play Store). Masalahnya: menurunkan resolusi/density
layar Android butuh akses shell yang tidak diberikan ke app biasa. Solusi
tanpa root: lewat **Shizuku** dari HP, atau koneksi ADB ke PC (USB / wireless
debugging 6 digit / QR gaya ADBMOD).

App harus:
- Bisa dipakai **tanpa root**
- Berjalan di **semua HP/tablet** — resolusi dan aspek layar apa pun, tidak ada hardcode
- Aman dicoba: uji coba 15 detik; kalau tidak dikonfirmasi, tampilan kembali otomatis
- Bisa dikontrol dari PC lewat USB atau wireless debugging

## Non-goals / anti-goals

- Bukan app Play Store
- Tidak butuh root, akun, atau internet untuk fitur inti
- Tidak menyentuh pengaturan lain selain size + density
- i18n: hanya Indonesia & Inggris

## Constraint

- `minSdk 26`, `targetSdk 35`, Kotlin, tanpa AndroidX kecuali dependency Shizuku
- Tanpa root: semua perintah lewat Shizuku UserService (uid shell)
- Wireless debugging harus aktif untuk Shizuku setelah HP restart (keterbatasan Android)
- Repo: `D:\Apk` (sudah `git init`, belum ada commit); repo GitHub **private** bernama **Redensity**
- Keystore release disimpan lokal, tidak di-commit (`.gitignore`)
- Application ID / namespace: `com.redensity.app`

## Approaches (dipilih)

| Pendekatan | Untuk | Keputusan |
|---|---|---|
| A. HTTP server di app; PC kontrol via jaringan | Tanpa adb | **Ditolak**: port terbuka, perlu token/keamanan, server harus hidup |
| B. Kontrol PC lewat ADB `am broadcast` ke receiver app | USB & wireless debugging | **Dipilih** — apply **langsung** (bukan lewat trial), sesuai keputusan pemilik |
| Standalone di HP | Semua fungsi utama | Tetap ada; semua perubahan **lewat uji coba 15 detik** |

## Spesifikasi fitur

### 1. Standalone di HP (UI)
- Status Shizuku (merah/oranye/hijau) + resolusi + density saat ini
- Tombol **Refresh status** (sudah ada) — re-check + rebind service
- Tombol **720p** / **540p** — size+density proporsional
- **Slider resolusi** (lebar px): range ~480px .. resolusi fisik; tinggi proporsional; aspek diikuti otomatis (baca fisik, bukan hardcode) — berlaku untuk HP non-1600, tablet, 16:9/20:9/4:3
- **Slider density** (sudah ada): 120–480 dpi
- **Reset ke resolusi asli** — size + density reset

### 2. Uji coba 15 detik (trial) — semua jalur app
- Setiap perubahan (tombol/slider/profil) diterapkan **sementara**, lalu muncul hitung mundur 15 dtk: "Simpan? kembali otomatis"
- **Simpan** → permanen
- **Batal / waktu habis** → revert ke snapshot **pra-apply**:
  - tidak ada override sebelumnya → `wm size reset` / `wm density reset`
  - snapshot gagal dibaca → apply **dibatalkan** (tidak dilanjutkan tanpa jaring)
- **Safety net**: snapshot trial disimpan ke internal storage sebelum apply; jika app mati/HP restart di tengah hitung mundur, **saat app dibuka lagi setelan pra-uji-coba otomatis dikembalikan**
- Tidak ada countdown via notifikasi/foreground service (scope dijaga kecil)

### 3. Profil
- **Bawaan** (read-only): "720p Hemat" (720px, density proporsional), "540p Super irit" (540px, density ×0.85 proporsional)
- **Buatan pengguna**: buat/edit/hapus, bernama; simpan JSON `profiles.json` di internal storage; field: nama, widthPx, density
- Terapkan profil lewat alur trial yang sama

### 4. Kontrol PC via ADB (apply langsung)
- `BroadcastReceiver` exported, action `com.redensity.app.COMMAND`, contoh:
  - `adb shell am broadcast -a com.redensity.app.COMMAND --es size 720x1600`
  - `adb shell am broadcast -a com.redensity.app.COMMAND --ei density 254`
  - `adb shell am broadcast -a com.redensity.app.COMMAND --es profile "720p Hemat"`
  - `adb shell am broadcast -a com.redensity.app.COMMAND --es action reset`
- Semantik extras: `size` saja → density ikut skala proporsional; `density` saja → size tidak berubah; `profile` + extras lain → **profile menang**
- Apply **langsung** (tidak lewat trial) — didokumentasikan; status bisa dibaca PC lewat `adb shell wm size` / `adb shell wm density`
- Semua eksekusi tetap via Shizuku (receiver memanggil UserService yang sama); app menampilkan toast/notifikasi singkat saat perintah PC masuk
- Tidak ada proteksi token (keputusan sadar; receiver hanya memicu alur app) — dicatat di docs
- `docs/PC-CONTROL.md` (id + en): USB & wireless (ADBMOD QR pairing) — arahkan pemakai ke ADBMOD/RedensityDesktop untuk pairing QR

### 5. Tema
- Terang / Gelap — follow system + toggle manual (disimpan di SharedPreferences)

### 6. Bahasa
- Indonesia & Inggris — `values-in/strings.xml`, default English, switch manual; semua teks lewat `@string/`

### 7. Rilis & publikasi
- Ikon app sederhana
- `README.md` (id + en): syarat (Shizuku + wireless debugging/ADB), cara pakai, kontrol PC, disclaimer (trial, restore, bukan root), license MIT
- `LICENSE` MIT
- `signingConfig` release dari `local.properties`/env; keystore tidak di-repo
- Repo GitHub: **Redensity**, **private** dulu
- Urutan eksekusi: trial → slider resolusi → profil → receiver+docs → tema → i18n → ikon/README/signing/release
