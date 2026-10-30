package dev.snapttl.vision

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** ML Kit text recognition + image labeling, reconstructed from Mark's dual pipeline. */
class MlKitScreenshotVision(private val context: Context) : ScreenshotVision {

    override suspend fun analyze(imageUri: String): ScreenshotVision.Result {
        val image = InputImage.fromFilePath(context, Uri.parse(imageUri))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) as TextRecognizer
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) as ImageLabeler
        return try {
            val text = recognizer.process(image).awaitOrNull()?.text.orEmpty()
            val labels = labeler.process(image).awaitOrNull()?.map { it.label }.orEmpty()
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
