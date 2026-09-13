package com.fastsend.app

import android.content.Context
import android.net.wifi.WifiManager
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/** Lightweight LAN web receiver. A PC can open the shown URL and upload files without installing TurboShare. */
class WebTransferServer(
    private val context: Context,
    private val history: HistoryStore,
    private val onState: (String) -> Unit
) {
    companion object { const val PORT = 39272 }
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    fun start(): String {
        stop()
        server = ServerSocket(PORT)
        pool.execute {
            while (server != null && !Thread.currentThread().isInterrupted) {
                try { pool.execute { handle(server!!.accept()) } } catch (_: Exception) { break }
            }
        }
        val ip = currentIp() ?: "device-ip"
        val url = "http://$ip:$PORT/"
        onState("PC transfer ready: $url")
        return url
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 15_000
            val input = BufferedInputStream(s.getInputStream(), 64 * 1024)
            val request = readHeaders(input) ?: return
            val first = request.line
            if (first.startsWith("GET / ")) {
                respond(s, 200, "text/html; charset=utf-8", page())
                return
            }
            if (first.startsWith("POST /upload")) {
                val type = request.headers["content-type"] ?: return@use respond(s, 400, "text/plain", "Missing content type")
                val length = request.headers["content-length"]?.toLongOrNull() ?: return@use respond(s, 411, "text/plain", "Content-Length required")
                val boundary = Regex("boundary=([^;]+)").find(type)?.groupValues?.get(1)?.trim('"') ?: return@use respond(s, 400, "text/plain", "Invalid multipart boundary")
                if (length > 2L * 1024 * 1024 * 1024) return@use respond(s, 413, "text/plain", "File too large")
                val body = ByteArray(length.toInt())
                readFully(input, body)
                val marker = ("--$boundary").toByteArray(StandardCharsets.UTF_8)
                val headerEnd = byteIndexOf(body, "\r\n\r\n".toByteArray())
                if (headerEnd < 0) return@use respond(s, 400, "text/plain", "Invalid upload")
                val fileStart = headerEnd + 4
                val fileEnd = byteIndexOf(body, "\r\n--".toByteArray(), fileStart)
                if (fileEnd < 0) return@use respond(s, 400, "text/plain", "Invalid upload")
                val partHeaders = String(body, 0, headerEnd, StandardCharsets.UTF_8)
                val filename = Regex("filename=\"([^\"]*)\"").find(partHeaders)?.groupValues?.get(1)?.ifBlank { "upload.bin" } ?: "upload.bin"
                val safe = FileName.safe(filename)
                val dir = File(context.getExternalFilesDir(null), "Received").apply { mkdirs() }
                val target = uniqueFile(dir, safe)
                FileOutputStream(target).use { it.write(body, fileStart, fileEnd - fileStart) }
                history.add(TransferItem(target.name, target.length(), "WEB", System.currentTimeMillis(), true))
                onState("Received ${target.name} from PC")
                respond(s, 200, "text/html; charset=utf-8", "<h2>Uploaded successfully</h2><p>${target.name}</p><a href='/'>Back</a>")
                return
            }
            respond(s, 404, "text/plain", "Not found")
        }
    }

    fun stop() { runCatching { server?.close() }; server = null }

    private fun page() = """
        <!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>TurboShare Web</title>
        <style>body{font-family:system-ui;max-width:720px;margin:40px auto;padding:20px;background:#0b1020;color:white}main{padding:28px;border-radius:24px;background:#151b31}input,button{font-size:18px;padding:14px;margin-top:16px;width:100%;box-sizing:border-box}button{cursor:pointer}</style></head>
        <body><main><h1>⚡ TurboShare</h1><p>Send files from this computer to your phone over the local network.</p><form method='post' action='/upload' enctype='multipart/form-data'><input type='file' name='file' multiple required><button type='submit'>Upload to phone</button></form><p>No cloud upload. Files stay on your local network.</p></main></body></html>
    """.trimIndent()

    private data class Headers(val line: String, val headers: Map<String, String>)
    private fun readHeaders(input: InputStream): Headers? {
        val bytes = ByteArrayOutputStream()
        var state = 0
        while (bytes.size() < 32 * 1024) {
            val b = input.read(); if (b < 0) return null
            bytes.write(b)
            state = when { state == 0 && b == 13 -> 1; state == 1 && b == 10 -> 2; state == 2 && b == 13 -> 3; state == 3 && b == 10 -> 4; else -> if (b == 13) 1 else 0 }
            if (state == 4) break
        }
        val lines = bytes.toString("UTF-8").trim().split("\r\n")
        if (lines.isEmpty()) return null
        val map = lines.drop(1).mapNotNull { line -> val i = line.indexOf(':'); if (i > 0) line.substring(0, i).lowercase() to line.substring(i + 1).trim() else null }.toMap()
        return Headers(lines.first(), map)
    }
    private fun readFully(input: InputStream, b: ByteArray) { var p = 0; while (p < b.size) { val n = input.read(b, p, b.size - p); if (n < 0) throw EOFException(); p += n } }
    private fun byteIndexOf(h: ByteArray, n: ByteArray, from: Int = 0): Int { outer@ for (i in from..h.size - n.size) { for (j in n.indices) if (h[i + j] != n[j]) continue@outer; return i }; return -1 }
    private fun respond(s: Socket, code: Int, type: String, body: String) { val bytes = body.toByteArray(StandardCharsets.UTF_8); val out = s.getOutputStream(); out.write("HTTP/1.1 $code OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); out.write(bytes); out.flush() }
    private fun uniqueFile(dir: File, name: String): File { var f = File(dir, name); if (!f.exists()) return f; val dot = name.lastIndexOf('.'); val base = if (dot > 0) name.substring(0, dot) else name; val ext = if (dot > 0) name.substring(dot) else ""; var i = 2; while (f.exists()) f = File(dir, "$base ($i)$ext").also { i++ }; return f }
    private fun currentIp(): String? = runCatching { val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager; val ip = wm.connectionInfo.ipAddress; listOf(ip and 255, ip shr 8 and 255, ip shr 16 and 255, ip shr 24 and 255).joinToString(".") }.getOrNull()
}
