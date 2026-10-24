package com.subhashh.lumen.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class GeoPoint(val latitude: Double, val longitude: Double)

@Immutable
data class CameraInfo(
    val device: String?,
    val lens: String?,
    val aperture: Double?,
    /** Seconds. */
    val exposure: Double?,
    val iso: Int?,
    val focalLengthMm: Double?,
    /** The same focal length on a 35 mm film camera: how wide or tight the shot is. */
    val focal35mm: Int? = null,
    /** Exposure compensation, in stops. */
    val exposureBias: Double? = null,
    val flashFired: Boolean? = null,
    val whiteBalanceManual: Boolean? = null,
)

/** What's inside a video file: its streams' formats. */
@Immutable
data class VideoInfo(
    /** "HEVC", "H.264", "AV1"… */
    val codec: String?,
    /** Bits per second, all streams. */
    val bitrate: Long?,
    /** "HDR10", "HLG", "Dolby Vision", or null for SDR. */
    val hdr: String?,
    /** Frames per second it was shot at, when that differs from playback (slow motion). */
    val captureFps: Float?,
    val audioCodec: String?,
    val audioChannels: Int?,
    val sampleRate: Int?,
)

@Immutable
data class MediaDetails(
    val fileName: String,
    val folder: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val camera: CameraInfo?,
    val location: GeoPoint?,
    /** False when GPS may exist but Android redacted it (ACCESS_MEDIA_LOCATION not granted). */
    val locationReadable: Boolean,
    val frameRate: Float?,
    /** The time zone the photo was taken in, from EXIF, e.g. "+02:00". */
    val offset: String? = null,
    /** Metres above sea level, from GPS. */
    val altitude: Double? = null,
    /** Compass direction the camera faced, degrees from north. */
    val direction: Double? = null,
    /** "Display P3", "sRGB", "BT.2020"… (photos). */
    val colorSpace: String? = null,
    /** Bits per colour channel, when more than 8 (photos). */
    val bitDepth: Int? = null,
    /** "Ultra HDR" for photos with a gain map. */
    val hdr: String? = null,
    /** A photo with a short video inside (Google and Samsung Motion Photos). */
    val motionPhoto: Boolean = false,
    val video: VideoInfo? = null,
    /** Where the file is, as a full path, and the storage it's on ("Internal storage", an SD card's name). */
    val path: String? = null,
    val volume: String? = null,
    /** Epoch milliseconds. */
    val dateAdded: Long? = null,
    val dateModified: Long? = null,
)

/** Reads per-item details on demand; too slow to do for every grid item. */
object MediaDetailsLoader {

