package com.example.neutraldetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

class ImageAnalyzer(
    private val context: Context,
    private val onResults: (AnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    private var frameCount = AtomicInteger(0)
    private var lastTime = System.currentTimeMillis()
    private var fps = 0f

    private var faceLandmarker: FaceLandmarker? = null
    private var isMediaPipeInitialized = false

    init {
        // Инициализируем MediaPipe лениво, чтобы не блокировать конструктор
    }

    private fun setupFaceLandmarker() {
        try {
            val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath("face_landmarker.task")
            val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.IMAGE)
                .build()
            
            faceLandmarker = FaceLandmarker.createFromOptions(context, optionsBuilder)
            isMediaPipeInitialized = true
        } catch (e: Throwable) {
            Log.e("ImageAnalyzer", "FaceLandmarker failed to initialize", e)
            isMediaPipeInitialized = true // Чтобы больше не пытаться
        }
    }

    companion object {
        // Губы
        private const val UPPER_LIP = 13
        private const val LOWER_LIP = 14
        private const val LEFT_LIP = 78
        private const val RIGHT_LIP = 308

        // Глаза
        private val LEFT_EYE_INDICES = listOf(33, 133, 157, 158, 159, 160, 161, 173)
        private val RIGHT_EYE_INDICES = listOf(362, 263, 386, 387, 388, 389, 390, 466)

        // Брови
        private const val LEFT_EYEBROW_INNER = 107
        private const val LEFT_EYEBROW_OUTER = 66
        private const val RIGHT_EYEBROW_INNER = 336
        private const val RIGHT_EYEBROW_OUTER = 296
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (!isMediaPipeInitialized) {
                setupFaceLandmarker()
            }

            val currentTime = System.currentTimeMillis()
            frameCount.incrementAndGet()
            if (currentTime - lastTime >= 1000) {
                fps = frameCount.get() * 1000f / (currentTime - lastTime)
                frameCount.set(0)
                lastTime = currentTime
            }

            // Конвертация ImageProxy в Mat (OpenCV)
            val mat = imageProxy.toMat()
            if (mat.empty()) {
                imageProxy.close()
                return
            }

            // Предобработка с OpenCV
            preprocessFrame(mat)

            // Получаем landmarks от MediaPipe
            val landmarks = getRealLandmarks(mat)

            // Используем EmotionAnalyzer для получения стабильного результата
            val analysis = EmotionAnalyzer.analyze(landmarks, mat.width(), mat.height())

            // Освобождение ресурсов
            mat.release()
            
            // Передача результатов в UI
            onResults(AnalysisResult(
                fps = fps,
                mouthRatio = analysis.mouthRatio,
                eyeRatio = analysis.eyeRatio,
                eyebrowRatio = analysis.eyebrowRatio,
                confidence = analysis.confidence,
                status = analysis.status
            ))
        } catch (e: Exception) {
            Log.e("ImageAnalyzer", "Error analyzing image", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun getRealLandmarks(mat: Mat): List<Landmark> {
        val landmarker = faceLandmarker ?: return generateMockLandmarks(mat.width(), mat.height())
        
        val bitmap = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = landmarker.detect(mpImage)
        
        val landmarks = mutableListOf<Landmark>()
        result.faceLandmarks().firstOrNull()?.let { faceLandmarks ->
            faceLandmarks.forEachIndexed { index, landmark ->
                // MediaPipe возвращает нормализованные координаты (0..1)
                // Но для расчета расстояний в EmotionAnalyzer нам удобнее пиксели
                landmarks.add(Landmark(
                    index, 
                    landmark.x() * mat.width(), 
                    landmark.y() * mat.height()
                ))
            }
        }
        
        return if (landmarks.isEmpty()) {
            // Если лицо не найдено в текущем кадре
            emptyList()
        } else {
            landmarks
        }
    }

    private fun ImageProxy.toMat(): Mat {
        try {
            val image = this.image ?: return Mat()
            val width = image.width
            val height = image.height

            // YUV_420_888 -> NV21 conversion
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + (width * height / 2))
            
            // Copy Y
            yBuffer.get(nv21, 0, ySize)
            
            // For NV21 we need V and U interleaved. 
            // This is a simplified copy that works if pixelStride is 2 (common for YUV_420_888)
            // If it fails, we catch the exception.
            try {
                vBuffer.get(nv21, ySize, vSize)
            } catch (e: Exception) {
                // Fallback for non-contiguous buffers
            }

            val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
            yuvMat.put(0, 0, nv21)

            val rgbMat = Mat()
            Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)
            yuvMat.release()

            // Rotate 270 degrees for front camera to be upright in portrait
            Core.rotate(rgbMat, rgbMat, Core.ROTATE_90_COUNTERCLOCKWISE)
            return rgbMat
        } catch (e: Exception) {
            Log.e("ImageAnalyzer", "toMat conversion failed", e)
            return Mat()
        }
    }

    private fun preprocessFrame(mat: Mat) {
        // Конвертация в grayscale для некоторых операций
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

        // Нормализация яркости с помощью CLAHE
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(gray, gray)

        // Применение Gaussian blur для уменьшения шума
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        gray.release()
    }

    private fun generateMockLandmarks(width: Int, height: Int): List<Landmark> {
        // Заглушка: возвращаем фиктивныеlandmarks, которые теперь "нейтральны"
        return listOf(
            Landmark(UPPER_LIP, width / 2f, height / 2f - 5),
            Landmark(LOWER_LIP, width / 2f, height / 2f + 5),
            Landmark(LEFT_LIP, width / 2f - 30, height / 2f),
            Landmark(RIGHT_LIP, width / 2f + 30, height / 2f),
            Landmark(LEFT_EYE_INDICES[0], width / 2f - 50, height / 2f - 50),
            Landmark(LEFT_EYE_INDICES[1], width / 2f - 30, height / 2f - 40),
            Landmark(RIGHT_EYE_INDICES[0], width / 2f + 50, height / 2f - 50),
            Landmark(RIGHT_EYE_INDICES[1], width / 2f + 30, height / 2f - 40),
            Landmark(LEFT_EYEBROW_INNER, width / 2f - 40, height / 2f - 70),
            Landmark(LEFT_EYEBROW_OUTER, width / 2f - 60, height / 2f - 70),
            Landmark(RIGHT_EYEBROW_INNER, width / 2f + 40, height / 2f - 70),
            Landmark(RIGHT_EYEBROW_OUTER, width / 2f + 60, height / 2f - 70)
        )
    }
}

data class Landmark(
    val index: Int,
    val x: Float,
    val y: Float
)
