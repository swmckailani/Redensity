package com.redensity.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Kontrol PC lewat broadcast (fallback; di beberapa ROM Xiaomi/HyperOS
 * background broadcast diblokir — jalur yang andal adalah am start ke
 * CommandActivity). Apply langsung, ditolak saat uji coba aktif.
 */
class CommandReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.redensity.app.COMMAND"
        private const val BIND_TIMEOUT_MS = 5000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        if (Trial.load(context) != null) {
            ShizukuGateway.toast(context, context.getString(R.string.pc_trial_active))
            return
        }

        val pending = goAsync()
        Thread {
            try {
                val ok = ShizukuGateway.withService(context, BIND_TIMEOUT_MS) { service ->
                    CommandApplier.handle(context, intent, service)
                }
                if (!ok) {
                    ShizukuGateway.toast(context, "Redensity PC: Shizuku tidak aktif / gagal terhubung")
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
