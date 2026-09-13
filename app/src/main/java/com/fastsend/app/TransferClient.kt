package com.fastsend.app

import android.content.ContentResolver
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class TransferClient(
    private val resolver: ContentResolver,
    private val history: HistoryStore,
    private val onProgress: (String, Long, Long) -> Unit,
    private val onState: (String) -> Unit
) {
    private val pool = Executors.newSingleThreadExecutor()

    fun send(host: String, files: List<SelectedFile>) {
        pool.execute {
            try {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(host, TransferProtocol.PORT),
                        5000
                    )
                    socket.tcpNoDelay = true

                    val out = DataOutputStream(
                        BufferedOutputStream(
                            socket.getOutputStream(),
                            TransferProtocol.BUFFER
                        )
                    )

                    val input = DataInputStream(
                        BufferedInputStream(
                            socket.getInputStream(),
                            TransferProtocol.BUFFER
                        )
                    )

                    TransferProtocol.writeString(out, TransferProtocol.MAGIC)
                    out.writeInt(files.size)

                    files.forEach { f ->
                        TransferProtocol.writeString(out, f.name)
                        out.writeLong(f.size)
                        TransferProtocol.writeString(out, f.mime)

                        resolver.openInputStream(f.uri)?.use { stream ->
                            TransferProtocol.writeString(
                                out,
                                TransferProtocol.sha256(stream)
                            )
                        } ?: throw IOException("Cannot open file: ${f.name}")
                    }

                    out.flush()

                    for (f in files) {
                        val offset = input.readLong()

                        resolver.openInputStream(f.uri)?.use { stream ->
                            var skipped = 0L

                            while (skipped < offset) {
                                val n = stream.skip(offset - skipped)
                                if (n <= 0) break
                                skipped += n
                            }

                            if (skipped < offset) {
                                throw IOException("Unable to resume file: ${f.name}")
                            }

                            val buf = ByteArray(TransferProtocol.BUFFER)
                            var done = offset

                            while (done < f.size) {
                                val n = stream.read(
                                    buf,
                                    0,
                                    minOf(
                                        buf.size.toLong(),
                                        f.size - done
                                    ).toInt()
                                )

                                if (n <= 0) {
                                    throw EOFException()
                                }

                                out.write(buf, 0, n)
                                done += n

                                onProgress(f.name, done, f.size)

                                if (done % (4L * TransferProtocol.BUFFER) == 0L) {
                                    out.flush()
                                }
                            }
                        } ?: throw IOException("Cannot open file: ${f.name}")

                        out.flush()

                        history.add(
                            TransferItem(
                                f.name,
                                f.size,
                                "SENT",
                                System.currentTimeMillis(),
                                true
                            )
                        )
                    }

                    onState("Transfer complete")
                }
            } catch (e: Exception) {
                onState(
                    "Transfer failed: ${e.message ?: "connection error"}"
                )
            }
        }
    }

    fun shutdown() = pool.shutdownNow()
}
