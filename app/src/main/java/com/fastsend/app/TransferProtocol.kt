package com.fastsend.app

import java.io.*
import java.security.MessageDigest

object TransferProtocol {
    const val MAGIC = "FASTSEND1"
    const val PORT = 39271
    const val BUFFER = 1024 * 1024

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
        out.writeInt(b.size)
        out.write(b)
    }

    fun readString(input: DataInputStream): String {
        val n = input.readInt()
        require(n in 0..1_000_000)
        return input.readFullyToByteArray(n).toString(Charsets.UTF_8)
    }

    fun DataInputStream.readFullyToByteArray(n: Int): ByteArray {
        val b = ByteArray(n)
        readFully(b)
        return b
    }
}
