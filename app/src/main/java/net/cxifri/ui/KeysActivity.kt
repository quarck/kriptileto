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

package net.cxifri.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import net.cxifri.aks.AndroidKeyStore
import net.cxifri.crypto.DerivedKeyGenerator
import net.cxifri.R
import net.cxifri.keysdb.KeyEntry
import net.cxifri.keysdb.KeySaveHelper
import net.cxifri.keysdb.KeysDatabase

class KeyStateEntry(
        var context: Context,
        inflater: LayoutInflater,
        val key: KeyEntry,
        val onReplaceKey: (KeyEntry) -> Unit,
        val onDeleteKey: (KeyEntry) -> Unit
) {

    val layout: RelativeLayout
    val keyName: TextView
    val keyDetails: TextView
    val buttonReplace: Button
    val buttonDelete: Button

    init {
        layout = inflater.inflate(R.layout.key_list_item, null) as RelativeLayout? ?: throw Exception("Layout error")

        keyName = layout.findViewById<TextView>(R.id.textViewKeyName) ?: throw Exception("Layout error")
        keyDetails = layout.findViewById<TextView>(R.id.textViewKeyDetails) ?: throw Exception("Layout error")
        buttonReplace = layout.findViewById(R.id.buttonReplace)
        buttonDelete = layout.findViewById(R.id.buttonDelete)

        buttonReplace.setOnClickListener(this::onButtonReplace)
        buttonDelete.setOnClickListener(this::onButtonDelete)

        keyName.setText(key.name)
        keyDetails.setText(key.toStringDetails())
    }

    fun onButtonReplace(v: View) {

        AlertDialog.Builder(context)
                .setMessage("Key replacement is not implemented yet!")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) {
                    _, _ ->
                }
//                .setNegativeButton(android.R.string.cancel) {
//                    _, _ ->
//                }
                .create()
                .show()

        // onReplaceKey(key)
    }

    fun onButtonDelete(v: View) {

        AlertDialog.Builder(context)
                .setMessage("Delete key ${key.name}?")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) {
                    _, _ ->
                    onDeleteKey(key)
                }
                .setNegativeButton(android.R.string.cancel) {
                    _, _ ->
                }
                .create()
                .show()
    }
}

class KeysActivity : AppCompatActivity() {
    lateinit var keysRoot: LinearLayout
    lateinit var addNewPasswordKeyButton: Button
    lateinit var genNewKeyButton: Button
    lateinit var scanNewKeyButton: Button
    lateinit var addKeyLayout: LinearLayout
    lateinit var keyName: EditText
    lateinit var keyPassword: EditText
    lateinit var keyPasswordConfirmation: EditText
    lateinit var buttonSaveKey: Button
    lateinit var buttonCancel: Button
    lateinit var textError: TextView
    lateinit var layoutAddNewKeyButtons: LinearLayout
    lateinit var checkboxPreferAndroidKeyStore: CheckBox

    lateinit var keyStates: MutableList<KeyStateEntry>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keys)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        keysRoot = findViewById(R.id.layoutExistingKeysRoot) ?: throw Exception("Layout error")
        addNewPasswordKeyButton = findViewById(R.id.buttonAddNewPasswordKey) ?: throw Exception("Layout error")
        genNewKeyButton = findViewById(R.id.buttonGenerateRandomKey) ?: throw Exception("Layout error")
        scanNewKeyButton = findViewById(R.id.buttonScanRandomKey) ?: throw Exception("Layout error")
        addKeyLayout = findViewById(R.id.layoutAddKey) ?: throw Exception("Layout error")
        keyName = findViewById(R.id.keyName) ?: throw Exception("Layout error")
        keyPassword = findViewById(R.id.password) ?: throw Exception("Layout error")
        keyPasswordConfirmation = findViewById(R.id.passwordConfirmation) ?: throw Exception("Layout error")
        buttonSaveKey = findViewById(R.id.buttonSaveKey) ?: throw Exception("Layout error")
        buttonCancel = findViewById(R.id.buttonCancel) ?: throw Exception("Layout error")
        textError = findViewById(R.id.textError) ?: throw Exception("Layout error")
        layoutAddNewKeyButtons = findViewById(R.id.layoutAddNewKeyButtons) ?: throw Exception("Layout error")
        checkboxPreferAndroidKeyStore = findViewById(R.id.checkBoxPreferAndroidKeyStore) ?: throw Exception("Layout error")

        addKeyLayout.visibility = View.GONE

        val keys = KeysDatabase(context = this).use { it.keys }
        keyStates = mutableListOf<KeyStateEntry>()

        for (key in keys) {
            val keyState = KeyStateEntry(
                    context = this,
                    inflater = layoutInflater,
                    key = key,
                    onReplaceKey = this::onReplaceKey,
                    onDeleteKey = this::onDeleteKey
            )

            keyStates.add(keyState)
            keysRoot.addView(keyState.layout)

        }

        addNewPasswordKeyButton.setOnClickListener(this::onButtonAddPasswordKey)
        genNewKeyButton.setOnClickListener(this::onButtonGenerateKey)
        scanNewKeyButton.setOnClickListener(this::onButtonScanNewKey)

        buttonSaveKey.setOnClickListener(this::onButtonAddPasswordKeySave)

        buttonCancel.setOnClickListener(this::onButtonAddPasswordKeyCancel)
    }

    private fun onButtonAddPasswordKey(v: View) {
        layoutAddNewKeyButtons.visibility = View.GONE
        addKeyLayout.visibility = View.VISIBLE
    }

    private fun onButtonGenerateKey(v: View) {
        val intent = Intent(this, RandomKeyQRCodeShareActivity::class.java)
        startActivity(intent)
    }

    private fun onButtonScanNewKey(v: View) {
        val intent = Intent(this, RandomKeyQRCodeScanActivity::class.java)
        startActivity(intent)
    }

    private fun onButtonAddPasswordKeySave(v: View) {

        val name = keyName.text.toString()
        val password = keyPassword.text.toString()
        val passwordConfirm = keyPasswordConfirmation.text.toString()

        val onError = {
            text: String ->
            textError.setText(text)
            textError.visibility = View.VISIBLE
        }

        if (name.isEmpty()) {
            onError("Name is empty!")
            return
        }
        else if (password != passwordConfirm) {
            onError("Passwords didn't match!")
            return
        }
        else if (password.isEmpty()) {
            onError("Password is empty")
            return
        }
        else if (password.length < 8) {
            onError("Password is way too short - 8 chars min")
            return
        }

        val key = DerivedKeyGenerator().generateForAESTwofishSerpent(password)
        KeySaveHelper().saveKey(this, name, key, checkboxPreferAndroidKeyStore.isChecked)

        reload()
    }


    private fun onButtonAddPasswordKeyCancel(v: View) {
        layoutAddNewKeyButtons.visibility = View.VISIBLE
        addKeyLayout.visibility = View.GONE
    }

    private fun onReplaceKey(key: KeyEntry){
        // Ignored - we don't have it implemented yet
    }

    private fun onDeleteKey(key: KeyEntry){

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // this will render DB key useless - so do it first thing
            AndroidKeyStore().dropKey(key.id)
        }

        KeysDatabase(context = this).use {
            db -> db.deleteKey(key.id)
        }

        reload()
    }

    fun reload() {
        startActivity(Intent(this, KeysActivity::class.java))
        finish()
    }
}
