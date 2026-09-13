package com.fastsend.app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.wifi.p2p.*
import android.os.Build
import androidx.core.content.ContextCompat

class WifiDirectManager(
    private val context: Context,
    private val onDevices: (List<DeviceInfo>) -> Unit,
    private val onConnection: (WifiP2pInfo?) -> Unit,
    private val onState: (String) -> Unit
) {
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(context, context.mainLooper, null)
    private val devices = mutableListOf<DeviceInfo>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (!hasPermission()) return
                    manager.requestPeers(channel) { list ->
                        devices.clear()
                        list.deviceList.forEach { devices.add(DeviceInfo(it.deviceName, it.deviceAddress)) }
                        onDevices(devices.toList())
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    if (!hasPermission()) return
                    manager.requestConnectionInfo(channel) { onConnection(it) }
                }
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) ==
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    onState(if (enabled) "Wi-Fi Direct is on" else "Turn on Wi-Fi")
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
    }

    fun discover() {
        if (!hasPermission()) return
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { onState("Searching for nearby devices…") }
            override fun onFailure(reason: Int) { onState("Discovery failed ($reason)") }
        })
    }

    fun connect(address: String) {
        if (!hasPermission()) return
        val config = WifiP2pConfig().apply { deviceAddress = address }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { onState("Connecting…") }
            override fun onFailure(reason: Int) { onState("Connection failed ($reason)") }
        })
    }

    fun unregister() {
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    private fun hasPermission(): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
            PackageManager.PERMISSION_GRANTED
    }
}
