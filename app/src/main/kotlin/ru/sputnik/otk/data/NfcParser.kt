package ru.sputnik.otk.data

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter

object NfcParser {
    fun parse(intent: Intent): String? {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED != intent.action) return null
        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES) ?: return null
        val msg = rawMsgs.firstOrNull() as? NdefMessage ?: return null
        val record = msg.records.firstOrNull() ?: return null
        return readText(record)?.trim()?.takeIf { it.isNotBlank() }
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
}
