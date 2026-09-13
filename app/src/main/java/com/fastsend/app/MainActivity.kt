package com.fastsend.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var devicesBox: LinearLayout
    private lateinit var selectedBox: LinearLayout
    private lateinit var historyBox: LinearLayout

    private val selected = mutableListOf<SelectedFile>()
    private lateinit var history: HistoryStore
    private lateinit var direct: WifiDirectManager
    private var server: TransferServer? = null
    private lateinit var client: TransferClient
    private var connectedHost: String? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { addUri(it) }
        refreshSelected()
    }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startDiscovery()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = HistoryStore(this)
        client = TransferClient(contentResolver, history, ::onProgress, ::setStatus)
        buildUi()
        direct = WifiDirectManager(this, ::showDevices, ::onConnection, ::setStatus)
        direct.register()
        requestNeededPermissions()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 32)
        }
        scroll.addView(root)
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "⚡ " + getString(R.string.app_name)
            textSize = 30f
            setPadding(0, 0, 0, 6)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = getString(R.string.tagline)
            textSize = 15f
        }
        root.addView(subtitle)

        status = TextView(this).apply {
            text = getString(R.string.ready)
            textSize = 15f
            setPadding(0, 18, 0, 12)
        }
        root.addView(status)

        val pick = Button(this).apply {
            text = getString(R.string.select_files)
            setOnClickListener { picker.launch(arrayOf("*/*")) }
        }
        root.addView(pick)

        selectedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(selectedBox)

        val search = Button(this).apply {
            text = getString(R.string.find_nearby)
            setOnClickListener { startDiscovery() }
        }
        root.addView(search)

        devicesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(devicesBox)

        val receive = Button(this).apply {
            text = getString(R.string.receive_files)
            setOnClickListener { startReceiver() }
        }
        root.addView(receive)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            visibility = View.GONE
        }
        root.addView(progress)

        progressText = TextView(this).apply {
            visibility = View.GONE
            setPadding(0, 8, 0, 12)
        }
        root.addView(progressText)

        val historyTitle = TextView(this).apply {
            text = getString(R.string.transfer_history)
            textSize = 20f
            setPadding(0, 28, 0, 8)
        }
        root.addView(historyTitle)

        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(historyBox)
        refreshHistory()
    }

    private fun addUri(uri: Uri) {
        val name = queryName(uri) ?: getString(R.string.file_fallback)
        val size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        if (selected.none { it.uri == uri }) selected += SelectedFile(uri, name, size.coerceAtLeast(0), mime)
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment
    }

    private fun refreshSelected() {
        selectedBox.removeAllViews()
        if (selected.isEmpty()) return
        val header = TextView(this).apply {
            text = getString(R.string.files_selected, selected.size)
            textSize = 17f
        }
        selectedBox.addView(header)
        selected.forEachIndexed { i, f ->
            selectedBox.addView(TextView(this).apply {
                text = getString(R.string.file_item, f.name, formatSize(f.size))
                setPadding(0, 4, 0, 4)
                setOnClickListener { selected.removeAt(i); refreshSelected() }
            })
        }
        val clear = Button(this).apply {
            text = getString(R.string.clear_selection)
            setOnClickListener { selected.clear(); refreshSelected() }
        }
        selectedBox.addView(clear)
    }

    private fun showDevices(list: List<DeviceInfo>) {
        devicesBox.removeAllViews()
        list.forEach { d ->
            val b = Button(this).apply {
                text = "📱 ${d.name}\n${d.address}"
                setOnClickListener {
                    direct.connect(d.address)
                }
            }
            devicesBox.addView(b)
        }
    }

    private fun onConnection(info: WifiP2pInfo?) {
        if (info == null || !info.groupFormed) return
        val host = if (info.isGroupOwner) "127.0.0.1" else info.groupOwnerAddress?.hostAddress
        if (host != null) {
            connectedHost = host
            setStatus(getString(R.string.connected_ready))
            if (selected.isNotEmpty() && host != "127.0.0.1") {
                client.send(host, selected.toList())
            }
        }
    }

    private fun startReceiver() {
        server?.stop()
        server = TransferServer(this, history, ::onProgress, ::setStatus)
        server!!.start()
        setStatus(getString(R.string.receiver_ready))
    }

    private fun startDiscovery() {
        direct.discover()
    }

    private fun setStatus(s: String) {
        runOnUiThread { status.text = s }
    }

    private fun onProgress(name: String, done: Long, total: Long) {
        runOnUiThread {
            progress.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
            val p = if (total > 0) ((done * 1000L) / total).toInt() else 0
            progress.progress = p.coerceIn(0, 1000)
            progressText.text = "$name\n${formatSize(done)} / ${formatSize(total)}  (${p / 10}%)"
            if (total > 0 && done >= total) {
                refreshHistory()
            }
        }
    }

    private fun refreshHistory() {
        historyBox.removeAllViews()
        history.all().take(15).forEach {
            historyBox.addView(TextView(this).apply {
                text = getString(R.string.history_item, if (it.success) getString(R.string.success_mark) else getString(R.string.failure_mark), it.direction, it.name, formatSize(it.size))
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
        var x = n.toDouble()
        var i = -1
        while (x >= 1024 && i < units.lastIndex) { x /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", x, units[i])
    }

    override fun onDestroy() {
        direct.unregister()
        server?.stop()
        client.shutdown()
        super.onDestroy()
    }
}
