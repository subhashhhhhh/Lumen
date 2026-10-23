package com.subhashh.lumen

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.imageDecoderEnabled
import coil3.gif.AnimatedImageDecoder
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import com.subhashh.lumen.data.GridThumbnailFetcher
import com.subhashh.lumen.data.MediaThumbnailFetcher

/** App-wide image loader, shared by the main app and the viewer other apps open. */
class LumenApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            // Platform ImageDecoder handles AVIF/HEIF, which BitmapFactory can't on every device.
            .imageDecoderEnabled(true)
            .components {
                add(MediaThumbnailFetcher.Factory())
                add(GridThumbnailFetcher.Factory(context))
                add(VideoFrameDecoder.Factory())
                add(SvgDecoder.Factory())
                // Animated GIF, WebP, AVIF and HEIF play in the viewer. Grid cells stay still:
                // they come from GridThumbnail's cached stills, not through this decoder.
                add(AnimatedImageDecoder.Factory())
            }
            .build()
}
