package com.redensity.app

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Helper bersama: menunggu binder Shizuku (asinkron setelah provider di-attach),
 * lalu mengikat UserService dan menjalankan blok aksi.
 */
object ShizukuGateway {

    fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun pingGranted(): Boolean =
        try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }

    fun waitForBinder(timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (pingGranted()) return true
            Thread.sleep(100)
        }
        return false
    }

    fun withService(context: Context, timeoutMs: Long, block: (IRedensityService) -> Unit): Boolean {
        if (!waitForBinder(timeoutMs)) return false

        val args = Shizuku.UserServiceArgs(
            ComponentName(context, RedensityService::class.java)
        ).daemon(false).processNameSuffix("service").version(1)

        var service: IRedensityService? = null
        val latch = CountDownLatch(1)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = IRedensityService.Stub.asInterface(binder)
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }

        try {
            Shizuku.bindUserService(args, connection)
        } catch (_: Exception) {
            return false
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        val svc = service ?: return false
        // Binder bisa mati di tengah panggilan (Shizuku service terbunuh) → DeadObjectException.
        // Jangan sampai exception itu mematikan proses app.
        return try {
            block(svc)
            true
        } catch (e: Exception) {
            android.util.Log.e("Redensity", "service block failed", e)
            false
        }
    }
}
