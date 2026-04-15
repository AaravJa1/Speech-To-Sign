package com.example.speech_to_sign

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class SignInterpreter(
    context: Context,
    private val onCharacterLocked: (String, String) -> Unit,
    private val onWordComplete: (String) -> Unit
) {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    private var currentWord        = ""
    private var lockedChar         = ""
    private var currentPendingChar = ""
    private var frameCount         = 0
    private var noHandCount        = 0

    // Tune these if letters lock too fast/slow
    private val CONFIDENCE_THRESHOLD = 0.01f
    private val DEBOUNCE_FRAMES      = 10       // frames before a letter locks
    private val SPACE_THRESHOLD      = 25       // no-hand frames before word completes

    init {
        labels = loadLabels(context)
        loadModel(context)
        Log.d("SignInterpreter", "Loaded ${labels.size} classes: $labels")
    }

    // ── Load labels from class_names.txt in assets ────────────────────────────

    private fun loadLabels(context: Context): List<String> {
        return try {
            context.assets.open("class_names.txt")
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("SignInterpreter", "label load failed: ${e.message}")
            ('A'..'Z').map { it.toString() }
        }
    }

    // ── Load TFLite MLP from assets ───────────────────────────────────────────

    private fun loadModel(context: Context) {
        try {
            val afd = context.assets.openFd("isl_gesture.tflite")
            val buffer = FileInputStream(afd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
            interpreter = Interpreter(buffer)
            Log.d("SignInterpreter", "Model loaded OK")
        } catch (e: Exception) {
            Log.e("SignInterpreter", "Model load failed: ${e.message}")
        }
    }

    // ── Main entry: called every frame by HandTracker ─────────────────────────
    // landmarks = FloatArray(126) or null if no hands visible

    fun processLandmarks(landmarks: FloatArray?) {
        if (landmarks == null) {
            noHandCount++
            if (noHandCount > SPACE_THRESHOLD && currentWord.isNotEmpty()) {
                onWordComplete(currentWord)
                currentWord        = ""
                lockedChar         = ""
                currentPendingChar = ""
                frameCount         = 0
            }
            return
        }

        noHandCount = 0

        val (label, conf) = runInference(landmarks)

        if (conf < CONFIDENCE_THRESHOLD) {
            frameCount = 0
            return
        }

        // Already locked this char — wait for hand to move
        if (label == lockedChar) return

        if (label == currentPendingChar) {
            frameCount++
            if (frameCount >= DEBOUNCE_FRAMES) {
                lockCharacter(label)
            }
        } else {
            currentPendingChar = label
            frameCount         = 0
        }
    }

    private fun lockCharacter(label: String) {
        lockedChar   = label
        currentWord += label
        frameCount   = 0
        onCharacterLocked(label, currentWord)
    }

    // ── MLP inference on float[126] ───────────────────────────────────────────

    private fun softmax(logits: FloatArray): FloatArray {
        val maxVal = logits.max()!!
        val exps = logits.map { Math.exp((it - maxVal).toDouble()).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    private fun runInference(landmarks: FloatArray): Pair<String, Float> {
        val interp = interpreter ?: return Pair("None", 0f)

        val input = ByteBuffer.allocateDirect(126 * 4).apply {
            order(ByteOrder.nativeOrder())
            landmarks.forEach { putFloat(it) }
            rewind()
        }

        val output = Array(1) { FloatArray(labels.size) }
        interp.run(input, output)

        val probs  = softmax(output[0])   // <-- add this
        val maxIdx = probs.indices.maxByOrNull { probs[it] }
            ?: return Pair("None", 0f)

        Log.d("SignInterpreter", "${labels[maxIdx]} : ${"%.2f".format(probs[maxIdx])}")
        return Pair(labels[maxIdx], probs[maxIdx])
    }

    fun close() {
        interpreter?.close()
    }
}
