package com.fastsend.app

import android.content.ContentResolver
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.math.max

class TransferClient(
    private val resolver: ContentResolver,
    private val history: HistoryStore,
    private val onProgress: (String, Long, Long) -> Unit,
    private val onState: (String) -> Unit
) {
    private val pool = Executors.newSingleThreadExecutor()
    @Volatile private var cancelled = false

    fun send(host: String, files: List<SelectedFile>, mode: TransferMode = TransferMode.TURBO) {
        cancelled = false
        pool.execute {
            try {
                require(files.isNotEmpty())
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, TransferProtocol.PORT), 7000)
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    socket.setPerformancePreferences(0, 2, 1)
                    val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), TransferProtocol.BUFFER))
                    val input = DataInputStream(BufferedInputStream(socket.getInputStream(), TransferProtocol.BUFFER))

                    TransferProtocol.writeString(out, TransferProtocol.MAGIC)
                    out.writeInt(mode.ordinal)
                    out.writeInt(files.size)
                    files.forEach { f ->
                        TransferProtocol.writeString(out, f.name)
                        out.writeLong(f.size)
                        TransferProtocol.writeString(out, f.mime)
                        resolver.openInputStream(f.uri)?.use { stream ->
                            TransferProtocol.writeString(out, TransferProtocol.sha256(stream))
                        } ?: throw IOException("Cannot open file: ${f.name}")
                    }
                    out.flush()

                    files.forEach { f ->
                        if (cancelled) throw InterruptedIOException("Cancelled")
                        val offset = input.readLong().coerceIn(0L, f.size)
                        resolver.openInputStream(f.uri)?.use { stream ->
                            skipFully(stream, offset)
                            var done = offset
                            var lastUi = System.nanoTime()
                            val started = System.nanoTime()
                            val buf = ByteArray(if (mode == TransferMode.TURBO) 4 * TransferProtocol.BUFFER else TransferProtocol.BUFFER)
                            while (done < f.size) {
                                if (cancelled) throw InterruptedIOException("Cancelled")
                                val n = stream.read(buf, 0, minOf(buf.size.toLong(), f.size - done).toInt())
                                if (n <= 0) throw EOFException("Unexpected end of ${f.name}")
                                out.write(buf, 0, n)
                                done += n
                                val now = System.nanoTime()
                                if (now - lastUi > 120_000_000L || done == f.size) {
                                    onProgress(f.name, done, f.size)
                                    lastUi = now
                                }
                                if (done % TransferProtocol.CHUNK_ACK_BYTES == 0L) out.flush()
                            }
                            out.flush()
                            val elapsed = max(1L, (System.nanoTime() - started) / 1_000_000L)
                            history.add(TransferItem(f.name, f.size, "SENT", System.currentTimeMillis(), true, f.size * 1000L / elapsed))
                        } ?: throw IOException("Cannot open file: ${f.name}")
                    }
                    onState("Transfer complete")
                }
            } catch (e: Exception) {
                onState(if (e is InterruptedIOException) "Transfer cancelled" else "Transfer failed: ${e.message ?: "connection error"}")
            }
        }
    }

    fun cancel() { cancelled = true }
    fun shutdown() { cancelled = true; pool.shutdownNow() }

    private fun skipFully(input: InputStream, target: Long) {
        var skipped = 0L
        while (skipped < target) {
            val n = input.skip(target - skipped)
            if (n <= 0) throw IOException("Unable to resume transfer")
            skipped += n
        }
    }
}
