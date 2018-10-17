package com.github.quarck.kriptileto

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.TextView
import org.bouncycastle.crypto.engines.AESEngine

class TextViewActivity : Activity() {

    lateinit var textViewAuthStatus: TextView
    lateinit var textViewMessage: TextView
    lateinit var buttonReply: Button
    lateinit var buttonQuote: Button
    lateinit var buttonCopy: Button

    var currentKey: KeyEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_view)

        textViewAuthStatus = findViewById(R.id.textViewAuthStatus)
        textViewMessage = findViewById(R.id.textViewMessage)
        buttonReply = findViewById(R.id.buttonReply)
        buttonQuote = findViewById(R.id.buttonQuote)
        buttonCopy = findViewById(R.id.buttonCopy)

        buttonCopy.setOnClickListener(this::onButtonCopy)
        buttonReply.setOnClickListener(this::onButtonReply)
        buttonQuote.setOnClickListener(this::onButtonQuote)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    fun handleEncryptedTextIntent(text: String) {

        background {
            val keys = KeysDatabase(context = this).use { it.keys }
            var success = false

            val cryptoMessage = KriptiletoMessage()

            for (key in keys) {
                try {
                    val decrypted = cryptoMessage.decrypt(text, key)
                    if (decrypted != null) {
                        runOnUiThread {
                            textViewMessage.setText(decrypted)
                            textViewAuthStatus.setText("Decrypted and valid, key: ${key.name}")
                        }
                        currentKey = key
                        success = true
                        break
                    }
                } catch (ex: Exception) {
                }
            }

            if (!success) {
                runOnUiThread {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra(MainActivity.INTENT_EXTRA_TEXT, text)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                handleEncryptedTextIntent(uri.toString())
            }
        }
        else if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            if (text != null) {
                 handleEncryptedTextIntent(text)
            }
        }
        else if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val text = intent.getCharSequenceExtra(android.content.Intent.EXTRA_PROCESS_TEXT)
                if (text != null) {
                    handleEncryptedTextIntent(text.toString())
                }
            }
        }
    }

    private fun onButtonCopy(v: View) {
        val msg = textViewMessage.text.toString()
        if (msg.length != 0) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.clipboard_clip_label), msg)
            clipboard.setPrimaryClip(clip)
        }
    }

    private fun onButtonReply(v: View) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(MainActivity.INTENT_EXTRA_KEY_ID, currentKey?.id ?: -1L)
        startActivity(intent)
        finish()
    }

    private fun onButtonQuote(v: View) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(MainActivity.INTENT_EXTRA_KEY_ID, currentKey?.id ?: -1L)
        val text =
                textViewMessage.text.toString()
                        .split("(\\n|\\r\\n)".toRegex())
                        .map { "> $it" }
                        .joinToString("\r\n")

        intent.putExtra(MainActivity.INTENT_EXTRA_TEXT, text)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1
    }
}

