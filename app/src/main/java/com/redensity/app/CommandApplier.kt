package com.redensity.app

import android.content.Context
import android.content.Intent

/**
 * Logika bersama untuk perintah PC (size / density / profile / reset).
 * Dipakai CommandReceiver (broadcast) dan CommandActivity (am start — paling
 * andal di HyperOS/MIUI). Apply langsung, tidak lewat uji coba 15 detik.
 */
object CommandApplier {

    private fun parseLastSize(raw: String): Pair<Int, Int>? {
        val matches = Regex("""(\d+)x(\d+)""").findAll(raw).toList()
        val last = matches.lastOrNull() ?: return null
        return Pair(last.groupValues[1].toInt(), last.groupValues[2].toInt())
    }

    private fun physicalSize(service: IRedensityService): Pair<Int, Int>? {
        val raw = service.exec(arrayOf("wm", "size")) ?: return null
        val m = Regex("""Physical size:\s*(\d+)x(\d+)""").find(raw)
        return if (m != null) {
            Pair(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        } else {
            parseLastSize(raw)
        }
    }

    private fun physicalDensity(service: IRedensityService): Int {
        val raw = service.exec(arrayOf("wm", "density")) ?: return 420
        return Regex("""Physical density:\s*(\d+)""").find(raw)?.groupValues?.get(1)?.toInt()
            ?: Regex("""(\d+)""").findAll(raw).lastOrNull()?.value?.toInt()
            ?: 420
    }

    private fun evenRounded(value: Double): Int = (value / 2.0).toInt() * 2

    private fun applyWidthWithDensity(service: IRedensityService, widthPx: Int): String {
        val physical = physicalSize(service)
        val baseDensity = physicalDensity(service)
        val scale = if (physical != null && physical.first > 0) widthPx.toDouble() / physical.first else 1.0
        val sizeTarget = if (physical != null) {
            Pair(widthPx, evenRounded(physical.second * scale))
        } else {
            Pair(widthPx, widthPx * 2)
        }
        val densityTarget = (baseDensity * scale).toInt().coerceIn(120, 640)
        // Density dulu (alasan sama seperti di startTrial).
        val densityResult = service.exec(arrayOf("wm", "density", densityTarget.toString()))
        val sizeResult = service.exec(arrayOf("wm", "size", "${sizeTarget.first}x${sizeTarget.second}"))
        return "${sizeTarget.first}x${sizeTarget.second} @${densityTarget}dpi\n$sizeResult\n$densityResult"
    }

    fun handle(context: Context, intent: Intent, service: IRedensityService) {
        val actionExtra = intent.getStringExtra("action")
        val profileExtra = intent.getStringExtra("profile")
        val sizeExtra = intent.getStringExtra("size")
        val densityInt = if (intent.hasExtra("density")) intent.getIntExtra("density", -1) else -1
        val densityStr = intent.getStringExtra("density")

        // 1) reset
        if (actionExtra.equals("reset", ignoreCase = true)) {
            service.exec(arrayOf("wm", "density", "reset"))
            service.exec(arrayOf("wm", "size", "reset"))
            ShizukuGateway.toast(context, context.getString(R.string.pc_reset_done))
            return
        }

        // 2) profile (menang dari extras lain)
        if (!profileExtra.isNullOrBlank()) {
            val physical = physicalSize(service)
            val all = Profiles.loadAll(context)
            val p = all.firstOrNull { it.name.equals(profileExtra, ignoreCase = true) }
            if (p != null) {
                val height = if (physical != null) {
                    evenRounded(physical.second * (p.widthPx.toDouble() / physical.first))
                } else {
                    p.widthPx * 2
                }
                if (p.widthPx >= 1440) {
                    ShizukuGateway.toast(context, context.getString(R.string.warning_2k))
                }
                service.exec(arrayOf("wm", "density", p.density.toString()))
                service.exec(arrayOf("wm", "size", "${p.widthPx}x$height"))
                ShizukuGateway.toast(context, "Redensity PC: profil ${p.name} diterapkan")
            } else {
                ShizukuGateway.toast(context, "Redensity PC: profil \"$profileExtra\" tidak ditemukan")
            }
            return
        }

        // 3) size (+ density proporsional bila density tidak diberikan)
        if (!sizeExtra.isNullOrBlank()) {
            val m = Regex("""(\d+)x(\d+)""").find(sizeExtra)
            if (m != null) {
                val phys = physicalSize(service)
                val baseDensity = physicalDensity(service)
                val explicit = if (densityInt > 0) densityInt else densityStr?.toIntOrNull()
                val d = explicit ?: run {
                    if (phys != null) {
                        (baseDensity * (m.groupValues[1].toInt().toDouble() / phys.first)).toInt().coerceIn(120, 640)
                    } else {
                        baseDensity
                    }
                }
                // Density dulu (alasan sama seperti di startTrial).
                service.exec(arrayOf("wm", "density", d.toString()))
                val result = service.exec(arrayOf("wm", "size", "${m.groupValues[1]}x${m.groupValues[2]}"))
                ShizukuGateway.toast(context, "Redensity PC: ${m.groupValues[1]}x${m.groupValues[2]} @${d}dpi\n$result")
                return
            }
            val presetMatch = Regex("""^(\d{3,4})p$""").find(sizeExtra.trim().lowercase())
            if (presetMatch != null) {
                val result = applyWidthWithDensity(service, presetMatch.groupValues[1].toInt())
                ShizukuGateway.toast(context, "Redensity PC: $result")
                return
            }
            ShizukuGateway.toast(context, "Redensity PC: format size tidak dikenal (pakai 720x1600 atau 720p)")
            return
        }

        // 4) density saja
        val d = if (densityInt > 0) densityInt else densityStr?.toIntOrNull()
        if (d != null && d > 0) {
            service.exec(arrayOf("wm", "density", d.toString()))
            ShizukuGateway.toast(context, "Redensity PC: density $d dpi")
            return
        }

        ShizukuGateway.toast(context, "Redensity PC: tidak ada perintah dikenali")
    }
}
