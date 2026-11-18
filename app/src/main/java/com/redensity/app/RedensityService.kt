package com.redensity.app

import java.util.concurrent.TimeUnit

/**
 * Remote service yang dijalankan Shizuku dengan privilege shell (uid: shell).
 * Di sinilah perintah `wm size ...` dieksekusi.
 */
class RedensityService : IRedensityService.Stub() {

    override fun exec(cmd: Array<String>): String {
        return try {
            val process = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor(10, TimeUnit.SECONDS)
            if (out.isBlank()) "(selesai, exit=${process.exitValue()})" else out.trim()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}

