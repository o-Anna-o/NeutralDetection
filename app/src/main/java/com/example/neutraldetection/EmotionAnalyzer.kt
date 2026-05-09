package com.example.neutraldetection

import kotlin.math.absoluteValue
import kotlin.math.sqrt

/**
 * Анализатор эмоционального состояния на основе геометрических параметров лица.
 * Реализует алгоритмы, описанные в ТЗ:
 * - Mouth Opening Ratio (MOR)
 * - Eye Aspect Ratio (EAR)
 * - Eyebrow Position (EP)
 */
object EmotionAnalyzer {

    // Пороговые значения для нейтрального состояния (настраиваются эмпирически)
    data class Thresholds(
        val mouthRatioMin: Float = 0.05f,
        val mouthRatioMax: Float = 0.25f,
        val eyeRatioMin: Float = 0.15f,
        val eyeRatioMax: Float = 0.35f,
        val eyebrowRatioMin: Float = 0.1f,
        val eyebrowRatioMax: Float = 0.4f,
        val stabilityFrames: Int = 5  // количество кадров для стабильного определения
    )

    private var thresholds = Thresholds()
    private val history = mutableListOf<EmotionSnapshot>()

    /**
     * Обновляет пороговые значения.
     */
    fun updateThresholds(newThresholds: Thresholds) {
        thresholds = newThresholds
    }

    /**
     * Анализирует landmarks и возвращает результат определения эмоции.
     */
    fun analyze(
        landmarks: List<Landmark>,
        frameWidth: Int,
        frameHeight: Int
    ): EmotionAnalysis {
        if (landmarks.isEmpty()) {
            return EmotionAnalysis(
                status = EmotionStatus.NO_FACE,
                mouthRatio = 0f,
                eyeRatio = 0f,
                eyebrowRatio = 0f,
                confidence = 0f
            )
        }

        val mouthRatio = calculateMouthRatio(landmarks)
        val eyeRatio = calculateEyeRatio(landmarks)
        val eyebrowRatio = calculateEyebrowRatio(landmarks)

        val isNeutral = isNeutralState(mouthRatio, eyeRatio, eyebrowRatio)
        val confidence = calculateConfidence(mouthRatio, eyeRatio, eyebrowRatio)

        val snapshot = EmotionSnapshot(
            mouthRatio = mouthRatio,
            eyeRatio = eyeRatio,
            eyebrowRatio = eyebrowRatio,
            isNeutral = isNeutral,
            timestamp = System.currentTimeMillis()
        )
        addToHistory(snapshot)

        val stableStatus = getStableStatus()

        return EmotionAnalysis(
            status = stableStatus,
            mouthRatio = mouthRatio,
            eyeRatio = eyeRatio,
            eyebrowRatio = eyebrowRatio,
            confidence = confidence
        )
    }

    /**
     * Вычисляет Mouth Opening Ratio (MOR).
     * MOR = вертикальное расстояние между губами / горизонтальная ширина рта.
     */
    private fun calculateMouthRatio(landmarks: List<Landmark>): Float {
        val upperLip = landmarks.find { it.index == 13 } ?: return 0f
        val lowerLip = landmarks.find { it.index == 14 } ?: return 0f
        val leftLip = landmarks.find { it.index == 78 } ?: return 0f
        val rightLip = landmarks.find { it.index == 308 } ?: return 0f

        val vertical = distance(upperLip, lowerLip)
        val horizontal = distance(leftLip, rightLip)
        return if (horizontal > 0) vertical / horizontal else 0f
    }

    /**
     * Вычисляет Eye Aspect Ratio (EAR) для обоих глаз.
     * Упрощенная формула: высота глаза / ширина глаза.
     */
    private fun calculateEyeRatio(landmarks: List<Landmark>): Float {
        val leftEyePoints = landmarks.filter { it.index in listOf(33, 133, 157, 158, 159, 160, 161, 173) }
        val rightEyePoints = landmarks.filter { it.index in listOf(362, 263, 386, 387, 388, 389, 390, 466) }

        if (leftEyePoints.size < 2 || rightEyePoints.size < 2) return 0f

        val leftRatio = calculateSimpleEAR(leftEyePoints)
        val rightRatio = calculateSimpleEAR(rightEyePoints)

        return (leftRatio + rightRatio) / 2f
    }

    private fun calculateSimpleEAR(points: List<Landmark>): Float {
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }

