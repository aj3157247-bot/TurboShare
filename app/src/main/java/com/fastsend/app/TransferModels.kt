package com.fastsend.app

import android.net.Uri

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String
)

data class DeviceInfo(
    val name: String,
    val address: String
)

data class TransferItem(
    val name: String,
    val size: Long,
    val direction: String,
    val timestamp: Long,
    val success: Boolean,
    val speedBytesPerSecond: Long = 0L
)

data class TransferTask(
    val id: Long,
    val host: String,
    val files: List<SelectedFile>,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TransferMode { BALANCED, TURBO, BATTERY_SAVER }
