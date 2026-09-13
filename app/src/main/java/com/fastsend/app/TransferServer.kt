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
    private val pool = Executors.newCachedThreadPool()

    fun start(): Int {
        server?.close()
        server = ServerSocket(TransferProtocol.PORT)
        pool.execute {
            try {
                onState("Waiting for devices…")
                while (!Thread.currentThread().isInterrupted) {
                    val socket = server?.accept() ?: break
                    pool.execute { runCatching { receive(socket) }.onFailure { onState("Receive failed: ${it.message}") } }
                }
            } catch (_: Exception) {
                onState("Receiver stopped")
            }
        }
        return TransferProtocol.PORT
    }

    private fun receive(socket: Socket) {
        socket.use { connection ->
            connection.tcpNoDelay = true
            connection.keepAlive = true
            val input = DataInputStream(BufferedInputStream(connection.getInputStream(), TransferProtocol.BUFFER))
            val control = DataOutputStream(BufferedOutputStream(connection.getOutputStream(), TransferProtocol.BUFFER))
            require(TransferProtocol.readString(input) == TransferProtocol.MAGIC) { "Unsupported TurboShare client" }
            input.readInt() // transfer mode reserved for future adaptive tuning
            val count = input.readInt()
            require(count in 1..TransferProtocol.MAX_FILES)
            repeat(count) {
                val name = TransferProtocol.readString(input)
                val size = input.readLong()
                require(size >= 0)
                TransferProtocol.readString(input) // mime
                val hash = TransferProtocol.readString(input)
                val dir = File(context.getExternalFilesDir(null), "Received")
                dir.mkdirs()
                val safeName = FileName.safe(name)
                val finalFile = uniqueFile(dir, safeName)
                val tempFile = File(dir, "$safeName.part")
                val existing = tempFile.length().coerceAtMost(size)
                RandomAccessFile(tempFile, "rw").use { out ->
                    out.setLength(existing)
                    out.seek(existing)
                    control.writeLong(existing)
                    control.flush()
                    var done = existing
                    val buffer = ByteArray(TransferProtocol.BUFFER)
                    val started = System.nanoTime()
                    while (done < size) {
                        val want = minOf(buffer.size.toLong(), size - done).toInt()
                        input.readFully(buffer, 0, want)
                        out.write(buffer, 0, want)
                        done += want
                        onProgress(name, done, size)
                    }
                    out.fd.sync()
                    val elapsed = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
                    val actual = tempFile.inputStream().use { TransferProtocol.sha256(it) }
                    if (actual.equals(hash, true) && tempFile.length() == size) {
                        if (!tempFile.renameTo(finalFile)) throw IOException("Unable to finalize $name")
                        history.add(TransferItem(name, size, "RECEIVED", System.currentTimeMillis(), true, size * 1000L / elapsed))
                        onState("Received $name")
                    } else {
                        history.add(TransferItem(name, size, "RECEIVED", System.currentTimeMillis(), false))
                        onState("Checksum failed for $name — transfer can resume")
                    }
                }
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        pool.shutdownNow()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 2
        while (file.exists()) file = File(dir, "$base ($i)$ext").also { i++ }
        return file
    }
}

object FileName {
    fun safe(name: String): String = name.replace(Regex("""[\\/:*?\"<>|]"""), "_").take(180).ifBlank { "file" }
}
