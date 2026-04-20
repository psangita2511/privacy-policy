package com.kotlin.demo.Gallery
import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.musicplayer.PermissionGroup
import com.example.musicplayer.SmsMessage
import com.example.musicplayer.arePermissionsGranted
import com.example.musicplayer.askPermissions
import com.example.musicplayer.getAllSms
import com.example.musicplayer.registerPermissionLauncher
import java.io.File
import java.io.FileOutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────
//  DATA MODELS
// ─────────────────────────────────────────────
/*lateinit var sms:List<SmsMessage>
private val permLauncher = registerPermissionLauncher { result ->
    if (arePermissionsGranted(PermissionGroup.AUDIO)) {
        sms = getAllSms()
    }
}
findViewById<Button>(R.id.btnClick)?.setOnClickListener {
    if (arePermissionsGranted(PermissionGroup.SMS)) {
        sms = getAllSms()
    } else {
        askPermissions(permLauncher, PermissionGroup.SMS)
    }
}*/
data class MediaImage(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val mimeType: String,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val bucketName: String
)

data class MediaVideo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val mimeType: String,
    val duration: Long,          // milliseconds
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val bucketName: String
)

data class MediaAudio(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val mimeType: String,
    val duration: Long,          // milliseconds
    val title: String,
    val artist: String,
    val album: String,
    val dateAdded: Long
)

// ─────────────────────────────────────────────
//  CONTEXT — GALLERY IMAGES
// ─────────────────────────────────────────────

/** Return all images from MediaStore sorted by newest first. */
fun Context.getAllGalleryImages(
    limit: Int = Int.MAX_VALUE,
    mimeFilter: String? = null          // e.g. "image/jpeg"
): List<MediaImage> {
    val images = mutableListOf<MediaImage>()

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )

    val selection = mimeFilter?.let { "${MediaStore.Images.Media.MIME_TYPE} = ?" }
    val selectionArgs = mimeFilter?.let { arrayOf(it) }
    val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

    contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        val idCol        = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol      = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol      = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val mimeCol      = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        val dateCol      = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        val widthCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val heightCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val bucketCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

        var count = 0
        while (cursor.moveToNext() && count < limit) {
            val id = cursor.getLong(idCol)
            images += MediaImage(
                id          = id,
                uri         = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                displayName = cursor.getString(nameCol).orEmpty(),
                size        = cursor.getLong(sizeCol),
                mimeType    = cursor.getString(mimeCol).orEmpty(),
                dateTaken   = cursor.getLong(dateCol),
                width       = cursor.getInt(widthCol),
                height      = cursor.getInt(heightCol),
                bucketName  = cursor.getString(bucketCol).orEmpty()
            )
            count++
        }
    }
    return images
}

/** Return images from a specific folder / album name. */
fun Context.getImagesFromAlbum(albumName: String): List<MediaImage> {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )
    val selection     = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
    val selectionArgs = arrayOf(albumName)

    val images = mutableListOf<MediaImage>()
    contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        "${MediaStore.Images.Media.DATE_TAKEN} DESC"
    )?.use { cursor ->
        val idCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val mimeCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        val dateCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        val wCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val hCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val bCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            images += MediaImage(
                id          = id,
                uri         = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                displayName = cursor.getString(nameCol).orEmpty(),
                size        = cursor.getLong(sizeCol),
                mimeType    = cursor.getString(mimeCol).orEmpty(),
                dateTaken   = cursor.getLong(dateCol),
                width       = cursor.getInt(wCol),
                height      = cursor.getInt(hCol),
                bucketName  = cursor.getString(bCol).orEmpty()
            )
        }
    }
    return images
}

/** Return all unique album names. */
fun Context.getImageAlbums(): List<String> =
    getAllGalleryImages()
        .map { it.bucketName }
        .distinct()
        .filter { it.isNotBlank() }
        .sorted()

/** Save a Bitmap to the gallery and return its Uri. */
fun Context.saveBitmapToGallery(
    bitmap: Bitmap,
    filename: String = "IMG_${System.currentTimeMillis()}",
    mimeType: String = "image/jpeg",
    quality: Int = 90
): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    try {
        contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        }
        return uri
    } catch (e: Exception) {
        contentResolver.delete(uri, null, null)
        Log.e("Extension", "saveBitmapToGallery failed", e)
        return null
    }
}

