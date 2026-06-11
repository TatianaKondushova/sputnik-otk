package ru.sputnik.otk.data

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import ru.sputnik.otk.AppLogger

object NfcParser {
    private const val TAG = "NfcParser"

    fun parse(intent: Intent): String? {
        val action = intent.action
        AppLogger.d(TAG, "parse() action=$action")

        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            AppLogger.d(TAG, "Не NFC-интент, пропускаем (action=$action)")
            return null
        }

        // Способ 1: читаем NDEF-сообщения из интента
        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        AppLogger.d(TAG, "NDEF messages count=${rawMsgs?.size ?: 0}")
        if (rawMsgs != null && rawMsgs.isNotEmpty()) {
            val msg = rawMsgs.firstOrNull() as? NdefMessage
            val record = msg?.records?.firstOrNull()
            if (record != null) {
                val text = readText(record)?.trim()?.takeIf { it.isNotBlank() }
                AppLogger.d(TAG, "NDEF record type=${record.type?.contentToString()}, text=$text")
                if (text != null) {
                    AppLogger.i(TAG, "NDEF текст: '$text'")
                    return text
                }
            }
        }

        // Способ 2: fallback — UID метки
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            val uid = bytesToHex(tag.id)
            AppLogger.i(TAG, "UID метки=$uid, techList=${tag.techList.toList()}")
            return uid
        }

        AppLogger.w(TAG, "Не удалось распарсить NFC (нет NDEF, нет TAG)")
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
        return String(record.payload, Charsets.UTF_8)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }
}
