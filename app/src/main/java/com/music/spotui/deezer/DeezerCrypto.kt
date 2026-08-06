package com.music.spotui.deezer

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Deezer stream (de)obfuscation. Deezer serves each track as a stream where
 * every 3rd 2048-byte chunk is Blowfish-CBC encrypted with a per-track key.
 *
 * Ported 1:1 from ReFreezer's native `DeezerDecryptor.java`
 * (github.com/DJDoubleD/refreezer) — the key derivation (MD5 of the track id
 * XOR-folded with a fixed secret) and the Blowfish/CBC/NoPadding scheme are the
 * Deezer protocol and must match byte-for-byte.
 */
internal object DeezerCrypto {

    private const val SECRET = "g4el58wc0zvf9na1"
    private val IV = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)

    /** The Blowfish key for a track: MD5(id) hex folded with [SECRET] via XOR. */
    fun trackKey(trackId: String): ByteArray {
        val md5 = MessageDigest.getInstance("MD5").digest(trackId.toByteArray())
        val idMd5 = md5.toHex().lowercase()
        val key = StringBuilder(16)
        for (i in 0 until 16) {
            val s0 = idMd5[i].code
            val s1 = idMd5[i + 16].code
            val s2 = SECRET[i].code
            key.append((s0 xor s1 xor s2).toChar())
        }
        // Deezer's key is the Latin-1 bytes of these chars (all < 0x100).
        return key.toString().toByteArray(Charsets.ISO_8859_1)
    }

    /** A fresh Blowfish cipher initialised for decrypting [key]. Not thread-safe. */
    fun cipher(key: ByteArray): Cipher =
        Cipher.getInstance("Blowfish/CBC/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "Blowfish"), IvParameterSpec(IV))
        }

    private fun ByteArray.toHex(): String {
        val hex = "0123456789ABCDEF".toCharArray()
        val out = CharArray(size * 2)
        for (j in indices) {
            val v = this[j].toInt() and 0xFF
            out[j * 2] = hex[v ushr 4]
            out[j * 2 + 1] = hex[v and 0x0F]
        }
        return String(out)
    }
}