/** Delete an image from MediaStore. */
fun Context.deleteImageFromGallery(uri: Uri): Boolean =
    try { contentResolver.delete(uri, null, null) > 0 } catch (e: Exception) { false }

// ─────────────────────────────────────────────
//  CONTEXT — GALLERY VIDEOS
// ─────────────────────────────────────────────

/** Return all videos from MediaStore sorted by newest first. */
fun Context.getAllGalleryVideos(limit: Int = Int.MAX_VALUE): List<MediaVideo> {
    val videos = mutableListOf<MediaVideo>()
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.DATE_TAKEN,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME
    )

    contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        null, null,
        "${MediaStore.Video.Media.DATE_TAKEN} DESC"
    )?.use { cursor ->
        val idCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val sizeCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val mimeCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
        val durCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val dateCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
        val wCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
        val hCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
        val bucketCol= cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

        var count = 0
        while (cursor.moveToNext() && count < limit) {
            val id = cursor.getLong(idCol)
            videos += MediaVideo(
                id          = id,
                uri         = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                displayName = cursor.getString(nameCol).orEmpty(),
                size        = cursor.getLong(sizeCol),
                mimeType    = cursor.getString(mimeCol).orEmpty(),
                duration    = cursor.getLong(durCol),
                dateTaken   = cursor.getLong(dateCol),
                width       = cursor.getInt(wCol),
                height      = cursor.getInt(hCol),
                bucketName  = cursor.getString(bucketCol).orEmpty()
            )
            count++
        }
    }
    return videos
}

/** Generate a thumbnail Bitmap for a video Uri. */
fun Context.getVideoThumbnail(videoUri: Uri, width: Int = 512, height: Int = 384): Bitmap? =
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, videoUri)
        val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        frame?.let { ThumbnailUtils.extractThumbnail(it, width, height) }
    } catch (e: Exception) {
        Log.e("Extension", "getVideoThumbnail failed", e)
        null
    }

/** Get video duration in ms from Uri. */
fun Context.getVideoDuration(videoUri: Uri): Long =
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, videoUri)
        val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
        dur
    } catch (e: Exception) { 0L }

// ─────────────────────────────────────────────
//  CONTEXT — GALLERY AUDIO
// ─────────────────────────────────────────────

/** Return all audio files from MediaStore sorted by newest first. */
fun Context.getAllGalleryAudio(limit: Int = Int.MAX_VALUE): List<MediaAudio> {
    val audioList = mutableListOf<MediaAudio>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DATE_ADDED
    )

    contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        "${MediaStore.Audio.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val sizeCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val mimeCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val durCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val dateCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

        var count = 0
        while (cursor.moveToNext() && count < limit) {
            val id = cursor.getLong(idCol)
            audioList += MediaAudio(
                id          = id,
                uri         = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                displayName = cursor.getString(nameCol).orEmpty(),
                size        = cursor.getLong(sizeCol),
                mimeType    = cursor.getString(mimeCol).orEmpty(),
                duration    = cursor.getLong(durCol),
                title       = cursor.getString(titleCol).orEmpty(),
                artist      = cursor.getString(artCol).orEmpty(),
                album       = cursor.getString(albCol).orEmpty(),
                dateAdded   = cursor.getLong(dateCol)
            )
            count++
        }
    }
    return audioList
}

/** Return audio files for a specific album. */
fun Context.getAudioByAlbum(albumName: String): List<MediaAudio> {
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DATE_ADDED
    )
    val result = mutableListOf<MediaAudio>()
    contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.ALBUM} = ?",
        arrayOf(albumName),
        "${MediaStore.Audio.Media.TRACK} ASC"
    )?.use { cursor ->
        val idCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val sizeCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val mimeCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val durCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val dateCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            result += MediaAudio(id, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                cursor.getString(nameCol).orEmpty(), cursor.getLong(sizeCol), cursor.getString(mimeCol).orEmpty(),
                cursor.getLong(durCol), cursor.getString(titleCol).orEmpty(), cursor.getString(artCol).orEmpty(),
                cursor.getString(albCol).orEmpty(), cursor.getLong(dateCol))
        }
    }
    return result
}

