package com.subhashh.lumen

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.imageDecoderEnabled
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.gif.AnimatedImageDecoder
import coil3.request.Options
import com.github.awxkee.avifcoil.decoder.HeifDecoder
import com.github.awxkee.avifcoil.decoder.animation.AnimatedAvifDecoder
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
                // AVIF through bundled libavif + dav1d, not the phone's codec: on some phones
                // (the owner's Motorola) the platform AV1 decoder can't get output buffers and
                // every AVIF comes out blank. HEIC stays on the platform decoder, which works.
                add(AvifOnly(AnimatedAvifDecoder.Factory()))
                add(AvifOnly(HeifDecoder.Factory()))
                add(SvgDecoder.Factory())
                // Animated GIF, WebP, AVIF and HEIF play in the viewer. Grid cells stay still:
                // they come from GridThumbnail's cached stills, not through this decoder.
                add(AnimatedImageDecoder.Factory())
            }
            .build()
}

/** Hands only AVIF files to [inner]; everything else goes to the next decoder. */
private class AvifOnly(private val inner: Decoder.Factory) : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? =
        if (result.mimeType == "image/avif") inner.create(result, options, imageLoader) else null
}
