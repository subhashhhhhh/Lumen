package com.subhashh.lumen.data

import android.net.Uri

/**
 * A MediaStore-style item for tests. Every field a test doesn't care about gets a plain default,
 * so each test states only what it depends on. Needs Robolectric (a real [Uri]).
 */
fun media(
    id: Long,
    takenAt: Long = id * 1000,
    name: String = "IMG_$id.jpg",
    relativePath: String = "DCIM/Camera/",
    bucketId: Long = relativePath.hashCode().toLong(),
    bucketName: String = relativePath.trimEnd('/').substringAfterLast('/'),
    mimeType: String = if (name.endsWith(".mp4")) "video/mp4" else "image/jpeg",
    isVideo: Boolean = mimeType.startsWith("video/"),
    isFavorite: Boolean = false,
    size: Long = 0,
) = MediaItem(
    id = id,
    uri = Uri.parse("content://media/external/${if (isVideo) "video" else "images"}/media/$id"),
    mimeType = mimeType,
    isVideo = isVideo,
    takenAt = takenAt,
    durationMs = if (isVideo) 1000 else 0,
    isFavorite = isFavorite,
    relativePath = relativePath,
    bucketId = bucketId,
    bucketName = bucketName,
    width = 4000,
    height = 3000,
    modified = takenAt / 1000,
    name = name,
    size = size,
)

/** Ids, for comparing result order without comparing whole items. */
val List<MediaItem>.ids: List<Long> get() = map { it.id }