/** Get album art Bitmap for an audio Uri. */
fun Context.getAlbumArt(audioUri: Uri): Bitmap? =
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, audioUri)
        val art = retriever.embeddedPicture
        retriever.release()
        art?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    } catch (e: Exception) { null }

// ─────────────────────────────────────────────
//  URI — FILE INFO
// ─────────────────────────────────────────────

/** Get real file path from any Uri (content / file). */
fun Uri.getRealPath(context: Context): String? {
    if (scheme == "file") return path
    if (scheme == "content") {
        context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val cacheFile = File(context.cacheDir, name)
                context.contentResolver.openInputStream(this)?.use { input ->
                    FileOutputStream(cacheFile).use { input.copyTo(it) }
                }
                return cacheFile.absolutePath
            }
        }
    }
    return null
}

/** Get file name from a Uri. */
fun Uri.getFileName(context: Context): String {
    if (scheme == "file") return File(path!!).name
    context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst())
            return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)).orEmpty()
    }
    return lastPathSegment.orEmpty()
}

/** Get file size in bytes from a Uri. */
fun Uri.getFileSize(context: Context): Long {
    context.contentResolver.query(this, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst())
            return cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
    }
    return 0L
}

/** Get MIME type from a Uri. */
fun Uri.getMimeType(context: Context): String =
    context.contentResolver.getType(this) ?: "application/octet-stream"

// ─────────────────────────────────────────────
//  LONG / INT — DURATION FORMATTING
// ─────────────────────────────────────────────

/** Format milliseconds → "mm:ss" */
fun Long.toTimeFormat(): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return "%02d:%02d".format(minutes, seconds)
}

/** Format milliseconds → "hh:mm:ss" (for videos > 1 hour) */
fun Long.toFullTimeFormat(): String {
    val hours   = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

/** Human-readable file size: "1.5 MB" */
fun Long.toReadableFileSize(): String = when {
    this >= 1_073_741_824L -> "%.2f GB".format(this / 1_073_741_824.0)
    this >= 1_048_576L     -> "%.2f MB".format(this / 1_048_576.0)
    this >= 1_024L         -> "%.2f KB".format(this / 1_024.0)
    else                   -> "$this B"
}

// ─────────────────────────────────────────────
//  DATE CONVERSION
// ─────────────────────────────────────────────

// Common patterns
const val DATE_PATTERN_DISPLAY   = "dd MMM yyyy"          // 15 Jan 2025
const val DATE_PATTERN_FULL      = "dd MMMM yyyy"         // 15 January 2025
const val DATE_PATTERN_ISO       = "yyyy-MM-dd"           // 2025-01-15
const val DATE_PATTERN_ISO_TIME  = "yyyy-MM-dd HH:mm:ss"  // 2025-01-15 14:30:00
const val DATE_PATTERN_API       = "yyyy-MM-dd'T'HH:mm:ss'Z'"
const val DATE_PATTERN_SLASH     = "dd/MM/yyyy"           // 15/01/2025
const val DATE_PATTERN_TIME      = "HH:mm"                // 14:30
const val DATE_PATTERN_FULL_TIME = "hh:mm a"              // 02:30 PM
const val DATE_PATTERN_GUJARATI  = "dd-MM-yyyy"           // 15-01-2025

/** Convert a timestamp (ms) to formatted date string. */
fun Long.toDateString(pattern: String = DATE_PATTERN_DISPLAY, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat(pattern, locale).format(Date(this))

/** Convert a Date to formatted string. */
fun Date.format(pattern: String = DATE_PATTERN_DISPLAY, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat(pattern, locale).format(this)

/** Parse a date string into a Date object (returns null on failure). */
fun String.toDate(pattern: String = DATE_PATTERN_ISO, locale: Locale = Locale.getDefault()): Date? =
    try { SimpleDateFormat(pattern, locale).parse(this) } catch (e: ParseException) { null }

/** Convert date string from one format to another. */
fun String.convertDateFormat(fromPattern: String, toPattern: String, locale: Locale = Locale.getDefault()): String {
    val date = toDate(fromPattern, locale) ?: return this
    return date.format(toPattern, locale)
}

/** Relative time label: "Today", "Yesterday", "3 days ago", "2 weeks ago" etc. */
fun Long.toRelativeTime(context: Context? = null): String {
    val now  = System.currentTimeMillis()
    val diff = now - this
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours   = minutes / 60
    val days    = hours / 24
    val weeks   = days / 7
    val months  = days / 30
    val years   = days / 365
    return when {
        seconds < 60    -> "Just now"
        minutes < 60    -> "${minutes}m ago"
        hours < 24      -> "${hours}h ago"
        days == 1L      -> "Yesterday"
        days < 7        -> "${days} days ago"
        weeks < 5       -> "${weeks} week${if (weeks > 1) "s" else ""} ago"
        months < 12     -> "${months} month${if (months > 1) "s" else ""} ago"
        else            -> "${years} year${if (years > 1) "s" else ""} ago"
    }
}

/** Check if a timestamp is today. */
fun Long.isToday(): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = this@isToday }
    val cal2 = Calendar.getInstance()
    return cal1.get(Calendar.YEAR)         == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

/** Check if a timestamp is yesterday. */
fun Long.isYesterday(): Boolean {
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val target = Calendar.getInstance().apply { timeInMillis = this@isYesterday }
    return target.get(Calendar.YEAR)         == cal.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
}

/** Get start of day timestamp for a given timestamp. */
fun Long.startOfDay(): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = this@startOfDay
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/** Get end of day timestamp for a given timestamp. */
fun Long.endOfDay(): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = this@endOfDay
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }
    return cal.timeInMillis
}

