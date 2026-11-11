package com.sawn.domain.feature

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.classifier.ImageClassifier

/**
 * On-device NSFW image classifier (TensorFlow Lite Task Vision).
 *
 * Model: MobileNet AutoML classifier from the open source
 * `nipunru/nsfw-detector-android` project, re-hosted in Sawn's assets so we run it
 * with the official TF Task Library instead of the deprecated Firebase MLKit stack the
 * upstream used — same weights, maintained runtime, no Google-services plugin.
 *
 * Task-vision expects the model file directly (labels come from a same-directory
 * text file when the model has none embedded), so the AutoML `manifest.json` indirection
 * from upstream is dropped: assets/nsfw/NSFW.tflite + assets/nsfw/labels.txt.
 *
 * Contract: [classify] is thread-safe (the interpreter serializes internally) and on
 * any failure returns an empty map so the caller treats the image as allowed — a broken
 * classifier must never hard-block; only a confident verdict may.
 */
class NsfwClassifier private constructor(private val classifier: ImageClassifier) {

    fun classify(bitmap: Bitmap): Map<String, Float> = runCatching {
        val image = org.tensorflow.lite.support.image.TensorImage.fromBitmap(bitmap)
        val results = classifier.classify(image) ?: return emptyMap()
        val out = HashMap<String, Float>(4)
        for (c in results) {
            val categories: List<org.tensorflow.lite.support.label.Category> = c.categories
            for (cat in categories) {
                val previous = out[cat.label] ?: 0f
                out[cat.label] = maxOf(previous, cat.score)
            }
        }
        out
    }.getOrDefault(emptyMap())

    companion object {
        private const val MODEL_PATH = "nsfw/NSFW.tflite"
        private const val LABELS_PATH = "nsfw/labels.txt"
        private const val MAX_RESULTS = 2
        private const val NUM_THREADS = 2

        @Volatile private var instance: NsfwClassifier? = null

        /** Lazily loads once per process; null when the model is missing/unreadable. */
        fun get(context: Context): NsfwClassifier? = runCatching {
            instance ?: NsfwClassifier(
                ImageClassifier.createFromFileAndOptions(
                    context,
                    MODEL_PATH,
                    ImageClassifier.ImageClassifierOptions.builder()
                        .setBaseOptions(BaseOptions.builder().setNumThreads(NUM_THREADS).build())
                        .setMaxResults(MAX_RESULTS)
                        .build(),
                ),
            ).also { instance = it }
        }.getOrNull()

        /** Test/ops seam: confirms the assets are intact without loading the interpreter. */
        fun assetsReadable(context: Context): Boolean = runCatching {
            context.assets.open(MODEL_PATH).use { s -> s.read() >= 0 } &&
                context.assets.open(LABELS_PATH).bufferedReader().readLines().any { it.isNotBlank() }
        }.getOrDefault(false)
    }
}
