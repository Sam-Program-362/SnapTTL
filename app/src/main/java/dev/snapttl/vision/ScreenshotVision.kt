package dev.snapttl.vision

/**
 * On-device OCR + image-labeling boundary. The clean-room app defines the contract
 * here so the indexing worker is testable without ML Kit and so a real recognizer
 * can be substituted without touching the worker.
 */
interface ScreenshotVision {

    data class Result(val text: String, val labels: List<String>)

    /** Extracts visible text and scene/object labels from the given image URI. */
    suspend fun analyze(imageUri: String): Result

    object Unavailable : ScreenshotVision {
        override suspend fun analyze(imageUri: String): Result = Result(text = "", labels = emptyList())
    }
}
