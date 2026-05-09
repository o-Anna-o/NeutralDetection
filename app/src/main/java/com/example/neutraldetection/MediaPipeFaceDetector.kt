package com.example.neutraldetection

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.util.concurrent.atomic.AtomicReference

/**
 * Класс для детекции лица и извлечения landmarks с использованием MediaPipe Face Mesh.
 * В реальном приложении требуется инициализация с моделью и конфигурацией.
 */
class MediaPipeFaceDetector(context: Context) {

    private var faceLandmarker: FaceLandmarker? = null
    private val isInitialized = AtomicReference(false)

    init {
        initializeFaceLandmarker(context)
    }

    private fun initializeFaceLandmarker(context: Context) {
        try {
            // В реальном проекте нужно загрузить модель из assets
            // val modelPath = "face_landmarker.task"
            // val baseOptions = BaseOptions.builder().setModelAssetPath(modelPath).build()

            val options = com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions.builder()
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, inputImage ->
                    // Обработка результатов
                }
                .build()

            // faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            isInitialized.set(true)
        } catch (e: Exception) {
            e.printStackTrace()
            isInitialized.set(false)
        }
    }

    /**
     * Детектирует лицо в кадре (Mat) и возвращает список landmarks.
     * Если MediaPipe не инициализирован, возвращает пустой список.
     */
    fun detectFace(frame: Mat): List<Landmark> {
        if (!isInitialized.get()) {
            return emptyList()
        }

        // Конвертация Mat в Bitmap (упрощенно)
        val bitmap = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(frame, bitmap)

        // В реальном использовании:
        // val mpImage = BitmapImageBuilder(bitmap).build()
        // val result = faceLandmarker?.detect(mpImage)

        // Заглушка: возвращаем mock landmarks для демонстрации
        return generateMockLandmarks(frame.width(), frame.height())
    }

    private fun generateMockLandmarks(width: Int, height: Int): List<Landmark> {
        val landmarks = mutableListOf<Landmark>()

        // Нос
        landmarks.add(Landmark(1, width / 2f, height / 2f))

        // Губы
        landmarks.add(Landmark(13, width / 2f, height / 2f + 20))  // верхняя губа
        landmarks.add(Landmark(14, width / 2f, height / 2f + 40))  // нижняя губа
        landmarks.add(Landmark(78, width / 2f - 30, height / 2f + 30))  // левый угол
        landmarks.add(Landmark(308, width / 2f + 30, height / 2f + 30)) // правый угол

        // Левый глаз
        landmarks.add(Landmark(33, width / 2f - 50, height / 2f - 30))
        landmarks.add(Landmark(133, width / 2f - 30, height / 2f - 30))
        landmarks.add(Landmark(157, width / 2f - 40, height / 2f - 20))
        landmarks.add(Landmark(158, width / 2f - 40, height / 2f - 40))

        // Правый глаз
        landmarks.add(Landmark(362, width / 2f + 50, height / 2f - 30))
        landmarks.add(Landmark(263, width / 2f + 30, height / 2f - 30))
        landmarks.add(Landmark(386, width / 2f + 40, height / 2f - 20))
        landmarks.add(Landmark(387, width / 2f + 40, height / 2f - 40))

        // Брови
        landmarks.add(Landmark(107, width / 2f - 40, height / 2f - 50))  // левая внутренняя
        landmarks.add(Landmark(66, width / 2f - 60, height / 2f - 50))   // левая внешняя
        landmarks.add(Landmark(336, width / 2f + 40, height / 2f - 50))  // правая внутренняя
        landmarks.add(Landmark(296, width / 2f + 60, height / 2f - 50))  // правая внешняя

        return landmarks
    }

    fun release() {
        faceLandmarker?.close()
        faceLandmarker = null
        isInitialized.set(false)
    }
}

/**
 * Вспомогательная функция для конвертации MediaPipe NormalizedLandmark в наш Landmark.
 */
private fun com.google.mediapipe.tasks.components.containers.NormalizedLandmark.toLandmark(
    index: Int,
    imageWidth: Int,
    imageHeight: Int
): Landmark {
    return Landmark(
        index = index,
        x = this.x() * imageWidth,
        y = this.y() * imageHeight
    )
}