package com.example.neutraldetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private lateinit var cameraPreview: PreviewView
    private lateinit var statusText: TextView
    private lateinit var fpsText: TextView
    private lateinit var mouthRatioText: TextView
    private lateinit var eyeRatioText: TextView
    private lateinit var eyebrowRatioText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var confidenceProgress: ProgressBar

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageAnalyzer: ImageAnalysis? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraPreview = findViewById(R.id.cameraPreview)
        statusText = findViewById(R.id.statusText)
        fpsText = findViewById(R.id.fpsText)
        mouthRatioText = findViewById(R.id.mouthRatioText)
        eyeRatioText = findViewById(R.id.eyeRatioText)
        eyebrowRatioText = findViewById(R.id.eyebrowRatioText)
        confidenceText = findViewById(R.id.confidenceText)
        confidenceProgress = findViewById(R.id.confidenceProgress)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Log.e(TAG, "Permissions not granted by the user.")
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(cameraPreview.surfaceProvider)
                }

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer(
                        context = this,
                        onResults = { analysisResult ->
                            runOnUiThread {
                                updateUI(analysisResult)
                            }
                        }
                    ))
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateUI(result: AnalysisResult) {
        fpsText.text = getString(R.string.fps, result.fps)
        mouthRatioText.text = getString(R.string.mouth_ratio, result.mouthRatio)
        eyeRatioText.text = getString(R.string.eye_ratio, result.eyeRatio)
        eyebrowRatioText.text = getString(R.string.eyebrow_ratio, result.eyebrowRatio)

        val confidencePercent = (result.confidence * 100).toInt()
        confidenceText.text = getString(R.string.confidence, confidencePercent.toFloat())
        confidenceProgress.progress = confidencePercent

        when (result.status) {
            EmotionStatus.NEUTRAL -> {
                statusText.text = getString(R.string.neutral_state)
                statusText.setBackgroundColor(ContextCompat.getColor(this, R.color.neutral_green))
                confidenceProgress.progressTintList = ContextCompat.getColorStateList(this, R.color.neutral_green)
            }
            EmotionStatus.NON_NEUTRAL -> {
                statusText.text = getString(R.string.non_neutral_state)
                statusText.setBackgroundColor(ContextCompat.getColor(this, R.color.non_neutral_red))
                confidenceProgress.progressTintList = ContextCompat.getColorStateList(this, R.color.non_neutral_red)
            }
            EmotionStatus.NO_FACE -> {
                statusText.text = getString(R.string.no_face)
                statusText.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
                confidenceProgress.progressTintList = ContextCompat.getColorStateList(this, android.R.color.darker_gray)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

data class AnalysisResult(
    val fps: Float,
    val mouthRatio: Float,
    val eyeRatio: Float,
    val eyebrowRatio: Float,
    val confidence: Float,
    val status: EmotionStatus
)

enum class EmotionStatus {
    NEUTRAL,
    NON_NEUTRAL,
    NO_FACE
}