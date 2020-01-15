package kaist.iclab.abclogger.collector

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.Telephony
import kaist.iclab.abclogger.*
import kaist.iclab.abclogger.base.BaseCollector

class MessageCollector(val context: Context) : BaseCollector {
    private val smsObserver: ContentObserver by lazy {
        object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                val timestamps = mutableListOf<Long>()

                getRecentContents(
                        contentResolver = context.contentResolver,
                        uri = Telephony.Sms.CONTENT_URI,
                        lastTime = SharedPrefs.lastAccessTimeSms,
                        timeColumn = Telephony.Sms.DATE,
                        columns = arrayOf(
                                Telephony.Sms.DATE,
                                Telephony.Sms.ADDRESS,
                                Telephony.Sms.TYPE)
                ) { cursor ->
                    val timestamp = cursor.getLong(0)
                    val number = cursor.getString(1)

                    timestamps.add(timestamp)

                    MessageEntity(
                            number = toHash(number, 0, 4),
                            messageClass = "SMS",
                            messageBox = messageBoxToString(cursor.getInt(2))
                    ).fillContact(
                            number = number,
                            contentResolver = context.contentResolver
                    ).fillBaseInfo(toMillis(timestamp = timestamp))
                }?.run {
                    putEntity(this)
                }

                SharedPrefs.lastAccessTimeSms = timestamps.max() ?: SharedPrefs.lastAccessTimeSms
            }
        }
    }

    private val mmsObserver: ContentObserver by lazy {
        object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                val timestamps = mutableListOf<Long>()

                getRecentContents(
                        contentResolver = context.contentResolver,
                        uri = Telephony.Mms.CONTENT_URI,
                        lastTime = SharedPrefs.lastAccessTimeMms,
                        timeColumn = Telephony.Mms.DATE,
                        columns = arrayOf(
                                Telephony.Mms.DATE,
                                Telephony.Mms._ID,
                                Telephony.Mms.MESSAGE_BOX)
                ) { cursor ->
                    val timestamp = cursor.getLong(0)
                    val number = getMmsAddress(cursor.getLong(1), context.contentResolver) ?: ""

                    timestamps.add(timestamp)

                    MessageEntity(
                            number = toHash(number, 0, 4),
                            messageClass = "MMS",
                            messageBox = messageBoxToString(cursor.getInt(2))
                    ).fillContact(
                            number = number,
                            contentResolver = context.contentResolver
                    ).fillBaseInfo(toMillis(timestamp = timestamp))
                }?.run {
                    putEntity(this)
                }

                SharedPrefs.lastAccessTimeMms = timestamps.max() ?: SharedPrefs.lastAccessTimeMms
            }
        }
    }

    private fun getMmsAddress(id: Long, contentResolver: ContentResolver): String? {
        return contentResolver.query(
                Uri.withAppendedPath(
                        Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, id.toString()), "addr"),
                arrayOf(Telephony.Mms.Addr.ADDRESS),
                null, null, null
        )?.use {
            return@use if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun messageBoxToString(typeInt: Int): String = when (typeInt) {
        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT -> "DRAFT"
        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED -> "FAILED"
        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX -> "INBOX"
        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX -> "OUTBOX"
        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_QUEUED -> "QUEUED"
        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT -> "SENT"
        else -> "UNDEFINED"
    }

    override fun onStart() {
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        context.contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, mmsObserver)
    }

    override fun onStop() {
        context.contentResolver.unregisterContentObserver(smsObserver)
        context.contentResolver.unregisterContentObserver(mmsObserver)
    }

    override fun checkAvailability(): Boolean = context.checkPermission(requiredPermissions)

    override fun handleActivityResult(resultCode: Int, intent: Intent?) { }

    override val requiredPermissions: List<String>
        get() = listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS
        )

    override val newIntentForSetUp: Intent?
        get() = null
}