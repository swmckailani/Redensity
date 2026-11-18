package com.redensity.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.content.res.Configuration

import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMISSION = 1001
    }

    private lateinit var statusText: TextView
    private lateinit var sizeText: TextView
    private lateinit var outputText: TextView
    private lateinit var densityValue: TextView
    private lateinit var densitySlider: SeekBar
    private lateinit var profileList: ListView

    private var profileItems: List<Profiles.Profile> = emptyList()


    /** Dialog konfirmasi gaya Windows: hitungan mundur + tombol SIMPAN/KEMBALIKAN. */
    private var trialDialog: AlertDialog? = null
    private var trialDetailLabel: String = ""

    private fun trialDialogMessage(remaining: Long): String =
        "$trialDetailLabel\n\n" + getString(R.string.trial_countdown, remaining)

    private var suppressSlider = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val trialTicker = object : Runnable {
        override fun run() {
            val snap = Trial.load(this@MainActivity) ?: return hideTrialDialog()
            val remaining = ((snap.expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            if (remaining <= 0) {
                autoRevertTrial()
                return
            }
            trialDialog?.setMessage(trialDialogMessage(remaining))
            mainHandler.postDelayed(this, 1000)
        }
    }

    private var service: IRedensityService? = null
    private var bound = false

    /** Ukuran/density fisik HP — dipakai untuk menampilkan detail di dialog konfirmasi. */
    @Volatile private var physicalSizeCache: Pair<Int, Int>? = null
    @Volatile private var physicalDensityCache: Int? = null

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.redensity.app", RedensityService::class.java.name)
    ).daemon(false).processNameSuffix("service").version(1)

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> onPermissionResult() }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.d("Redensity", "onServiceConnected: $name")
            service = IRedensityService.Stub.asInterface(binder)
            bound = true
            // Cache setelan fisik untuk dialog konfirmasi (di luar UI thread).
            Thread {
                try {
                    physicalSizeCache = physicalSize()
                    physicalDensityCache = physicalDensity()
                } catch (_: Exception) {
                }
            }.start()
            runOnUiThread {
                refreshStatus()
                refreshProfileList()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d("Redensity", "onServiceDisconnected: $name")
            service = null
            bound = false
            runOnUiThread { refreshStatus() }
        }
    }

    /**
     * Sistem tidak selalu me-recreate activity saat density/size berubah (bisa lambat
     * atau tidak pernah) — itu yang membuat tampilan raksasa/terpotong. Kita tangani sendiri:
     * begitu konfigurasi berubah, recreate sekarang juga agar langsung digambar ulang di
     * konfigurasi baru. configChanges di manifest memastikan callback ini dipanggil.
     * Tidak ada loop: setelah recreate, konfigurasinya stabil.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        sizeText = findViewById(R.id.sizeText)
        outputText = findViewById(R.id.outputText)
        densityValue = findViewById(R.id.densityValue)
        densitySlider = findViewById(R.id.densitySlider)
        profileList = findViewById(R.id.profileList)

        profileList.setOnItemClickListener { _, _, position, _ ->
            profileItems.getOrNull(position)?.let { applyProfile(it) }
        }
        profileList.setOnItemLongClickListener { _, _, position, _ ->
            val p = profileItems.getOrNull(position) ?: return@setOnItemLongClickListener true
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_delete_title))
                .setMessage(p.name)
                .setPositiveButton(getString(R.string.profile_delete)) { _, _ ->
                    Profiles.deleteUser(this, p.name)
                    refreshProfileList()
                }
                .setNegativeButton(getString(R.string.profile_cancel), null)
                .show()
            true
        }
        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener { promptSaveProfile() }

        densitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !suppressSlider) {
                    densityValue.text = getString(R.string.density_value, progress + 120)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (seekBar != null && ensurePermission()) {
                    val dpi = seekBar.progress + 120
                    Thread {
                        if (isTrialPending()) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, getString(R.string.trial_finish_first), Toast.LENGTH_SHORT).show()
                                syncDensitySlider()
                            }
                            return@Thread
                        }
                        startTrial(null, dpi, "density $dpi dpi")
                    }.start()
                }
            }
        })


        findViewById<Button>(R.id.btn360).setOnClickListener { applyWidth(360) }
        findViewById<Button>(R.id.btn480).setOnClickListener { applyWidth(480) }
        findViewById<Button>(R.id.btn540).setOnClickListener { applyWidth(540) }
        findViewById<Button>(R.id.btn720).setOnClickListener { applyWidth(720) }
        findViewById<Button>(R.id.btn1080).setOnClickListener { applyWidth(1080) }
        findViewById<Button>(R.id.btn1440).setOnClickListener { applyWidth(1440) }
        findViewById<Button>(R.id.btnReset).setOnClickListener { resetSize() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshAll() }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        // Jika app mati di tengah uji coba, kembalikan setelan pra-trial sekarang.
        restoreTrialIfPending(silent = true)
        refreshStatus()
        refreshProfileList()
    }

    override fun onResume() {
        super.onResume()
        ensureBound()
        refreshStatus()
    }

    override fun onDestroy() {
        // Dialog adalah window terpisah — tutup sebelum window activity hilang,
        // kalau tidak dismiss() melempar IllegalArgumentException "not attached to window manager".
        hideTrialDialog()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        if (bound) {
            try {
                Shizuku.unbindUserService(serviceArgs, serviceConnection, true)
            } catch (_: Exception) {
            }
            bound = false
        }
        super.onDestroy()
    }

    private fun shizukuRunning(): Boolean =
        try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }

    private fun shizukuGranted(): Boolean =
        try {
            shizukuRunning() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }

    private fun refreshAll() {
        statusText.text = getString(R.string.status_checking)
        statusText.setTextColor(getColor(R.color.text_secondary))
        ensureBound()
        refreshStatus()
        // Deteksi ulang setelah service punya waktu untuk tersambung kembali.
        densitySlider.postDelayed({ ensureBound(); refreshStatus() }, 1500)
    }

    private fun onPermissionResult() {
        ensureBound()
        refreshStatus()
    }

    private fun isTrialPending(): Boolean {
        val snap = Trial.load(this) ?: return false
        if (System.currentTimeMillis() >= snap.expiresAt) {
            // Uji coba kedaluwarsa tapi snapshotnya masih ada (proses mati paksa/gagal bersih) —
            // jangan blokir preset; biarkan RESET RESOLUSI yang membersihkan setelannya.
            Trial.clear(this)
            return false
        }
        return true
    }

    /**
     * Dialog konfirmasi ala Windows: "Keep these display settings?" dengan hitung mundur.
     * Tidak bisa ditutup sembarangan — user pilih SIMPAN / KEMBALIKAN, atau tunggu auto-revert.
     * Dipanggil ulang di onCreate bila uji coba masih pending (mis. app di-recreate).
     */
    private fun showTrialDialog(label: String) {
        val snap = Trial.load(this) ?: return
        val remaining = ((snap.expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
        trialDetailLabel = label
        val message = trialDialogMessage(remaining)
        mainHandler.removeCallbacks(trialTicker)
        try {
            trialDialog?.dismiss()
        } catch (_: IllegalArgumentException) {
            // Window lama sudah dilepas — abaikan.
        }
        trialDialog = null
        trialDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.trial_dialog_title))
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.trial_save)) { _, _ -> saveTrial() }
            .setNegativeButton(getString(R.string.trial_revert)) { _, _ -> cancelTrial(manual = true) }
            .setNeutralButton(getString(R.string.btn_reset)) { _, _ -> resetSize() }
            .show()
        mainHandler.postDelayed(trialTicker, 1000)
    }

    private fun hideTrialDialog() {
        mainHandler.removeCallbacks(trialTicker)
        val d = trialDialog
        trialDialog = null
        try {
            d?.dismiss()
        } catch (_: IllegalArgumentException) {
            // Window sudah dilepas saat activity di-recreate — tidak apa-apa.
        }
    }

    private fun autoRevertTrial() {
        Thread { performRevert(getString(R.string.trial_inactive)) }.start()
    }

    private fun cancelTrial(manual: Boolean) {
        Thread { performRevert(getString(R.string.trial_reverted_msg)) }.start()
    }

    private fun saveTrial() {
        if (Trial.load(this) == null) return
        Trial.clear(this)
        hideTrialDialog()
        Toast.makeText(this, getString(R.string.trial_saved), Toast.LENGTH_SHORT).show()
        showOutput(getString(R.string.trial_saved_msg))
        refreshStatus()
    }

    /** Dijalankan di background thread. */
    private fun startTrial(sizeTarget: Pair<Int, Int>?, densityTarget: Int?, label: String) {
        if (!ensurePermission()) return
        if (ensureServiceBlocking() == null) {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.service_not_connected), Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (Trial.load(this) != null) {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.trial_finish_first), Toast.LENGTH_SHORT).show()
            }
            return
        }
        val sizeRaw = exec(arrayOf("wm", "size"))
        val densityRaw = exec(arrayOf("wm", "density"))
        if (sizeRaw.startsWith("(Shizuku") || sizeRaw.startsWith("Error") ||
            densityRaw.startsWith("(Shizuku") || densityRaw.startsWith("Error")
        ) {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.trial_read_failed), Toast.LENGTH_LONG).show()
            }
            return
        }
        // Dimensi konkret yang sedang diterapkan — disimpan di snapshot juga,
        // supaya dialog pasca-recreate menampilkan hal yang sama.
        val detailLabel = when {
            sizeTarget != null && densityTarget != null -> "${sizeTarget.first}x${sizeTarget.second} @ $densityTarget dpi"
            sizeTarget != null -> "${sizeTarget.first}x${sizeTarget.second}"
            else -> label
        }
        // SAVE dulu — reconfiguration akibat wm apply bisa me-recreate activity
        // SEBELUM exec selesai; tanpa snapshot, dialog uji coba tidak pernah tampil.
        Trial.save(
            this,
            Trial.Snapshot(sizeRaw, densityRaw, detailLabel, System.currentTimeMillis() + 15_000)
        )
        // Density DULU lalu size: size memicu reconfigure layar yang berat;
        // kalau size duluan, density butuh beberapa detik baru tercatat.
        val densityResult = densityTarget?.let { exec(arrayOf("wm", "density", it.toString())) } ?: "(skip)"
        val sizeResult = sizeTarget?.let { exec(arrayOf("wm", "size", "${it.first}x${it.second}")) } ?: "(skip)"
        if (sizeResult.startsWith("Error") || densityResult.startsWith("Error")) {
            // Gagal terapan — rollback best effort dan batalkan uji coba.
            exec(arrayOf("wm", "density", "reset"))
            exec(arrayOf("wm", "size", "reset"))
            forceFullReconfigure()
            restartSystemUI()
            Trial.clear(this)
            runOnUiThread {
                Toast.makeText(this, getString(R.string.trial_read_failed), Toast.LENGTH_LONG).show()
            }
            return
        }
        // JANGAN buat dialog di sini — activity bisa di-recreate oleh reconfiguration
        // di tengah apply; dialog yang lepas dengan window-nya melempar IllegalArgumentException.
        // Saat onCreate berikutnya, restoreTrialIfPending() menampilkan dialog uji coba
        // di konfigurasi baru (dengan dimensi lengkap dari snapshot).
        runOnUiThread {
            showOutput("$label -> size: $sizeResult | density: $densityResult")
        }
    }

    /** Dijalankan di background thread. */
    private fun performRevert(message: String) {
        val snap = Trial.load(this) ?: return
        if (ensureServiceBlocking() == null) {
            runOnUiThread {
                Toast.makeText(
                    this,
                    getString(R.string.trial_revert_fail),
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        // Density restore dulu (alasan sama: size memicu reconfigure berat).
        exec(Trial.densityRestoreCommand(snap.densityRaw))
        exec(Trial.sizeRestoreCommand(snap.sizeRaw))
        // Kembali ke default? Paksa dua siklus reconfigure agar insets ikut reset.
        if (!snap.sizeRaw.contains("Override") && !snap.densityRaw.contains("Override")) {
            forceFullReconfigure()
        }
        restartSystemUI()
        Trial.clear(this)
        runOnUiThread {
            hideTrialDialog()
            showOutput(message)
            refreshStatus()
            Toast.makeText(this, getString(R.string.reminder_reinflate), Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreTrialIfPending(silent: Boolean) {
        val snap = Trial.load(this) ?: return
        // Trial sudah kedaluwarsa saat app mati — langsung kembalikan, jangan ganggu user.
        if (System.currentTimeMillis() >= snap.expiresAt) {
            Thread {
                if (ensureServiceBlocking() != null) {
                    performRevert(getString(R.string.trial_leftover_reverted))
                } else if (!silent) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            getString(R.string.trial_revert_fail),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
            return
        }
        // Uji coba belum habis (app mungkin di-recreate di tengah trial) —
        // tampilkan dialog lagi; tidak ada soft-lock: tombol SIMPAN/KEMBALIKAN tersedia,
        // ticker menghitung sisa waktu asli dari expiresAt.
        showTrialDialog(snap.label)
    }

    private fun ensureBound() {
        val granted = shizukuGranted()
        Log.d("Redensity", "ensureBound granted=$granted serviceNull=${service == null} bound=$bound")
        if (granted && service == null && !bound) {
            bound = true
            try {
                Shizuku.bindUserService(serviceArgs, serviceConnection)
                Log.d("Redensity", "bindUserService called")
            } catch (e: Exception) {
                bound = false
                Log.e("Redensity", "bindUserService failed: ${e.message}", e)
                showOutput("Gagal bind Shizuku service: ${e.message}")
            }
        }
    }

    /**
     * Setelah `wm size` / `wm density` diterapkan, layar di-reconfigure dan koneksi
     * service bisa putus. Tunggu sampai service tersambung lagi (maks ~2 detik).
     */
    private fun ensureServiceBlocking(): IRedensityService? {
        if (service != null) return service
        runOnUiThread { ensureBound() }
        repeat(40) {
            if (service != null) return service
            Thread.sleep(50)
        }
        return service
    }

    private fun ensurePermission(): Boolean {
        if (!shizukuRunning()) {
            Toast.makeText(
                this,
                getString(R.string.shizuku_not_running),
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        if (!shizukuGranted()) {
            Shizuku.requestPermission(REQ_PERMISSION)
            return false
        }
        return true
    }

    /** Force-stop SystemUI agar insets/gesture dihitung ulang setelah resolusi dikembalikan. */
    private fun restartSystemUI() {
        try {
            exec(arrayOf("am", "force-stop", "com.android.systemui"))
        } catch (_: Exception) {
            // Best effort — jangan sampai menggagalkan alur reset.
        }
    }

    /**
     * Paksa dua siklus reconfigure setelah pindah ke default:
     * tulis ulang nilai fisik sebagai override, lalu kosongkan lagi.
     * Tanpa siklus kedua, HyperOS kadang menyimpan insets/gesture lama
     * (gejala: UI terasa = setelan lama, gesture bawah memicu asisten).
     */
    private fun forceFullReconfigure() {
        try {
            physicalDensity().let { exec(arrayOf("wm", "density", it.toString())) }
            physicalSize()?.let { (w, h) -> exec(arrayOf("wm", "size", "${w}x${h}")) }
            exec(arrayOf("wm", "size", "reset"))
            exec(arrayOf("wm", "density", "reset"))
        } catch (_: Exception) {
            // Best effort — biarkan gagal diam-diam.
        }
    }

    private fun refreshStatus() {
        when {
            !shizukuRunning() -> {
                statusText.text = getString(R.string.status_not_running)
                statusText.setTextColor(getColor(R.color.red))
            }
            !shizukuGranted() -> {
                statusText.text = getString(R.string.status_not_granted)
                statusText.setTextColor(getColor(R.color.amber))
            }
            service == null -> {
                statusText.text = getString(R.string.status_connecting)
                statusText.setTextColor(getColor(R.color.amber))
            }
            else -> {
                statusText.text = getString(R.string.status_active)
                statusText.setTextColor(getColor(R.color.green))
            }
        }
        sizeText.text = getString(R.string.current_size_density, currentEffectiveSizeText(), currentEffectiveDensityText())
        syncDensitySlider()
    }

    private fun exec(cmd: Array<String>): String {
        val current = service ?: ensureServiceBlocking() ?: return "(Shizuku service belum terhubung)"
        return try {
            current.exec(cmd) ?: "(kosong)"
        } catch (e: Exception) {
            // Display reconfigure bisa mematikan UserService di tengah panggilan —
            // rebind dan coba sekali lagi, jangan diam-diam mengembalikan error.
            Log.w("Redensity", "exec ${cmd.joinToString(" ")} failed, rebinding: ${e.message}")
            service = null
            bound = false
            val fresh = ensureServiceBlocking() ?: return "Error: ${e.message}"
            try {
                fresh.exec(cmd) ?: "(kosong)"
            } catch (e2: Exception) {
                "Error: ${e2.message}"
            }
        }
    }

    private fun currentEffectiveSizeText(): String {
        if (service == null) return getString(R.string.size_unknown)
        val raw = exec(arrayOf("wm", "size"))
        val effective = parseSize(raw) ?: return raw.replace("\n", " | ").trim()
        val m = Regex("""Physical size:\s*(\d+)x(\d+)""").find(raw)
        val physical = if (m != null) Pair(m.groupValues[1].toInt(), m.groupValues[2].toInt()) else null
        return if (physical != null && effective != physical) {
            getString(R.string.size_override, effective.first, effective.second, physical.first, physical.second)
        } else {
            "${effective.first}x${effective.second}"
        }
    }

    private fun currentEffectiveDensityText(): String =
        effectiveDensity()?.let { "$it dpi" } ?: getString(R.string.density_unknown)

    private fun parseLineValue(raw: String, label: String): Int? {
        val m = Regex("""$label:\s*(\d+)""").find(raw)
        return m?.groupValues?.get(1)?.toInt()
    }

    private fun physicalSize(): Pair<Int, Int>? {
        val raw = exec(arrayOf("wm", "size"))
        val m = Regex("""Physical size:\s*(\d+)x(\d+)""").find(raw) ?: return parseSize(raw)
        return Pair(m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }

    private fun effectiveDensity(): Int? {
        if (service == null) return null
        val raw = exec(arrayOf("wm", "density"))
        // "Override density: 320" appears after "Physical density: 440" when overridden.
        val matches = Regex("""(\d+)""").findAll(raw).toList()
        return matches.lastOrNull()?.value?.toInt()
    }

    private fun syncDensitySlider() {
        val dpi = effectiveDensity() ?: return
        suppressSlider = true
        densitySlider.progress = (dpi - 120).coerceIn(0, 360)
        densityValue.text = getString(R.string.density_value, dpi)
        suppressSlider = false
    }

    private fun effectiveWidth(): Int? = parseSize(exec(arrayOf("wm", "size")))?.first

    private fun physicalDensity(): Int {
        val raw = exec(arrayOf("wm", "density"))
        return parseLineValue(raw, "Physical density") ?: 420
    }

    private fun parseSize(raw: String): Pair<Int, Int>? {
        val matches = Regex("""(\d+)x(\d+)""").findAll(raw).toList()
        if (matches.isEmpty()) return null
        val last = matches.last()
        return Pair(last.groupValues[1].toInt(), last.groupValues[2].toInt())
    }

    private fun evenRounded(value: Double): Int =
        (value / 2.0).toInt() * 2

    private fun showOutput(text: String) {
        outputText.text = text
    }

    private fun applyWidth(widthPx: Int) {
        if (!ensurePermission()) return
        showApplyConfirmation(widthPx >= 1440, targetDetail(widthPx)) { applyWidthConfirmed(widthPx) }
    }

    /** Density custom per preset (bucket Android + tuning mobile-friendly). */
    private fun presetDensity(widthPx: Int): Int {
        val native = physicalDensityCache ?: physicalDensity()
        return when (widthPx) {
            360 -> 160
            480 -> 180
            540 -> 200
            720 -> 260
            1080 -> native
            1440 -> 560
            else -> native
        }
    }

    private fun applyWidthConfirmed(widthPx: Int) {
        if (!ensurePermission()) return
        Thread {
            if (ensureServiceBlocking() == null) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.service_not_connected), Toast.LENGTH_SHORT)
                        .show()
                }
                return@Thread
            }
            if (isTrialPending()) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.trial_finish_first), Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }
            val raw = exec(arrayOf("wm", "size"))
            val current = parseSize(raw)
            val physical = physicalSize()
            val baseWidth = physical?.first ?: current?.first
            val scale = if (baseWidth != null && baseWidth > 0) widthPx.toDouble() / baseWidth else 1.0

            val sizeTarget: Pair<Int, Int> = if (current != null) {
                val h = evenRounded(current.second * (widthPx.toDouble() / current.first))
                Pair(widthPx, h)
            } else {
                Pair(widthPx, widthPx * 2)
            }
            val densityTarget = presetDensity(widthPx)

            startTrial(sizeTarget, densityTarget, "${widthPx}p")
        }.start()
    }

    /** Detail dimensi target untuk dialog konfirmasi (preset lebar, density proporsional). */
    private fun targetDetail(widthPx: Int): String {
        val phys = physicalSizeCache
        val base = physicalDensityCache
        if (phys == null || phys.first <= 0 || base == null) return "${widthPx}p"
        val scale = widthPx.toDouble() / phys.first
        val h = evenRounded(phys.second * scale)
        val d = presetDensity(widthPx)
        return getString(R.string.apply_confirm_detail, widthPx, h, d)
    }

    /**
     * Konfirmasi SEBELUM resolusi berubah — dialog tampil di layar default,
     * jadi tombolnya selalu bisa dipencet (beda dengan dialog pasca-uji-coba
     * yang bisa raksasa selama reconfigure). 2K memakai peringatan gaming.
     */
    private fun showApplyConfirmation(isTwoK: Boolean, detail: String, onApply: () -> Unit) {
        val title = if (isTwoK) getString(R.string.warning_2k_title) else getString(R.string.apply_confirm_title)
        val message = if (isTwoK) {
            getString(R.string.warning_2k_message)
        } else {
            getString(R.string.apply_confirm_message, detail)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(getString(if (isTwoK) R.string.warning_2k_apply else R.string.apply_confirm_apply)) { _, _ -> onApply() }
            .setNegativeButton(getString(R.string.profile_cancel), null)
            .show()
    }

    private fun refreshProfileList() {
        Thread {
            if (!shizukuGranted() || ensureServiceBlocking() == null) {
                runOnUiThread {
                    profileItems = emptyList()
                    profileList.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, listOf(getString(R.string.profile_shizuku_inactive)))
                }
                return@Thread
            }
            val all = Profiles.loadAll(this)
            runOnUiThread {
                profileItems = all
                profileList.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    if (all.isEmpty()) listOf(getString(R.string.profile_empty)) else all.map { it.name }
                )
                // wrap_content (tidak ada celah saat kosong), tapi batasi ~3 baris bila banyak profil.
                val maxHeight = (168 * resources.displayMetrics.density).toInt()
                profileList.layoutParams = profileList.layoutParams.apply {
                    height = if (all.size >= 3) maxHeight else android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }
        }.start()
    }

    private fun applyProfile(p: Profiles.Profile) {
        if (isTrialPending()) {
            Toast.makeText(this, getString(R.string.trial_finish_first), Toast.LENGTH_SHORT).show()
            return
        }
        if (!ensurePermission()) return
        // Detail dimensi konkret (pakai cache fisik); fallback ke nama profil.
        val detail = physicalSizeCache?.let { phys ->
            if (phys.first > 0) {
                val h = evenRounded(phys.second * (p.widthPx.toDouble() / phys.first))
                getString(R.string.apply_confirm_detail, p.widthPx, h, p.density)
            } else {
                null
            }
        } ?: p.name
        showApplyConfirmation(p.widthPx >= 1440, detail) { startProfileTrial(p) }
    }

    private fun startProfileTrial(p: Profiles.Profile) {
        Thread {
            if (ensureServiceBlocking() == null) return@Thread
            if (isTrialPending()) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.trial_finish_first), Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }
            val physical = physicalSize() ?: return@Thread
            val sizeTarget = Pair(
                p.widthPx,
                evenRounded(physical.second * (p.widthPx.toDouble() / physical.first))
            )
            startTrial(sizeTarget, p.density, getString(R.string.profile_format, p.name))
        }.start()
    }

    private fun promptSaveProfile() {
        if (!ensurePermission()) return
        Thread {
            if (ensureServiceBlocking() == null) return@Thread
            val w = effectiveWidth() ?: return@Thread
            val d = effectiveDensity() ?: physicalDensity()
            runOnUiThread {
                val input = EditText(this)
                input.hint = getString(R.string.profile_name_hint)
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.profile_save_title, w, d))
                    .setView(input)
                    .setPositiveButton(getString(R.string.profile_save)) { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isEmpty()) {
                            Toast.makeText(this, getString(R.string.profile_empty_name), Toast.LENGTH_SHORT).show()
                        } else {
                            Profiles.addUser(this, Profiles.Profile(name, w, d))
                            Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
                            refreshProfileList()
                        }
                    }
                    .setNegativeButton(getString(R.string.profile_cancel), null)
                    .show()
            }
        }.start()
    }

    private fun resetSize() {
        if (!ensurePermission()) return
        Thread {
            if (ensureServiceBlocking() == null) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.service_not_connected), Toast.LENGTH_SHORT)
                        .show()
                }
                return@Thread
            }
            // Kalau sedang uji coba, batalkan dulu (kembali ke pra-trial).
            if (Trial.load(this) != null) {
                performRevert(getString(R.string.trial_reverted_msg))
            }
            // Lalu reset ke bawaan, apapun kondisinya.
            val densityResult = exec(arrayOf("wm", "density", "reset"))
            val sizeResult = exec(arrayOf("wm", "size", "reset"))
            forceFullReconfigure()
            restartSystemUI()
            val message = "-> wm size reset\n$sizeResult\n-> wm density reset\n$densityResult\n-> SystemUI di-restart"
            runOnUiThread {
                hideTrialDialog()
                showOutput(message)
                refreshStatus()
                Toast.makeText(this, getString(R.string.reminder_reinflate), Toast.LENGTH_LONG).show()
            }
        }.start()
    }
}

