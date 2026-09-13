package com.fastsend.app

import android.content.Context
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class TransferServer(
    private val context: Context,
    private val history: HistoryStore,
    private val onProgress: (String, Long, Long) -> Unit,
    private val onState: (String) -> Unit
) {
    private var server: ServerSocket? = null
    private val pool = Executors.newSingleThreadExecutor()

    fun start(): Int {
        server = ServerSocket(TransferProtocol.PORT)
        val port = server!!.localPort

        pool.execute {
            try {
                onState("Waiting for a device…")

                val socket = server!!.accept()
                receive(socket)
            } catch (e: Exception) {
                onState("Receiver stopped")
            }
        }

        return port
    }

    private fun receive(socket: Socket) {
        socket.use { connection ->
            connection.tcpNoDelay = true

            val input = DataInputStream(
                BufferedInputStream(
                    connection.getInputStream(),
                    TransferProtocol.BUFFER
                )
            )

            val control = DataOutputStream(
                BufferedOutputStream(
                    connection.getOutputStream(),
                    TransferProtocol.BUFFER
                )
            )

            val magic = TransferProtocol.readString(input)
            require(magic == TransferProtocol.MAGIC)

            val count = input.readInt()
            require(count in 1..1000)

            repeat(count) {
                val name = TransferProtocol.readString(input)
                val size = input.readLong()
                val mime = TransferProtocol.readString(input)
                val hash = TransferProtocol.readString(input)

                val safeName = FileName.safe(name)

                val dir = File(
                    context.getExternalFilesDir(null),
                    "Received"
                )

                dir.mkdirs()

                val finalFile = File(dir, safeName)
                val tempFile = File(dir, "$safeName.part")

                val existing =
                    if (tempFile.exists()) tempFile.length()
                    else 0L

                RandomAccessFile(tempFile, "rw").use { out ->
                    out.seek(existing)

                    control.writeLong(existing)
                    control.flush()

                    var done = existing
                    val buffer = ByteArray(TransferProtocol.BUFFER)

                    while (done < size) {
                        val want = minOf(
                            buffer.size.toLong(),
                            size - done
                        ).toInt()

                        input.readFully(buffer, 0, want)
                        out.write(buffer, 0, want)

                        done += want

                        onProgress(name, done, size)
                    }
                }

                val actual = tempFile.inputStream().use {
                    TransferProtocol.sha256(it)
                }

                if (actual.equals(hash, true)) {
                    if (finalFile.exists()) {
                        finalFile.delete()
                    }

                    tempFile.renameTo(finalFile)

                    history.add(
                        TransferItem(
                            name,
                            size,
                            "RECEIVED",
                            System.currentTimeMillis(),
                            true
                        )
                    )

                    onState("Received $name")
                } else {
                    history.add(
                        TransferItem(
                            name,
                            size,
                            "RECEIVED",
                            System.currentTimeMillis(),
                            false
                        )
                    )

                    onState("Checksum failed for $name")
                }
            }
        }
    }

    fun stop() {
        try {
            server?.close()
        } catch (_: Exception) {
        }

        pool.shutdownNow()
    }
}

object FileName {
    fun safe(name: String): String =
        name
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(180)
            .ifBlank { "file" }
}
