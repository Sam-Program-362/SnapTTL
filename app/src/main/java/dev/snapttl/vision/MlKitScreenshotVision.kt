package dev.snapttl.vision

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * ML Kit text recognition + image labeling, reconstructed from Mark's dual pipeline.
 *
 * Both models are the *bundled* variants (`com.google.mlkit:text-recognition`,
 * `com.google.mlkit:image-labeling`), so recognition is fully on-device and the app
 * needs no network permission.
 */
class MlKitScreenshotVision(private val context: Context) : ScreenshotVision {

    override suspend fun analyze(imageUri: String): ScreenshotVision.Result =
        withContext(Dispatchers.IO) {
            val image = InputImage.fromFilePath(context, Uri.parse(imageUri))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            try {
                val text = recognizer.process(image).awaitOrNull()?.text.orEmpty()
                // ImageLabel exposes its string as `text` (getText()); there is no
                // `label` member — that mismatch was the long-standing CI failure.
                val labels = labeler.process(image).awaitOrNull()
                    ?.map { it.text }
                    .orEmpty()
                ScreenshotVision.Result(text = text, labels = labels)
            } finally {
                recognizer.close()
                labeler.close()
            }
        }

    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else if (task.exception is CancellationException) {
                cont.cancel()
            } else {
                cont.resume(null)
            }
        }
    }
}
