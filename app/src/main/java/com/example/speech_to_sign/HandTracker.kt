package com.example.speech_to_sign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HandTracker(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    // Now emits FloatArray(126) or null (no hands detected)
    private val onLandmarks: (FloatArray?) -> Unit
) {
    private lateinit var handLandmarker: HandLandmarker
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var latestBitmap: Bitmap? = null

    fun start() {
        setupMediaPipe()
        setupCamera()
    }

    private fun setupMediaPipe() {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> processResult(result) }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    private fun setupCamera() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(cameraExecutor, ::processFrame)
                }

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview, analyzer
            )
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        }
        val bmp = imageProxy.toBitmap()
        latestBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)

        handLandmarker.detectAsync(
            BitmapImageBuilder(latestBitmap!!).build(),
            imageProxy.imageInfo.timestamp
        )
        imageProxy.close()
    }

    private fun processResult(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) { onLandmarks(null); return }

        val leftVec  = FloatArray(63)
        val rightVec = FloatArray(63)

        val hands = result.landmarks()
        val handednesses = result.handednesses()

        if (hands.size == 1) {
            // Single hand detected — always put it in the LEFT slot
            // regardless of which anatomical hand it is, because the model
            // was trained with single-hand signs always in the left slot
            val vec = extractAndNormalize(hands[0])
            vec.copyInto(leftVec)
            // rightVec stays all zeros — matches training sentinel behavior
        } else {
            // Two hands — assign anatomically (with front-camera swap)
            for (i in hands.indices) {
                val isRight = handednesses[i].first().categoryName() == "Left"
                val vec = extractAndNormalize(hands[i])
                if (isRight) vec.copyInto(rightVec) else vec.copyInto(leftVec)
            }
        }

        val landmarks = FloatArray(126)
        leftVec.copyInto(landmarks, destinationOffset = 0)
        rightVec.copyInto(landmarks, destinationOffset = 63)
        onLandmarks(landmarks)
    }

    private fun extractAndNormalize(hand: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): FloatArray {
        val vec = FloatArray(63)
        for (j in 0 until 21) {
            vec[j * 3 + 0] = hand[j].x()
            vec[j * 3 + 1] = hand[j].y()
            vec[j * 3 + 2] = hand[j].z()
        }
        val wx = vec[0]; val wy = vec[1]; val wz = vec[2]
        for (j in 0 until 21) {
            vec[j * 3 + 0] -= wx
            vec[j * 3 + 1] -= wy
            vec[j * 3 + 2] -= wz
        }
        return vec
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
}