/** Add days to a timestamp. */
fun Long.addDays(days: Int): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = this@addDays; add(Calendar.DAY_OF_YEAR, days) }
    return cal.timeInMillis
}

/** Get current timestamp as formatted string. */
fun currentDateString(pattern: String = DATE_PATTERN_DISPLAY): String =
    System.currentTimeMillis().toDateString(pattern)

/** Get age in years from a birth date timestamp. */
fun Long.ageInYears(): Int {
    val birth   = Calendar.getInstance().apply { timeInMillis = this@ageInYears }
    val today   = Calendar.getInstance()
    var age     = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
    if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age--
    return age
}

// ─────────────────────────────────────────────
//  TOAST EXTENSIONS
// ─────────────────────────────────────────────

/** Short toast. */
fun Context.showToast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

/** Long toast. */
fun Context.showLongToast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()

/** Short toast with string resource. */
fun Context.showToast(resId: Int) =
    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

/** Long toast with string resource. */
fun Context.showLongToast(resId: Int) =
    Toast.makeText(this, resId, Toast.LENGTH_LONG).show()

/** Toast shown on UI thread — safe to call from background threads. */
fun Context.showToastOnUiThread(message: String) {
    (this as? Activity)?.runOnUiThread { showToast(message) }
        ?: android.os.Handler(android.os.Looper.getMainLooper()).post { showToast(message) }
}

// ─────────────────────────────────────────────
//  PERMISSION HELPERS
// ─────────────────────────────────────────────

/** Check if a permission is granted. */
fun Context.isPermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Check READ_MEDIA / READ_EXTERNAL_STORAGE based on API level. */
fun Context.hasMediaReadPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES) &&
                isPermissionGranted(Manifest.permission.READ_MEDIA_VIDEO)  &&
                isPermissionGranted(Manifest.permission.READ_MEDIA_AUDIO)
    else
        isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)

// ─────────────────────────────────────────────
//  BITMAP EXTENSIONS
// ─────────────────────────────────────────────

/** Resize a Bitmap to the given max dimension preserving aspect ratio. */
fun Bitmap.resize(maxWidth: Int, maxHeight: Int): Bitmap {
    val ratio   = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
    val newW    = (width  * ratio).toInt()
    val newH    = (height * ratio).toInt()
    return Bitmap.createScaledBitmap(this, newW, newH, true)
}

/** Compress a Bitmap to a ByteArray. */
fun Bitmap.toByteArray(format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 80): ByteArray {
    val stream = java.io.ByteArrayOutputStream()
    compress(format, quality, stream)
    return stream.toByteArray()
}

/** Decode a ByteArray to a Bitmap. */
fun ByteArray.toBitmap(): Bitmap? =
    try { BitmapFactory.decodeByteArray(this, 0, size) } catch (e: Exception) { null }

