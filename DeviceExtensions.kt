// ═════════════════════════════════════════════════════════════════════════════
// FILE: DeviceExtensions.kt
// Package: change this to your package name
// Contains: SMS + Contacts + Permission — all extension functions merged
// ═════════════════════════════════════════════════════════════════════════════

package com.example.musicplayer

import android.Manifest
import android.app.Activity
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────

data class SmsMessage(
    val id: Long,
    val address: String,   // Phone number
    val body: String,
    val date: Long,
    val type: Int          // 1 = Inbox, 2 = Sent
)

data class Contact(
    val id: Long,
    val name: String,
    val phoneNumber: String
)

data class PermissionResult(
    val granted: List<String>,
    val denied: List<String>
)

// ─────────────────────────────────────────────────────────────────────────────
// ENUM — Permission Groups
// ─────────────────────────────────────────────────────────────────────────────

enum class PermissionGroup {
    IMAGE,
    VIDEO,
    AUDIO,
    SMS,
    CONTACTS,
    NOTIFICATION,
    CAMERA,
    STORAGE
}

// ═════════════════════════════════════════════════════════════════════════════
// SECTION 1 — PERMISSION EXTENSIONS
// ═════════════════════════════════════════════════════════════════════════════

// ── Single permission check ──────────────────────────────────────────────────
fun Context.isPermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

// ── Multiple permissions check ───────────────────────────────────────────────
fun Context.hasPermissions(vararg permissions: String): Boolean =
    permissions.all { isPermissionGranted(it) }

// ── Check by PermissionGroup ─────────────────────────────────────────────────
fun Context.arePermissionsGranted(vararg groups: PermissionGroup): Boolean =
    getPermissionsFor(*groups).all { isPermissionGranted(it) }

// ── Version-wise permission resolver ─────────────────────────────────────────
fun getPermissionsFor(vararg groups: PermissionGroup): Array<String> {
    val permissions = mutableSetOf<String>()

    groups.forEach { group ->
        when (group) {

            // IMAGE: API 33+ = READ_MEDIA_IMAGES | 29-32 = READ_EXTERNAL | <29 = READ+WRITE
            PermissionGroup.IMAGE -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                else -> {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            // VIDEO: API 33+ = READ_MEDIA_VIDEO | 29-32 = READ_EXTERNAL | <29 = READ+WRITE
            PermissionGroup.VIDEO -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                else -> {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            // AUDIO: API 33+ = READ_MEDIA_AUDIO | 29-32 = READ_EXTERNAL | <29 = READ+WRITE
            PermissionGroup.AUDIO -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                else -> {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            // SMS: Same on all versions
            PermissionGroup.SMS -> {
                permissions.add(Manifest.permission.READ_SMS)
                permissions.add(Manifest.permission.SEND_SMS)
                permissions.add(Manifest.permission.RECEIVE_SMS)
                permissions.add(Manifest.permission.RECEIVE_MMS)
            }

            // CONTACTS: Same on all versions
            PermissionGroup.CONTACTS -> {
                permissions.add(Manifest.permission.READ_CONTACTS)
                permissions.add(Manifest.permission.WRITE_CONTACTS)
                permissions.add(Manifest.permission.GET_ACCOUNTS)
            }

            // NOTIFICATION: API 33+ only, lower versions = system managed
            PermissionGroup.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            // CAMERA: Same on all versions
            PermissionGroup.CAMERA ->
                permissions.add(Manifest.permission.CAMERA)

            // STORAGE (general): API 30+ = READ | 29 = READ | <29 = READ+WRITE
            PermissionGroup.STORAGE -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                else -> {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    return permissions.toTypedArray()
}

// ── Register launcher (declare as field in Activity) ─────────────────────────
fun ComponentActivity.registerPermissionLauncher(
    onResult: (PermissionResult) -> Unit
): ActivityResultLauncher<Array<String>> {
    return registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.filter { it.value }.keys.toList()
        val denied  = result.filter { !it.value }.keys.toList()
        onResult(PermissionResult(granted, denied))
    }
}

// ── Ask permissions for groups — version check automatic ─────────────────────
fun ComponentActivity.askPermissions(
    launcher: ActivityResultLauncher<Array<String>>,
    vararg groups: PermissionGroup
) {
    val permissions = getPermissionsFor(*groups)
    if (permissions.isEmpty()) return  // e.g. Notification on API < 33
    launcher.launch(permissions)
}

// ── Open App Settings (permanently denied case) ───────────────────────────────
fun Activity.openAppSettings() {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).also {
        it.data = Uri.fromParts("package", packageName, null)
        startActivity(it)
    }
}

// ── Open MANAGE_EXTERNAL_STORAGE settings (API 30+) ─────────────────────────
fun Activity.openManageStorageSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).also {
            it.data = Uri.fromParts("package", packageName, null)
            startActivity(it)
        }
    }
}

// ── Show denied toast ─────────────────────────────────────────────────────────
fun Context.showPermissionDeniedToast(permissionName: String) {
    Toast.makeText(
        this,
        "⚠️ $permissionName permission denied. Settings ma jaane enable karo.",
        Toast.LENGTH_LONG
    ).show()
}

// ═════════════════════════════════════════════════════════════════════════════
// SECTION 2 — SMS EXTENSIONS
// ═════════════════════════════════════════════════════════════════════════════

// ── Get all SMS ───────────────────────────────────────────────────────────────
fun Context.getAllSms(): List<SmsMessage> {
    if (!isPermissionGranted(Manifest.permission.READ_SMS)) {
        Log.e("SMS", "READ_SMS permission not granted")
        return emptyList()
    }

    val smsList   = mutableListOf<SmsMessage>()
    val uri       = Uri.parse("content://sms")
    val projection = arrayOf("_id", "address", "body", "date", "type")

    val cursor = contentResolver.query(
        uri, projection, null, null, "date DESC"
    ) ?: return emptyList()

    cursor.use {
        while (it.moveToNext()) {
            smsList.add(
                SmsMessage(
                    id      = it.getLong(it.getColumnIndexOrThrow("_id")),
                    address = it.getString(it.getColumnIndexOrThrow("address")) ?: "",
                    body    = it.getString(it.getColumnIndexOrThrow("body"))    ?: "",
                    date    = it.getLong(it.getColumnIndexOrThrow("date")),
                    type    = it.getInt(it.getColumnIndexOrThrow("type"))
                )
            )
        }
    }
    return smsList
}

// ── Remove SMS by ID ──────────────────────────────────────────────────────────
// NOTE: App must be DEFAULT SMS app to delete SMS on Android
fun Context.removeSms(smsId: Long): Boolean {
    if (!isPermissionGranted(Manifest.permission.READ_SMS)) {
        Log.e("SMS", "READ_SMS permission not granted")
        return false
    }
    val uri  = Uri.parse("content://sms/$smsId")
    val rows = contentResolver.delete(uri, null, null)
    return rows > 0
}

// ── Send SMS ──────────────────────────────────────────────────────────────────
// Auto-splits messages longer than 160 chars
fun Context.sendSms(
    toNumber: String,
    message: String,
    onSuccess: (() -> Unit)? = null,
    onFailure: ((String) -> Unit)? = null
) {
    if (!isPermissionGranted(Manifest.permission.SEND_SMS)) {
        onFailure?.invoke("SEND_SMS permission not granted")
        return
    }
    if (toNumber.isBlank() || message.isBlank()) {
        onFailure?.invoke("Number or message is empty")
        return
    }

    try {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        if (message.length > 160) {
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(toNumber, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(toNumber, null, message, null, null)
        }

        onSuccess?.invoke()
    } catch (e: Exception) {
        onFailure?.invoke(e.message ?: "Unknown error")
        e.printStackTrace()
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// SECTION 3 — CONTACTS EXTENSIONS
// ═════════════════════════════════════════════════════════════════════════════

// ── Get all contacts ──────────────────────────────────────────────────────────
fun Context.getAllContacts(): List<Contact> {
    if (!isPermissionGranted(Manifest.permission.READ_CONTACTS)) {
        Log.e("Contacts", "READ_CONTACTS permission not granted")
        return emptyList()
    }

    val contactList = mutableListOf<Contact>()
    val projection  = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )

    val cursor = contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection, null, null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    ) ?: return emptyList()

    cursor.use {
        while (it.moveToNext()) {
            contactList.add(
                Contact(
                    id          = it.getLong(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)),
                    name        = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: "",
                    phoneNumber = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))       ?: ""
                )
            )
        }
    }
    return contactList
}

