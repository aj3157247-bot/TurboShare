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
    val success: Boolean
)
