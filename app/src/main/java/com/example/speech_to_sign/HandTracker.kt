package com.example.speech_to_sign

import android.content.Context
import android.util.Log
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
    private val onLandmarks: (HandLandmarkerResult) -> Unit
) {
    private lateinit var handLandmarker: HandLandmarker
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun start() {
        val assets = context.assets.list("")
        android.util.Log.d("HandTracker", "Assets: ${assets?.joinToString()}")
        setupMediaPipe()
        setupCamera()
    }

    private fun setupMediaPipe() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task") // put this in assets/
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(1)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> onLandmarks(result) }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageAnalyzer
            )

        }, ContextCompat.getMainExecutor(context))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val mpImage = BitmapImageBuilder(bitmap).build()
        handLandmarker.detectAsync(mpImage, imageProxy.imageInfo.timestamp)
        imageProxy.close()
    }

    fun stop() {
        cameraExecutor.shutdown()
    }
}
//storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task