// ── Edit contact phone number ─────────────────────────────────────────────────
fun Context.editContactNumber(contactId: Long, newNumber: String): Boolean {
    if (!isPermissionGranted(Manifest.permission.WRITE_CONTACTS)) {
        Log.e("Contacts", "WRITE_CONTACTS permission not granted")
        return false
    }

    val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND " +
                    "${ContactsContract.Data.MIMETYPE} = ?"

    val selectionArgs = arrayOf(
        contactId.toString(),
        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
    )

    val values = ContentValues().apply {
        put(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumber)
    }

    val rows = contentResolver.update(
        ContactsContract.Data.CONTENT_URI,
        values,
        selection,
        selectionArgs
    )
    return rows > 0
}

// ── Remove contact by ID ──────────────────────────────────────────────────────
fun Context.removeContact(contactId: Long): Boolean {
    if (!isPermissionGranted(Manifest.permission.WRITE_CONTACTS)) {
        Log.e("Contacts", "WRITE_CONTACTS permission not granted")
        return false
    }

    val uri  = Uri.withAppendedPath(
        ContactsContract.Contacts.CONTENT_URI,
        contactId.toString()
    )
    val rows = contentResolver.delete(uri, null, null)
    return rows > 0
}

// ── Edit contact number with batch (safer for multiple ops) ──────────────────
fun Context.editContactNumberSafe(
    contactId: Long,
    oldNumber: String,
    newNumber: String
): Boolean {
    if (!isPermissionGranted(Manifest.permission.WRITE_CONTACTS)) {
        Log.e("Contacts", "WRITE_CONTACTS permission not granted")
        return false
    }

    val ops = ArrayList<ContentProviderOperation>()

    val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND " +
                    "${ContactsContract.Data.MIMETYPE} = ? AND " +
                    "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"

    val selectionArgs = arrayOf(
        contactId.toString(),
        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
        oldNumber
    )

    ops.add(
        ContentProviderOperation
            .newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(selection, selectionArgs)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumber)
            .build()
    )

    return try {
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
