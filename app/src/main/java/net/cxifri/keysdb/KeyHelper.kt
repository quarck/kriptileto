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

package net.cxifri.keysdb

import android.content.Context
import android.os.Build
import android.util.Log
import net.cxifri.R
import net.cxifri.aks.AndroidKeyStore
import net.cxifri.crypto.KeyEntry
import org.bouncycastle.util.encoders.UrlBase64

val KeyEntry.binaryKey: ByteArray?
    get() {
        var ret: ByteArray? = null

        if (encrypted && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)) {
            return null // no encryption is supported - can't decrypt
        }

        val binaryTextKey = UrlBase64.decode(key)

        if (encrypted) {
            try {
                val aks = AndroidKeyStore()
                ret = aks.decrypt(id, binaryTextKey)
            }
            catch (ex: Exception) {
                Log.e("KeyHelper", "exception decrypting key: ${ex}, ${ex.stackTrace}")
            }
        } else {
            ret = binaryTextKey
        }

        return ret
    }

fun KeyEntry.toStringDetails(context: Context): String {

    if (revoked)
        return context.getString(R.string.revoked_key)
    else if (encrypted)
        return context.getString(R.string.encrypted_by_aks_key)
    else
        return context.getString(R.string.stored_as_pt_key)
}


class KeyHelper {

    fun saveKey(context: Context,
                keyEntryIn: KeyEntry,
                preferAndroidKeyStore: Boolean
    ) {
        val key = keyEntryIn.binaryKey ?: throw Exception("Can't read binary key")

        KeysDatabase(context).use {
            db ->

            val id = db.add(KeyEntry())

            val updatedKeyEntry =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferAndroidKeyStore) {
                        val aks = AndroidKeyStore()
                        aks.createKey(id, force=true) // create matching keystore key that would be encrypting this key in DB
                        val encryptedTextKey = aks.encrypt(id, key)
                        val encryptedBase64TextKey = UrlBase64.encode(encryptedTextKey)
                        KeyEntry(id,
                                keyEntryIn.name,
                                encryptedBase64TextKey.toString(charset = Charsets.UTF_8),
                                true)
                    }
                    else {
                        keyEntryIn.copy(id = id)
                    }

            db.update(updatedKeyEntry)
        }
    }
}