    suspend fun load(context: Context, item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = ""
        val folder = item.relativePath
        var size = 0L
        var width = 0
        var height = 0
        var orientation = 0
        var path: String? = null
        var volumeName: String? = null
        var dateAdded: Long? = null
        var dateModified: Long? = null
        if (item.uri.scheme == "content" && !ExternalMedia.isMediaStore(item.uri)) {
            // Opened from another app: only what that app's provider offers.
            resolver.query(item.uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0).orEmpty()
                    size = c.getLong(1)
                }
            }
            width = item.width
            height = item.height
        } else if (item.uri.scheme == "content") {
            resolver.query(
                ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), item.id),
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT,
                    MediaStore.MediaColumns.ORIENTATION,
                    // Deprecated for file access, still the simplest way to show where a file is.
                    @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.VOLUME_NAME,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                ),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0).orEmpty()
                    size = c.getLong(1)
                    width = c.getInt(2)
                    height = c.getInt(3)
                    orientation = c.getInt(4)
                    path = c.getString(5)
                    volumeName = c.getString(6)
                    dateAdded = c.getLong(7).takeIf { it > 0 }?.times(1000)
                    dateModified = c.getLong(8).takeIf { it > 0 }?.times(1000)
                }
            }
        } else {
            // Hidden files aren't in MediaStore: read what we need from the file itself; vault
            // items carry it in their encrypted header.
            if (com.subhashh.lumen.data.vault.Vault.isVault(item)) {
                name = item.name
                size = item.size
            } else HiddenMedia.file(item)?.let { file ->
                name = file.name
                size = file.length()
                path = file.path
                dateModified = file.lastModified().takeIf { it > 0 }
            }
            width = item.width
            height = item.height
        }
        // MediaStore reports stored dimensions; show them as the photo is displayed.
        if (ExternalMedia.isMediaStore(item.uri) && orientation == 90 || orientation == 270) {
            width = height.also { height = width }
        }

        // Without ACCESS_MEDIA_LOCATION, Android strips GPS from the stream we're given.
        val canReadLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        // Only MediaStore Uris carry redacted GPS; file Uris (hidden items) are read as-is.
        val sourceUri = if (canReadLocation && ExternalMedia.isMediaStore(item.uri)) MediaStore.setRequireOriginal(item.uri) else item.uri

        var camera: CameraInfo? = null
        var location: GeoPoint? = null
        var frameRate: Float? = null
        var offset: String? = null
        var altitude: Double? = null
        var direction: Double? = null
        var colorSpace: String? = null
        var bitDepth: Int? = null
        var motionPhoto = false
        var video: VideoInfo? = null

        if (item.isVideo) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, sourceUri)
                    location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                        ?.let(::parseIso6709)
                    val frames = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                        ?.toLongOrNull()
                    if (frames != null && item.durationMs > 0) frameRate = frames * 1000f / item.durationMs
                    val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                    val capture = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                    video = readTracks(context, sourceUri, item, bitrate)
                        .copy(captureFps = capture?.takeIf { c -> frameRate?.let { c > it * 1.5f } ?: (c > 60f) })
                }
            }
        } else {
            runCatching {
                resolver.openInputStream(sourceUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    location = exif.latLong?.let { GeoPoint(it[0], it[1]) }
                    offset = (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME))
                        ?.trim()?.takeIf { it.isNotEmpty() }
                    if (location != null && exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) != null) altitude = exif.getAltitude(0.0)
                    direction = exif.getAttributeDouble(ExifInterface.TAG_GPS_IMG_DIRECTION, -1.0).takeIf { it >= 0 }
                    // Google's and Samsung's XMP both name it.
                    motionPhoto = exif.getAttribute(ExifInterface.TAG_XMP)?.let { "MotionPhoto" in it || "MicroVideo" in it } == true
                    camera = CameraInfo(
                        device = deviceName(
                            exif.getAttribute(ExifInterface.TAG_MAKE),
                            exif.getAttribute(ExifInterface.TAG_MODEL),
                        ),
                        lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.takeIf { it.isNotEmpty() },
                        aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 },
                        exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 },
                        iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 },
                        focalLengthMm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 },
                        focal35mm = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0).takeIf { it > 0 },
                        exposureBias = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)
                            ?.let { exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, 0.0) },
                        flashFired = exif.getAttribute(ExifInterface.TAG_FLASH)
                            ?.let { exif.getAttributeInt(ExifInterface.TAG_FLASH, 0) and 1 == 1 },
                        whiteBalanceManual = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)
                            ?.let { exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, 0) == 1 },
                    ).takeIf { it.device != null || it.aperture != null || it.iso != null }
                }
            }
        }

        if (!item.isVideo) {
            // The header alone says how the pixels are stored.
            runCatching {
                resolver.openInputStream(item.uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    colorSpace = options.outColorSpace?.let(::colorSpaceName)
                    bitDepth = when (options.outConfig) {
                        Bitmap.Config.RGBA_F16 -> 16
                        Bitmap.Config.RGBA_1010102 -> 10
                        else -> null
                    }
                }
            }
        }
        val hdr = if (item.isVideo) null else UltraHdr.fullHdrRatio(context, item)?.let { "Ultra HDR" }

        MediaDetails(
            fileName = name,
            folder = folder,
            sizeBytes = size,
            width = width,
            height = height,
            mimeType = item.mimeType,
            camera = camera,
            location = location,
            locationReadable = canReadLocation,
            frameRate = frameRate,
            offset = offset,
            altitude = altitude,
            direction = direction,
            colorSpace = colorSpace,
            bitDepth = bitDepth,
            hdr = hdr,
            motionPhoto = motionPhoto,
            video = video,
            path = path,
            volume = volumeLabel(context, volumeName),
            dateAdded = dateAdded,
            dateModified = dateModified,
        )
    }

    /** Each stream's format, from the file's track list. */
    private fun readTracks(context: Context, uri: android.net.Uri, item: MediaItem, bitrate: Long?): VideoInfo {
        var codec: String? = null
        var hdr: String? = null
        var audio: String? = null
        var channels: Int? = null
        var rate: Int? = null
        runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") && codec == null) {
                        codec = codecName(mime)
                        if (mime == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) hdr = "Dolby Vision"
                        else if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                            hdr = when (format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)) {
                                MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10"
                                MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
                                else -> null
                            }
                        }
                    } else if (mime.startsWith("audio/") && audio == null) {
                        audio = codecName(mime)
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                }
            } finally {
                extractor.release()
            }
        }
        // The library already spotted HDR video some other way (Dolby Vision's HEVC base layer).
        if (hdr == null && item.hdrVideo) hdr = "HDR"
        return VideoInfo(codec, bitrate, hdr, null, audio, channels, rate)
    }

    private fun codecName(mime: String) = when (mime) {
        MediaFormat.MIMETYPE_VIDEO_HEVC -> "HEVC"
        MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264"
        MediaFormat.MIMETYPE_VIDEO_AV1 -> "AV1"
        MediaFormat.MIMETYPE_VIDEO_VP9 -> "VP9"
        MediaFormat.MIMETYPE_VIDEO_VP8 -> "VP8"
        MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> "Dolby Vision"
        MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "MPEG-4"
        MediaFormat.MIMETYPE_VIDEO_H263 -> "H.263"
        MediaFormat.MIMETYPE_AUDIO_AAC -> "AAC"
        MediaFormat.MIMETYPE_AUDIO_OPUS -> "Opus"
        MediaFormat.MIMETYPE_AUDIO_AC3 -> "Dolby Digital"
        MediaFormat.MIMETYPE_AUDIO_EAC3 -> "Dolby Digital Plus"
        MediaFormat.MIMETYPE_AUDIO_AMR_NB, MediaFormat.MIMETYPE_AUDIO_AMR_WB -> "AMR"
        MediaFormat.MIMETYPE_AUDIO_MPEG -> "MP3"
        MediaFormat.MIMETYPE_AUDIO_FLAC -> "FLAC"
        MediaFormat.MIMETYPE_AUDIO_VORBIS -> "Vorbis"
        else -> mime.substringAfter('/').uppercase()
    }

    private fun colorSpaceName(space: ColorSpace): String = when (space) {
        ColorSpace.get(ColorSpace.Named.SRGB) -> "sRGB"
        ColorSpace.get(ColorSpace.Named.DISPLAY_P3) -> "Display P3"
        ColorSpace.get(ColorSpace.Named.ADOBE_RGB) -> "Adobe RGB"
        ColorSpace.get(ColorSpace.Named.BT2020) -> "BT.2020"
        ColorSpace.get(ColorSpace.Named.PRO_PHOTO_RGB) -> "ProPhoto RGB"
        ColorSpace.get(ColorSpace.Named.DCI_P3) -> "DCI-P3"
        else -> space.name
    }

    /** "Internal storage", or the SD card's own name, for a MediaStore volume. */
    private fun volumeLabel(context: Context, volume: String?): String? {
        if (volume == null) return null
        if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) return "Internal storage"
        val storage = context.getSystemService(android.os.storage.StorageManager::class.java)
        return storage.storageVolumes.firstOrNull { it.mediaStoreVolumeName == volume }?.getDescription(context) ?: volume
    }

    /** "Google Pixel 9" rather than "Google Google Pixel 9" when the model repeats the make. */
    private fun deviceName(make: String?, model: String?): String? {
        val m = make?.trim().orEmpty()
        val d = model?.trim().orEmpty()
        return when {
            d.isEmpty() -> m.ifEmpty { null }
            m.isEmpty() || d.startsWith(m, ignoreCase = true) -> d
            else -> "$m $d"
        }
    }

    /** Video location metadata, e.g. "+37.4219-122.0840/" or "+37.4219-122.0840+010.000/". */
    private fun parseIso6709(value: String): GeoPoint? {
        val match = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(value) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return if (lat == 0.0 && lon == 0.0) null else GeoPoint(lat, lon)
    }
}
