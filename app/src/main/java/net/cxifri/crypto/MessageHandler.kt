/*
 * Copyright (C) 2018 Sergey Parshin (quarck@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.cxifri.crypto

import net.cxifri.encodings.Base61Encoder
import net.cxifri.encodings.GZipEncoder
import net.cxifri.keysdb.binaryKey


class MessageHandler(
        private val binaryCryptor: BinaryMessageHandlerInterface
): MessageHandlerInterface {

    companion object {
        const val MESSAGE_FORMAT_PLAINTEXT: Byte = 0
        const val MESSAGE_FORMAT_GZIP_PLAINTEXT: Byte = 1
        const val MESSAGE_FORMAT_KEY_REVOKE: Byte = 3

        const val MESSAGE_FORMAT_KEY_REVOKE_SIZE = 16
    }

    private fun packMessageForEncryption(message: MessageBase): ByteArray {

        return when (message) {
            is TextMessage ->
                packTextMessage(message)

            is KeyRevokeMessage ->
                packKeyIsNoLongerSecureMessage(message)

            else ->
                throw NotImplementedError("No support for type ${message.javaClass.canonicalName}")
        }
    }

    private fun packTextMessage(message: TextMessage): ByteArray {

        val utf8 = message.text.toByteArray(charset = Charsets.UTF_8)

        val gzipUtf8 = GZipEncoder().deflate(utf8)

        if (utf8.size < gzipUtf8.size) {
            val binaryMessage = ByteArray(1 + utf8.size)
            binaryMessage[0] = MESSAGE_FORMAT_PLAINTEXT
            System.arraycopy(utf8, 0, binaryMessage, 1, utf8.size)
            return binaryMessage
        }

        val binaryMessage = ByteArray(1 + gzipUtf8.size)
        binaryMessage[0] = MESSAGE_FORMAT_GZIP_PLAINTEXT
        System.arraycopy(gzipUtf8, 0, binaryMessage, 1, gzipUtf8.size)
        return binaryMessage

    }

    private fun packKeyIsNoLongerSecureMessage(message: KeyRevokeMessage): ByteArray {
        // just a single byte - message code for this one
        val ret = ByteArray(MESSAGE_FORMAT_KEY_REVOKE_SIZE)
        ret[0] = MESSAGE_FORMAT_KEY_REVOKE
        return ret
    }


    private fun unpackDecryptedMessage(key: KeyEntry, blob: ByteArray?): MessageBase? {
        if (blob == null || blob.size < 1)
            return null

        return when (blob[0]) {
            MESSAGE_FORMAT_PLAINTEXT, MESSAGE_FORMAT_GZIP_PLAINTEXT ->
                unpackTextMessage(key, blob)

            MESSAGE_FORMAT_KEY_REVOKE ->
                unpackKeyRevokeMessage(key, blob)

            else ->
                null
        }
    }

    private fun unpackTextMessage(key: KeyEntry, blob: ByteArray): TextMessage? {

        when (blob[0]) {
            MESSAGE_FORMAT_PLAINTEXT -> {
                return TextMessage(key, String(blob, 1, blob.size - 1, Charsets.UTF_8))
            }

            MESSAGE_FORMAT_GZIP_PLAINTEXT -> {
                val ungzip = GZipEncoder().inflate(blob, 1, blob.size-1)
                return ungzip?.let { TextMessage(key, ungzip.toString(charset=Charsets.UTF_8)) }
            }

            else ->
                return null
        }
    }

    private fun unpackKeyRevokeMessage(key: KeyEntry, blob: ByteArray): KeyRevokeMessage? {

        if (blob.size != MESSAGE_FORMAT_KEY_REVOKE_SIZE) {
            return null
        }

        for (i in 1 until MESSAGE_FORMAT_KEY_REVOKE_SIZE) {
            if (blob[i] != 0.toByte())
                return null
        }

        return KeyRevokeMessage(key)
    }

    override fun encrypt(message: MessageBase, key: KeyEntry): String {
        val packed = packMessageForEncryption(message)
        val encoded = binaryCryptor.encrypt(packed,
                key.binaryKey ?: throw Exception("Cannot access encryption key"))
        val base61 = Base61Encoder.encode(encoded)
        return base61.toString(charset = Charsets.UTF_8)
    }

    override fun decrypt(message: String, key: KeyEntry): MessageBase? {

        var decryptedBinary: ByteArray?

        try {
            val unbase61 = Base61Encoder.decode(message.toByteArray(charset = Charsets.UTF_8))
            decryptedBinary = binaryCryptor.decrypt(unbase61,
                    key.binaryKey ?: throw Exception("Cannot access encryption key"))
        }
        catch (ex: Exception) {
            decryptedBinary = null
        }

        return if (decryptedBinary != null)
            unpackDecryptedMessage(key, decryptedBinary)
        else
            null
    }

    override fun decrypt(message: String, keys: List<KeyEntry>): MessageBase? {

        val unbase61: ByteArray

        try {
            unbase61 = Base61Encoder.decode(message.toByteArray(charset = Charsets.UTF_8))
        }
        catch (ex: Exception) {
            return null
        }

        var decryptedBinary: ByteArray? = null
        var matchedKey: KeyEntry? = null

        for (key in keys) {
            try {
                val binaryKey = key.binaryKey
                if (binaryKey != null) {
                    decryptedBinary = binaryCryptor.decrypt(unbase61, binaryKey)
                    if (decryptedBinary != null) {
                        matchedKey = key
                        break
                    }
                }
            } catch (ex: Exception) {
                decryptedBinary = null
            }
        }

        return if (decryptedBinary != null && matchedKey != null)
            unpackDecryptedMessage(matchedKey, decryptedBinary)
        else
            null
    }
}
