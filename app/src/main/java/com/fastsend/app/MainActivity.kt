package com.fastsend.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var devicesBox: LinearLayout
    private lateinit var selectedBox: LinearLayout
    private lateinit var historyBox: LinearLayout
    private lateinit var modeSpinner: Spinner
    private lateinit var premiumButton: Button

    private val selected = mutableListOf<SelectedFile>()
    private lateinit var history: HistoryStore
    private lateinit var direct: WifiDirectManager
    private lateinit var client: TransferClient
    private lateinit var premium: PremiumManager
    private var webServer: WebTransferServer? = null
    private var server: TransferServer? = null
    private var transferMode = TransferMode.TURBO

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { addUri(it) }
        refreshSelected()
    }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startDiscovery() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = HistoryStore(this)
        client = TransferClient(contentResolver, history, ::onProgress, ::setStatus)
        premium = PremiumManager(this, { updatePremiumUi() }, ::setStatus)
        buildUi()
        direct = WifiDirectManager(this, ::showDevices, ::onConnection, ::setStatus)
        direct.register()
        premium.connect()
        requestNeededPermissions()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 36)
        }
        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val icon = ImageView(this).apply { setImageResource(R.drawable.ic_turboshare); layoutParams = LinearLayout.LayoutParams(64, 64) }
        header.addView(icon)
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 0, 0, 0)
            addView(TextView(this@MainActivity).apply { text = getString(R.string.app_name); textSize = 30f })
            addView(TextView(this@MainActivity).apply { text = getString(R.string.tagline); textSize = 14f })
        })
        root.addView(header)

        status = TextView(this).apply { text = getString(R.string.ready); textSize = 15f; setPadding(0, 20, 0, 10) }
        root.addView(status)

        val turboCard = MaterialCardView(this).apply { radius = 28f; cardElevation = 0f; setContentPadding(20, 18, 20, 18) }
        val turboBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        turboBox.addView(TextView(this).apply { text = getString(R.string.turbo_title); textSize = 19f })
        turboBox.addView(TextView(this).apply { text = getString(R.string.turbo_subtitle); textSize = 13f; setPadding(0, 4, 0, 8) })
        modeSpinner = Spinner(this)
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf(
            getString(R.string.mode_turbo), getString(R.string.mode_balanced), getString(R.string.mode_battery)
        ))
        modeSpinner.setSelection(0)
        modeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { transferMode = TransferMode.entries[position] }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        turboBox.addView(modeSpinner)
        turboCard.addView(turboBox)
        root.addView(turboCard)

        val pick = primaryButton(getString(R.string.select_files)) { picker.launch(arrayOf("*/*")) }
        root.addView(pick)
        selectedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(selectedBox)

        val search = primaryButton(getString(R.string.find_nearby)) { startDiscovery() }
        root.addView(search)
        devicesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(devicesBox)

        val receive = primaryButton(getString(R.string.receive_files)) { startReceiver() }
        root.addView(receive)

        root.addView(primaryButton(getString(R.string.web_transfer)) {
            webServer?.stop()
            webServer = WebTransferServer(this, history, ::setStatus)
            webServer!!.start()
        })

        premiumButton = primaryButton(getString(R.string.unlock_premium)) { premium.buy(this) }
        root.addView(premiumButton)
        updatePremiumUi()

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; visibility = View.GONE }
        root.addView(progress)
        progressText = TextView(this).apply { visibility = View.GONE; setPadding(0, 8, 0, 12) }
        root.addView(progressText)

        root.addView(TextView(this).apply { text = getString(R.string.transfer_history); textSize = 20f; setPadding(0, 28, 0, 8) })
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(historyBox)
        refreshHistory()
    }

    private fun primaryButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 15f
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
    }

    private fun updatePremiumUi() {
        premiumButton.text = if (premium.isPremium) getString(R.string.premium_active) else getString(R.string.unlock_premium)
        premiumButton.isEnabled = !premium.isPremium
    }

    private fun addUri(uri: Uri) {
        val name = queryName(uri) ?: getString(R.string.file_fallback)
        val size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        if (selected.none { it.uri == uri }) selected += SelectedFile(uri, name, size.coerceAtLeast(0), mime)
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return uri.lastPathSegment
    }

    private fun refreshSelected() {
        selectedBox.removeAllViews()
        if (selected.isEmpty()) return
        selectedBox.addView(TextView(this).apply { text = getString(R.string.files_selected, selected.size); textSize = 17f })
        selected.forEachIndexed { i, f ->
            selectedBox.addView(TextView(this).apply {
                text = getString(R.string.file_item, f.name, formatSize(f.size))
                setPadding(0, 7, 0, 7)
                setOnClickListener { selected.removeAt(i); refreshSelected() }
            })
        }
        selectedBox.addView(Button(this).apply { text = getString(R.string.clear_selection); setOnClickListener { selected.clear(); refreshSelected() } })
    }

    private fun showDevices(list: List<DeviceInfo>) {
        devicesBox.removeAllViews()
        list.forEach { d ->
            devicesBox.addView(Button(this).apply {
                text = "📱 ${d.name}\n${d.address}"
                setOnClickListener {
                    if (selected.isEmpty()) setStatus(getString(R.string.select_files_first)) else direct.connect(d.address)
                }
            })
        }
    }

    private fun onConnection(info: android.net.wifi.p2p.WifiP2pInfo?) {
        if (info == null || !info.groupFormed) return
        val host = if (info.isGroupOwner) "127.0.0.1" else info.groupOwnerAddress?.hostAddress
        if (host != null) {
            setStatus(getString(R.string.connected_ready))
            if (selected.isNotEmpty() && host != "127.0.0.1") client.send(host, selected.toList(), transferMode)
        }
    }

    private fun startReceiver() {
        server?.stop()
        server = TransferServer(this, history, ::onProgress, ::setStatus)
        server!!.start()
        setStatus(getString(R.string.receiver_ready))
    }

    private fun startDiscovery() = direct.discover()
    private fun setStatus(s: String) { runOnUiThread { status.text = s } }

    private fun onProgress(name: String, done: Long, total: Long) {
        runOnUiThread {
            progress.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
            val p = if (total > 0) ((done * 1000L) / total).toInt() else 0
            progress.progress = p.coerceIn(0, 1000)
            progressText.text = "$name\n${formatSize(done)} / ${formatSize(total)}  (${p / 10}%)"
            if (total > 0 && done >= total) refreshHistory()
        }
    }

    private fun refreshHistory() {
        if (!::historyBox.isInitialized) return
        historyBox.removeAllViews()
        history.all().take(20).forEach {
            val speed = if (it.speedBytesPerSecond > 0) " • ${formatSize(it.speedBytesPerSecond)}/s" else ""
            historyBox.addView(TextView(this).apply {
                text = getString(R.string.history_item, if (it.success) getString(R.string.success_mark) else getString(R.string.failure_mark), it.direction, it.name, formatSize(it.size)) + speed
                setPadding(0, 5, 0, 5)
            })
        }
    }

    private fun requestNeededPermissions() {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            p += Manifest.permission.NEARBY_WIFI_DEVICES
            p += Manifest.permission.READ_MEDIA_IMAGES
            p += Manifest.permission.READ_MEDIA_VIDEO
            p += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            p += Manifest.permission.ACCESS_FINE_LOCATION
            p += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val missing = p.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray()) else startDiscovery()
    }

    private fun formatSize(n: Long): String {
        if (n < 1024) return "$n B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var x = n.toDouble(); var i = -1
        while (x >= 1024 && i < units.lastIndex) { x /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", x, units[i])
    }

    override fun onDestroy() {
        direct.unregister()
        server?.stop()
        webServer?.stop()
        client.shutdown()
        premium.close()
        super.onDestroy()
    }
}
