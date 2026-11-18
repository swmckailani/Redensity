# Kontrol Redensity Mobile dari PC

Tanpa root, lewat ADB. Semua perintah berlaku **langsung** (bukan mode uji coba 15 detik di HP).
Semua perintah butuh Shizuku berjalan di HP dan Redensity Mobile sudah diizinkan aksesnya.

> Disarankan: coba dulu `adb shell wm size` / `wm density` untuk lihat keadaan, dan `--es action reset` untuk kembali normal.

## Prasyarat
- Wireless debugging atau USB debugging aktif di HP
- Redensity Mobile sudah terinstall + Shizuku berjalan (lihat README)
- `adb` tersedia di PC

### USB
```
adb shell am start -n com.redensity.app/.CommandActivity --es size 720x1600
```

### Wireless debugging (pairing 6 digit / pairing QR lewat Redensity Desktop)
Pair dulu dengan metode apa pun yang tersedia (tidak perlu PC yang sama):
```
adb pair IP:PORT          # jika perlu, masukkan kode 6 digit
adb connect IP:PORT
adb shell am start -n com.redensity.app/.CommandActivity --es size 720x1600
```
> Dengan QR: Redensity Desktop bisa dipakai untuk pair — lihat README Redensity Desktop.

## Perintah
Jalur yang **paling andal** (berfungsi di ROM ketat seperti HyperOS):

```
adb shell am start -n com.redensity.app/.CommandActivity --es size 720x1600
adb shell am start -n com.redensity.app/.CommandActivity --es size 720p
adb shell am start -n com.redensity.app/.CommandActivity --ei density 254
adb shell am start -n com.redensity.app/.CommandActivity --es profile "Gaming 720p"
adb shell am start -n com.redensity.app/.CommandActivity --es action reset
```

Alternatif (fallback, bisa diblokir ROM tertentu): broadcast ke receiver.

| Perintah | Contoh | Arti |
|---|---|---|
| `--es size` | `--es size 720x1600` | Set resolusi langsung; density ikut diskalakan proporsional |
| `--es size` (preset) | `--es size 720p` | Preset lebar (mengikuti aspek + density proporsional) |
| `--ei density` | `--ei density 254` | Set density saja (resolusi tidak berubah) |
| `--es density` | `--es density "254"` | Sama, versi string |
| `--es profile` | `--es profile "Gaming 720p"` | Terapkan profil buatan sendiri; menang atas extras lain |
| `--es action reset` | `--es action reset` | Kembali ke resolusi & density awal |

Semua contoh `am start -n com.redensity.app/.CommandActivity ...`, atau `am broadcast -a com.redensity.app.COMMAND ...` untuk receiver (fallback).

**Prioritas eksekusi:** `action=reset` → `profile` → `size` (+ density) → `density`.

> **Profil**: tidak ada profil bawaan. Buat dulu profil di HP (tombol **SIMPAN PROFIL**), lalu pakai namanya di perintah PC.

## Catatan perilaku
- Perintah ditolak jika **uji coba 15 detik sedang aktif** di HP.
- Pengganti aman: selalu bisa `adb shell am start -n com.redensity.app/.CommandActivity --es action reset` (atau tombol **RESET RESOLUSI** di app).
- Status saat ini: `adb shell wm size` dan `adb shell wm density`.
- Receiver/activity diekspor (`android:exported="true"`) tanpa proteksi tambahan — siapa pun yang bisa menjalankan `am start`/`am broadcast` (biasanya lewat adb) bisa memicu; tidak ada port terbuka, tidak ada koneksi jaringan.
- Catatan HyperOS: implicit broadcast diblokir platform ("Background execution not allowed") — makanya `am start` dijadikan jalur utama dan CommandActivity sengaja tidak tampil di recents & transparan.