        val height = maxY - minY
        val width = maxX - minX
        return if (width > 0) height / width else 0f
    }

    /**
     * Вычисляет Eyebrow Position (EP) как среднее расстояние между внутренними и внешними точками бровей.
     */
    private fun calculateEyebrowRatio(landmarks: List<Landmark>): Float {
        val leftInner = landmarks.find { it.index == 107 } ?: return 0f
        val leftOuter = landmarks.find { it.index == 66 } ?: return 0f
        val rightInner = landmarks.find { it.index == 336 } ?: return 0f
        val rightOuter = landmarks.find { it.index == 296 } ?: return 0f

        val leftDistance = distance(leftInner, leftOuter)
        val rightDistance = distance(rightInner, rightOuter)
        return (leftDistance + rightDistance) / 2f
    }

    /**
     * Определяет, находится ли состояние в "коридоре спокойствия".
     */
    private fun isNeutralState(mouthRatio: Float, eyeRatio: Float, eyebrowRatio: Float): Boolean {
        val mouthInRange = mouthRatio in thresholds.mouthRatioMin..thresholds.mouthRatioMax
        val eyeInRange = eyeRatio in thresholds.eyeRatioMin..thresholds.eyeRatioMax
        val eyebrowInRange = eyebrowRatio in thresholds.eyebrowRatioMin..thresholds.eyebrowRatioMax

        return mouthInRange && eyeInRange && eyebrowInRange
    }

    /**
     * Вычисляет уверенность в определении (0..1).
     */
    private fun calculateConfidence(mouthRatio: Float, eyeRatio: Float, eyebrowRatio: Float): Float {
        // Нормализуем каждое значение относительно идеального центра диапазона
        val mouthCenter = (thresholds.mouthRatioMin + thresholds.mouthRatioMax) / 2f
        val eyeCenter = (thresholds.eyeRatioMin + thresholds.eyeRatioMax) / 2f
        val eyebrowCenter = (thresholds.eyebrowRatioMin + thresholds.eyebrowRatioMax) / 2f

        val mouthDev = 1 - (mouthRatio - mouthCenter).absoluteValue / (thresholds.mouthRatioMax - thresholds.mouthRatioMin)
        val eyeDev = 1 - (eyeRatio - eyeCenter).absoluteValue / (thresholds.eyeRatioMax - thresholds.eyeRatioMin)
        val eyebrowDev = 1 - (eyebrowRatio - eyebrowCenter).absoluteValue / (thresholds.eyebrowRatioMax - thresholds.eyebrowRatioMin)

        return (mouthDev.coerceIn(0f, 1f) + eyeDev.coerceIn(0f, 1f) + eyebrowDev.coerceIn(0f, 1f)) / 3f
    }

    /**
     * Добавляет снимок в историю и удаляет старые записи.
     */
    private fun addToHistory(snapshot: EmotionSnapshot) {
        history.add(snapshot)
        // Оставляем только последние N кадров
        if (history.size > thresholds.stabilityFrames * 2) {
            history.removeFirst()
        }
    }

    /**
     * Определяет стабильное состояние на основе истории.
     */
    private fun getStableStatus(): EmotionStatus {
        if (history.size < thresholds.stabilityFrames) {
            return if (history.lastOrNull()?.isNeutral == true) EmotionStatus.NEUTRAL else EmotionStatus.NON_NEUTRAL
        }

        val recent = history.takeLast(thresholds.stabilityFrames)
        val neutralCount = recent.count { it.isNeutral }
        val nonNeutralCount = recent.size - neutralCount

        return when {
            neutralCount > nonNeutralCount -> EmotionStatus.NEUTRAL
            else -> EmotionStatus.NON_NEUTRAL
        }
    }

    /**
     * Очищает историю.
     */
    fun clearHistory() {
        history.clear()
    }

    private fun distance(a: Landmark, b: Landmark): Float {
        return sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))
    }

    private fun Float.pow(exp: Int): Float = Math.pow(this.toDouble(), exp.toDouble()).toFloat()
}

data class EmotionAnalysis(
    val status: EmotionStatus,
    val mouthRatio: Float,
    val eyeRatio: Float,
    val eyebrowRatio: Float,
    val confidence: Float
)

data class EmotionSnapshot(
    val mouthRatio: Float,
    val eyeRatio: Float,
    val eyebrowRatio: Float,
    val isNeutral: Boolean,
    val timestamp: Long
)