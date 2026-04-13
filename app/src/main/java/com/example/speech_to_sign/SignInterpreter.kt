package com.example.speech_to_sign

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class SignInterpreter(
    context: Context,
    private val onCharacterLocked: (String, String) -> Unit, // Returns (currentChar, currentWord)
    private val onWordComplete: (String) -> Unit
) {
    private var tflite: Interpreter? = null

    // --- STATE MACHINE VARIABLES ---
    private var currentWord = ""
    private var lockedChar = ""
    private var frameCount = 0
    private var noHandCount = 0

    private val DEBOUNCE_THRESHOLD = 10 // Frames needed to confirm a letter
    private val SPACE_THRESHOLD = 30    // Frames of no hands to trigger a space/word end

    // Make sure this matches the exact sorted order from your Python script!
    // Example assumes 1-9, then A-Z (Total 35 classes)
    private val labels = arrayOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9",
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
        "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    )

    init {
        // Load the TFLite model from assets
        try {
            val assetFileDescriptor = context.assets.openFd("isl_model.tflite")
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val buffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            tflite = Interpreter(buffer)
        } catch (e: Exception) {
            Log.e("SignInterpreter", "Error loading model", e)
        }
    }

    fun processLandmarks(result: HandLandmarkerResult) {
        // 1. Check for "Space" (No hands detected)
        if (result.landmarks().isEmpty()) {
            noHandCount++
            if (noHandCount == SPACE_THRESHOLD && currentWord.isNotEmpty()) {
                // The user put their hands down! Word is complete.
                onWordComplete(currentWord)
                currentWord = ""
                lockedChar = ""
            }
            return
        }

        // We see hands! Reset the space counter.
        noHandCount = 0

        // 2. Extract and Normalize Data (The Python Logic in Kotlin)
        val inputData = FloatArray(126) { 0f } // 126 zeros default

        result.landmarks().forEachIndexed { index, handLandmark ->
            // Get handedness ('Left' or 'Right')
            val handedness = result.handednesses()[index].first().categoryName()

            // Wrist is landmark 0
            val wristX = handLandmark[0].x()
            val wristY = handLandmark[0].y()
            val wristZ = handLandmark[0].z()

            val handData = FloatArray(63)
            var dataIndex = 0
            for (lm in handLandmark) {
                handData[dataIndex++] = lm.x() - wristX
                handData[dataIndex++] = lm.y() - wristY
                handData[dataIndex++] = lm.z() - wristZ
            }

            // Put into the correct slot (Left=0..62, Right=63..125)
            if (handedness == "Left") {
                System.arraycopy(handData, 0, inputData, 0, 63)
            } else {
                System.arraycopy(handData, 0, inputData, 63, 63)
            }
        }

        // 3. Run Inference
        val inputBuffer = Array(1) { inputData }
        val outputBuffer = Array(1) { FloatArray(labels.size) }

        tflite?.run(inputBuffer, outputBuffer)

        // 4. Find the highest probability
        val probabilities = outputBuffer[0]
        var maxIndex = 0
        for (i in probabilities.indices) {
            if (probabilities[i] > probabilities[maxIndex]) {
                maxIndex = i
            }
        }

        val maxProbability = probabilities[maxIndex]

        // --- THE CONFIDENCE FILTER ---
        // If the model is less than 60% sure, ignore this frame entirely!
        if (maxProbability < 0.60f) {
            frameCount = 0 // Reset debouncer
            return
        }
        
        val predictedChar = labels[maxIndex]

        // 5. The Debouncer Logic
        if (predictedChar == lockedChar) {
            // Already locked this letter, ignore duplicates (solves "HHHH" -> "H")
            return
        }

        // We are seeing a new character! Wait for it to stabilize.
        if (predictedChar == currentPendingChar) {
            frameCount++
            if (frameCount >= DEBOUNCE_THRESHOLD) {
                // Locked in!
                lockedChar = predictedChar
                currentWord += lockedChar
                frameCount = 0
                onCharacterLocked(lockedChar, currentWord)
            }
        } else {
            // Glitch or transition frame, reset counter
            currentPendingChar = predictedChar
            frameCount = 0
        }
    }

    private var currentPendingChar = "" // Temporary var for the debouncer

    fun close() {
        tflite?.close()
    }
}