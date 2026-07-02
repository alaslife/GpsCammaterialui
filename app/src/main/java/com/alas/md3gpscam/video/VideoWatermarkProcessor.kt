package com.alas.md3gpscam.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Transformer
import com.alas.md3gpscam.image.ImageProcessor
import com.alas.md3gpscam.location.GpsLocation
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object VideoWatermarkProcessor {

    suspend fun applyWatermark(
        context: Context,
        inputVideoUri: Uri,
        location: GpsLocation,
        templateId: String,
        bearing: Float,
        speedUnit: String,
        altitudeUnit: String,
        customNote: String? = null
    ): Uri = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var videoWidth = 1920
        var videoHeight = 1080
        var rotation = 0

        try {
            retriever.setDataSource(context, inputVideoUri)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            if (widthStr != null) videoWidth = widthStr.toInt()
            if (heightStr != null) videoHeight = heightStr.toInt()
            if (rotationStr != null) rotation = rotationStr.toInt()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }

        val actualWidth = if (rotation == 90 || rotation == 270) videoHeight else videoWidth
        val actualHeight = if (rotation == 90 || rotation == 270) videoWidth else videoHeight

        val watermarkBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888)
        watermarkBitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        val canvas = Canvas(watermarkBitmap)
        
        ImageProcessor.drawStamp(
            canvas = canvas,
            width = actualWidth,
            height = actualHeight,
            location = location,
            templateId = templateId,
            bearing = bearing,
            speedUnit = speedUnit,
            altitudeUnit = altitudeUnit,
            customNote = customNote
        )

        val outputTempFile = File(context.cacheDir, "watermarked_video_${System.currentTimeMillis()}.mp4")
        val mediaItem = MediaItem.fromUri(inputVideoUri)

        val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(watermarkBitmap)
        val overlayEffect = OverlayEffect(ImmutableList.of(bitmapOverlay))

        val effects = Effects(
            emptyList(),
            listOf(overlayEffect)
        )

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()

        withContext(Dispatchers.Main) {
            suspendCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .build()

                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        watermarkBitmap.recycle()
                        continuation.resume(Uri.fromFile(outputTempFile))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        watermarkBitmap.recycle()
                        exportException.printStackTrace()
                        continuation.resume(inputVideoUri)
                    }
                })

                try {
                    transformer.start(editedMediaItem, outputTempFile.absolutePath)
                } catch (e: Exception) {
                    watermarkBitmap.recycle()
                    e.printStackTrace()
                    continuation.resume(inputVideoUri)
                }
            }
        }
    }
}