// ─────────────────────────────────────────────
//  STRING UTILITIES
// ─────────────────────────────────────────────

/** Return null if string is blank, else the string itself. */
fun String?.orNullIfBlank(): String? = if (isNullOrBlank()) null else this

/** Capitalize first letter only. */
fun String.capitalizeFirst(): String =
    if (isEmpty()) this else this[0].uppercaseChar() + substring(1).lowercase()

/** Check valid email format. */
fun String.isValidEmail(): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()

/** Check valid mobile number (10-digit Indian format). */
fun String.isValidMobile(): Boolean =
    matches(Regex("^[6-9]\\d{9}$"))

/** Truncate string with ellipsis. */
fun String.truncate(max: Int, ellipsis: String = "…"): String =
    if (length <= max) this else take(max) + ellipsis

// ─────────────────────────────────────────────
//  COLLECTION UTILITIES
// ─────────────────────────────────────────────

/** Safe list access — returns null if index is out of bounds. */
fun <T> List<T>.getOrNullSafe(index: Int): T? =
    if (index in indices) this[index] else null

/** Chunk a list and run a callback on each chunk. */
fun <T> List<T>.forEachChunk(size: Int, action: (List<T>) -> Unit) =
    chunked(size).forEach(action)

// ─────────────────────────────────────────────
//  INTENT HELPERS
// ─────────────────────────────────────────────

/** Open gallery picker for images (single select). */
fun Activity.openImagePicker(requestCode: Int) {
    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    intent.type = "image/*"
    startActivityForResult(intent, requestCode)
}

/** Open gallery picker for videos (single select). */
fun Activity.openVideoPicker(requestCode: Int) {
    val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    intent.type = "video/*"
    startActivityForResult(intent, requestCode)
}

/** Open gallery picker for audio (single select). */
fun Activity.openAudioPicker(requestCode: Int) {
    val intent = Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    intent.type = "audio/*"
    startActivityForResult(intent, requestCode)
}

/** Open a file picker for any media type. */
fun Activity.openMediaPicker(requestCode: Int, mimeType: String = "*/*") {
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = mimeType; addCategory(Intent.CATEGORY_OPENABLE) }
    startActivityForResult(Intent.createChooser(intent, "Select Media"), requestCode)
}

/** Share an image Uri via chooser. */
fun Context.shareImage(uri: Uri, title: String = "Share Image") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type  = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, title))
}

/** Share plain text. */
fun Context.shareText(text: String, title: String = "Share") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, title))
}

// ─────────────────────────────────────────────
//  USAGE EXAMPLES (comment out before shipping)
// ─────────────────────────────────────────────
/*

// --- IMAGES ---
val images = getAllGalleryImages()
val albums = getImageAlbums()
val cameraPhotos = getImagesFromAlbum("Camera")
val savedUri = saveBitmapToGallery(myBitmap, filename = "profile_pic")

// --- VIDEOS ---
val videos = getAllGalleryVideos()
val thumb  = getVideoThumbnail(videos.first().uri)
val dur    = getVideoDuration(videos.first().uri)

// --- AUDIO ---
val songs     = getAllGalleryAudio()
val albumSongs = getAudioByAlbum("Beats")
val art        = getAlbumArt(songs.first().uri)
println(songs.first().duration.toTimeFormat())   // "03:45"
println(songs.first().size.toReadableFileSize())  // "4.23 MB"

// --- DATE ---
val nowStr   = currentDateString()                        // "20 Apr 2025"
val isoStr   = System.currentTimeMillis().toDateString(DATE_PATTERN_ISO)
val relative = System.currentTimeMillis().toRelativeTime() // "Just now"
val parsed   = "2025-01-15".toDate(DATE_PATTERN_ISO)
val converted = "15/01/2025".convertDateFormat(DATE_PATTERN_SLASH, DATE_PATTERN_FULL)
val age      = someBirthTimestamp.ageInYears()

// --- TOAST ---
showToast("Hello!")
showLongToast("Done processing ${images.size} images")
showToastOnUiThread("Background task finished")

// --- PICKER INTENT ---
openImagePicker(REQUEST_IMAGE)
openAudioPicker(REQUEST_AUDIO)
shareImage(myUri)

*/