package ru.sputnik.otk.data

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.util.Log

object NfcParser {
    private const val TAG = "NfcParser"

    fun parse(intent: Intent): String? {
        val action = intent.action
        Log.d(TAG, "parse() action=$action")

        // Принимаем все NFC-интенты (foreground dispatch даёт ACTION_TAG_DISCOVERED)
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            Log.d(TAG, "Не NFC-интент, пропускаем")
            return null
        }

        // Способ 1: читаем NDEF-сообщения из интента
        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (rawMsgs != null && rawMsgs.isNotEmpty()) {
            val msg = rawMsgs.firstOrNull() as? NdefMessage
            val record = msg?.records?.firstOrNull()
            if (record != null) {
                val text = readText(record)?.trim()?.takeIf { it.isNotBlank() }
                if (text != null) {
                    Log.d(TAG, "NDEF текст: $text")
                    return text
                }
            }
        }

        // Способ 2: fallback — UID метки (серийный номер)
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            val uid = bytesToHex(tag.id)
            Log.d(TAG, "UID метки = $uid (techList: ${tag.techList.toList()})")
            return uid
        }

        Log.d(TAG, "Не удалось распарсить NFC")
        return null
    }

    private fun readText(record: NdefRecord): String? {
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
            val payload = record.payload
            if (payload.isEmpty()) return null
            val textEncoding = if ((payload[0].toInt() and 0x80) == 0) Charsets.UTF_8 else Charsets.UTF_16
            val languageCodeLength = payload[0].toInt() and 0x3F
            return String(
                payload,
                languageCodeLength + 1,
                payload.size - languageCodeLength - 1,
                textEncoding,
            )
        }
        // Пробуем как обычный UTF-8 текст
        return String(record.payload, Charsets.UTF_8)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }
}
