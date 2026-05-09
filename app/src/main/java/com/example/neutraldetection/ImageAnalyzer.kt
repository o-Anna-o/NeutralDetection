package com.example.neutraldetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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

    // Индексы landmarks MediaPipe Face Mesh (упрощенные)
    companion object {
        // Губы
        private const val UPPER_LIP = 13
        private const val LOWER_LIP = 14
        private const val LEFT_LIP = 78
        private const val RIGHT_LIP = 308

        // Глаза (левый глаз)
        private val LEFT_EYE_INDICES = listOf(33, 133, 157, 158, 159, 160, 161, 173)
        private val RIGHT_EYE_INDICES = listOf(362, 263, 386, 387, 388, 389, 390, 466)

        // Брови
        private const val LEFT_EYEBROW_INNER = 107
        private const val LEFT_EYEBROW_OUTER = 66
        private const val RIGHT_EYEBROW_INNER = 336
        private const val RIGHT_EYEBROW_OUTER = 296

        // Пороги для нейтрального состояния (эмпирические)
        private const val MOUTH_RATIO_THRESHOLD_MIN = 0.05f
        private const val MOUTH_RATIO_THRESHOLD_MAX = 0.25f
        private const val EYE_RATIO_THRESHOLD_MIN = 0.15f
        private const val EYE_RATIO_THRESHOLD_MAX = 0.35f
        private const val EYEBROW_RATIO_THRESHOLD_MIN = 0.1f
        private const val EYEBROW_RATIO_THRESHOLD_MAX = 0.4f
    }

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        frameCount.incrementAndGet()
        if (currentTime - lastTime >= 1000) {
            fps = frameCount.get() * 1000f / (currentTime - lastTime)
            frameCount.set(0)
            lastTime = currentTime
        }

        // Конвертация ImageProxy в Mat (OpenCV)
        val mat = imageProxy.toMat()

        // Предобработка с OpenCV
        preprocessFrame(mat)

        // Здесь будет вызов MediaPipe для детекции лица
        // Временная заглушка: симулируем результаты
        val mockLandmarks = generateMockLandmarks(mat.width(), mat.height())

        // Вычисление параметров
        val mouthRatio = calculateMouthRatio(mockLandmarks)
        val eyeRatio = calculateEyeRatio(mockLandmarks)
        val eyebrowRatio = calculateEyebrowRatio(mockLandmarks)

        // Определение эмоционального состояния
        val status = determineEmotionStatus(mouthRatio, eyeRatio, eyebrowRatio)

        // Освобождение ресурсов
        mat.release()
        imageProxy.close()

        // Передача результатов в UI
        onResults(AnalysisResult(fps, mouthRatio, eyeRatio, eyebrowRatio, 1.0f, status))
    }

    private fun ImageProxy.toMat(): Mat {
        val image = this.image ?: return Mat()
        val width = image.width
        val height = image.height

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val mat = Mat(height + height / 2, width, CvType.CV_8UC1)
        mat.put(0, 0, nv21)

        val rgbMat = Mat()
        Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)
        mat.release()

        // Поворот в зависимости от ориентации камеры
        Core.rotate(rgbMat, rgbMat, Core.ROTATE_90_CLOCKWISE)
        return rgbMat
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
        // Заглушка: возвращаем фиктивные landmarks
        return listOf(
            Landmark(UPPER_LIP, width / 2f, height / 2f - 20),
            Landmark(LOWER_LIP, width / 2f, height / 2f + 20),
            Landmark(LEFT_LIP, width / 2f - 30, height / 2f),
            Landmark(RIGHT_LIP, width / 2f + 30, height / 2f),
            Landmark(LEFT_EYE_INDICES[0], width / 2f - 50, height / 2f - 50),
            Landmark(LEFT_EYE_INDICES[1], width / 2f - 30, height / 2f - 50),
            Landmark(RIGHT_EYE_INDICES[0], width / 2f + 50, height / 2f - 50),
            Landmark(RIGHT_EYE_INDICES[1], width / 2f + 30, height / 2f - 50),
            Landmark(LEFT_EYEBROW_INNER, width / 2f - 40, height / 2f - 70),
            Landmark(LEFT_EYEBROW_OUTER, width / 2f - 60, height / 2f - 70),
            Landmark(RIGHT_EYEBROW_INNER, width / 2f + 40, height / 2f - 70),
            Landmark(RIGHT_EYEBROW_OUTER, width / 2f + 60, height / 2f - 70)
        )
    }

    private fun calculateMouthRatio(landmarks: List<Landmark>): Float {
        val upperLip = landmarks.find { it.index == UPPER_LIP } ?: return 0f
        val lowerLip = landmarks.find { it.index == LOWER_LIP } ?: return 0f
        val leftLip = landmarks.find { it.index == LEFT_LIP } ?: return 0f
        val rightLip = landmarks.find { it.index == RIGHT_LIP } ?: return 0f

        val vertical = sqrt(
            (upperLip.x - lowerLip.x).pow(2) + (upperLip.y - lowerLip.y).pow(2)
        )
        val horizontal = sqrt(
            (leftLip.x - rightLip.x).pow(2) + (leftLip.y - rightLip.y).pow(2)
        )
        return if (horizontal > 0) vertical / horizontal else 0f
    }

    private fun calculateEyeRatio(landmarks: List<Landmark>): Float {
        // Упрощенный EAR (Eye Aspect Ratio)
        val leftEyePoints = landmarks.filter { it.index in LEFT_EYE_INDICES }
        val rightEyePoints = landmarks.filter { it.index in RIGHT_EYE_INDICES }
        if (leftEyePoints.size < 2 || rightEyePoints.size < 2) return 0f

        val leftHeight = leftEyePoints.maxOf { it.y } - leftEyePoints.minOf { it.y }
        val leftWidth = leftEyePoints.maxOf { it.x } - leftEyePoints.minOf { it.x }
        val rightHeight = rightEyePoints.maxOf { it.y } - rightEyePoints.minOf { it.y }
        val rightWidth = rightEyePoints.maxOf { it.x } - rightEyePoints.minOf { it.x }

        val leftRatio = if (leftWidth > 0) leftHeight / leftWidth else 0f
        val rightRatio = if (rightWidth > 0) rightHeight / rightWidth else 0f
        return (leftRatio + rightRatio) / 2f
    }

    private fun calculateEyebrowRatio(landmarks: List<Landmark>): Float {
        val leftInner = landmarks.find { it.index == LEFT_EYEBROW_INNER } ?: return 0f
        val leftOuter = landmarks.find { it.index == LEFT_EYEBROW_OUTER } ?: return 0f
        val rightInner = landmarks.find { it.index == RIGHT_EYEBROW_INNER } ?: return 0f
        val rightOuter = landmarks.find { it.index == RIGHT_EYEBROW_OUTER } ?: return 0f

        val leftDistance = sqrt(
            (leftInner.x - leftOuter.x).pow(2) + (leftInner.y - leftOuter.y).pow(2)
        )
        val rightDistance = sqrt(
            (rightInner.x - rightOuter.x).pow(2) + (rightInner.y - rightOuter.y).pow(2)
        )
        return (leftDistance + rightDistance) / 2f
    }

    private fun determineEmotionStatus(
        mouthRatio: Float,
        eyeRatio: Float,
        eyebrowRatio: Float
    ): EmotionStatus {
        // Если landmarks не обнаружены
        if (mouthRatio == 0f && eyeRatio == 0f && eyebrowRatio == 0f) {
            return EmotionStatus.NO_FACE
        }

        val isMouthNeutral = mouthRatio in MOUTH_RATIO_THRESHOLD_MIN..MOUTH_RATIO_THRESHOLD_MAX
        val isEyeNeutral = eyeRatio in EYE_RATIO_THRESHOLD_MIN..EYE_RATIO_THRESHOLD_MAX
        val isEyebrowNeutral = eyebrowRatio in EYEBROW_RATIO_THRESHOLD_MIN..EYEBROW_RATIO_THRESHOLD_MAX

        return if (isMouthNeutral && isEyeNeutral && isEyebrowNeutral) {
            EmotionStatus.NEUTRAL
        } else {
            EmotionStatus.NON_NEUTRAL
        }
    }
}

data class Landmark(
    val index: Int,
    val x: Float,
    val y: Float
)

private fun Float.pow(exp: Int): Float = Math.pow(this.toDouble(), exp.toDouble()).toFloat()