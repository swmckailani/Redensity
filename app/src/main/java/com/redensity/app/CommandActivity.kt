package com.redensity.app

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Jalur kontrol PC yang andal: `adb shell am start -n com.redensity.app/.CommandActivity --es size 720x1600`
 *
 * Activity ini tidak punya UI — langsung memproses extras lalu finish. Transparan,
 * tidak masuk recents, tidak butuh interaksi. Bekerja walau app belum pernah dibuka,
 * sejauh Shizuku aktif + izin sudah pernah diberikan.
 */
class CommandActivity : Activity() {

    companion object {
        const val ACTION = "com.redensity.app.COMMAND"
        private const val BIND_TIMEOUT_MS = 5000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent ?: run { finish(); return }
        val extras = intent.extras
        Log.d("Redensity", "CommandActivity action=${intent.action} extras=${extras?.keySet()}")

        if (Trial.load(this) != null) {
            ShizukuGateway.toast(this, getString(R.string.pc_trial_active))
            finish()
            return
        }

        Thread {
            val ok = ShizukuGateway.withService(this, BIND_TIMEOUT_MS) { service ->
                CommandApplier.handle(this, intent, service)
            }
            if (!ok) {
                ShizukuGateway.toast(this, "Redensity PC: Shizuku tidak aktif / gagal terhubung")
            }
            runOnUiThread { finish() }
        }.start()
    }
}
