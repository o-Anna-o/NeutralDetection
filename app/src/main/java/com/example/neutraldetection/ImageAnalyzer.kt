package com.example.neutraldetection

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import java.util.concurrent.atomic.AtomicInteger

/**
 * ImageAnalyzer переработан для использования ML Kit Face Mesh Detection.
 * Это решает проблему с загрузкой JNI-библиотек MediaPipe и обеспечивает
 * более стабильную работу на различных Android устройствах.
 */
class ImageAnalyzer(
    private val onResults: (AnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    private var frameCount = AtomicInteger(0)
    private var lastTime = System.currentTimeMillis()
    private var fps = 0f

    // Инициализация детектора ML Kit
    private val detector: FaceMeshDetector by lazy {
        val options = FaceMeshDetectorOptions.Builder()
            .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
            .build()
        FaceMeshDetection.getClient(options)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val currentTime = System.currentTimeMillis()
            frameCount.incrementAndGet()
            if (currentTime - lastTime >= 1000) {
                fps = frameCount.get() * 1000f / (currentTime - lastTime)
                frameCount.set(0)
                lastTime = currentTime
            }

            // Создаем InputImage для ML Kit, который автоматически учитывает поворот
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            // Запускаем детектор (синхронно, так как CameraX вызывает analyze в фоновом потоке)
            val faceMeshes = Tasks.await(detector.process(image))
            
            val landmarks = mutableListOf<Landmark>()
            if (faceMeshes.isNotEmpty()) {
                // Берем первое найденное лицо
                val mesh = faceMeshes[0]
                mesh.allPoints.forEach { point ->
                    landmarks.add(Landmark(
                        index = point.index,
                        x = point.position.x,
                        y = point.position.y
                    ))
                }
            }

            // Анализируемlandmarks с помощью EmotionAnalyzer (логика на основе индексов 468 точек)
            val analysis = EmotionAnalyzer.analyze(landmarks, image.width, image.height)

            // Отправляем результаты в UI
            onResults(AnalysisResult(
                fps = fps,
                mouthRatio = analysis.mouthRatio,
                eyeRatio = analysis.eyeRatio,
                eyebrowRatio = analysis.eyebrowRatio,
                confidence = analysis.confidence,
                status = analysis.status
            ))

        } catch (e: Exception) {
            Log.e("ImageAnalyzer", "Detection failed", e)
        } finally {
            // Обязательно закрываем ImageProxy
            imageProxy.close()
        }
    }
}

data class Landmark(
    val index: Int,
    val x: Float,
    val y: Float
)
