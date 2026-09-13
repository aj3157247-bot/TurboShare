package com.fastsend.app

import java.io.*
import java.security.MessageDigest

object TransferProtocol {
    const val MAGIC = "TURBOSHARE2"
    const val PORT = 39271
    const val BUFFER = 1024 * 1024
    const val MAX_FILES = 1000
    const val MAX_NAME = 1000
    const val CHUNK_ACK_BYTES = 4L * 1024L * 1024L

    fun sha256(file: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = file.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun writeString(out: DataOutputStream, value: String) {
        val b = value.toByteArray(Charsets.UTF_8)
        require(b.size <= MAX_NAME * 4) { "String too large" }
        out.writeInt(b.size)
        out.write(b)
    }

    fun readString(input: DataInputStream): String {
        val n = input.readInt()
        require(n in 0..(MAX_NAME * 4)) { "Invalid string length" }
        return input.readFullyToByteArray(n).toString(Charsets.UTF_8)
    }

    fun DataInputStream.readFullyToByteArray(n: Int): ByteArray {
        val b = ByteArray(n)
        readFully(b)
        return b
    }
}
