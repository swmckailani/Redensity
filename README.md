# Redensity

Redensity adalah aplikasi Android **gratis dan open source** untuk **mengecilkan resolusi dan density layar** (dpi) — mis. ke 720p / 540p — **tanpa root**, lewat **Shizuku**. Cocok untuk HP lama, menghemat baterai, atau memperingan UI yang berat.

Setiap perubahan bisa dicoba dulu lewat **mode uji coba 15 detik** — kalau tidak cocok, tampilan kembali otomatis.

![Redensity](docs/screenshot-main.png)

## Fitur
- Tanpa root (lewat [Shizuku](https://github.com/RikkaApps/Shizuku), atau via ADB dari PC)
- Semua perangkat: resolusi dan aspek dibaca dari HP itu sendiri (16:9, 20:9, 4:3, tablet, dll — dengan `wm`)
- **Tanya dulu, baru terapkan + uji coba 15 detik** — ala konfirmasi resolusi di Windows: konfirmasi sebelum diterapkan, lalu dialog hitung mundur (SIMPAN / KEMBALIKAN / RESET RESOLUSI) — kembali otomatis kalau diabaikan
- Preset sekali ketuk: **360p / 480p / 540p / 720p / 1080p / 1440p** (1440p meminta konfirmasi dulu — tidak disarankan untuk gaming, bisa ngelag)
  - Density custom per preset: **360p→160, 480p→180, 540p→200, 720p→260, 1080p→400, 1440p→560**
- Slider density (120–480 dpi)
- **Profil**: profil buatan sendiri (bisa disimpan, diterapkan, dan dihapus) — tombol 1440p dan profil di atas 1080p meminta konfirmasi
- **Mode gelap** (permanen)
- **Bahasa Indonesia**
- **Kontrol dari PC via ADB**: langsung dari komputer lewat USB / wireless debugging (lihat [docs/PC-CONTROL.md](docs/PC-CONTROL.md)) — juga lewat pairing QR dengan Redensity Desktop
- Tombol **Reset Resolusi** kapan pun, baik di luar maupun di dalam uji coba

## Persyaratan
- Android 8.0+ (API 26)
- [Shizuku](https://github.com/RikkaApps/Shizuku) berjalan di HP (wireless debugging atau `adb`)
- Wireless debugging perlu diaktifkan lagi setelah HP restart (keterbatasan Android, bukan app-nya)

## Instalasi
1. Unduh `Redensity-v1.0.apk` dari [Releases](../../releases).
2. Install (izinkan "install dari sumber tidak dikenal" bila diminta).
3. Install dan jalankan **Shizuku**.
4. Buka Redensity → izinkan akses Shizuku.

## Cara pakai
1. Ketuk preset (**360p** … **1440p**) (atau geser slider density).
2. Muncul dialog konfirmasi — ketuk **TERAPKAN**. Baru setelah itu layar berubah — muncul hitung mundur 15 detik.
3. Ketuk **SIMPAN** untuk memakai, **KEMBALIKAN** untuk batal, atau **RESET RESOLUSI** untuk kembali ke setelan default custom (1080x2400 @400 dpi) (atau tunggu 15 detik - kembali sendiri).
4. **RESET RESOLUSI** bisa diketuk kapan pun — bahkan saat uji coba sedang berjalan.
5. **Profil**: ketuk untuk menerapkan, tahan lama untuk menghapus profil buatan sendiri. **SIMPAN PROFIL** menyimpan resolusi + density saat ini.

## Build dari source
```
git clone <repo ini>
cd Redensity
./gradlew assembleDebug     # atau assembleRelease (butuh release.keystore)
```
Butuh JDK 17 + Android SDK (compileSdk 35). Lihat `local.properties.example`. Jangan pernah commit `release.keystore` (sudah masuk `.gitignore`).

## Keamanan
Redensity hanya mengubah `wm size` / `wm density` — perintah yang sama dipakai ADB shell. Tidak bisa merusak HP: kembalikan kapan pun lewat app, atau restart (setelah restart mungkin perlu `wm size reset` via PC). Beberapa ROM mungkin tampil aneh pada resolusi rendah — makanya ada uji coba 15 detik.

## Lisensi
MIT — lihat [LICENSE](LICENSE).
