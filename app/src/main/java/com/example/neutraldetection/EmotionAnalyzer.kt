package com.example.neutraldetection

import kotlin.math.absoluteValue
import kotlin.math.sqrt

/**
 * Анализатор эмоционального состояния на основе геометрических параметров лица.
 * Реализует алгоритмы:
 * - Mouth Opening Ratio (MOR)
 * - Eye Aspect Ratio (EAR)
 * - Eyebrow Position (EP)
 */
object EmotionAnalyzer {

    // Пороговые значения для нейтрального состояния (настраиваются эмпирически)
    data class Thresholds(
        val mouthRatioMax: Float = 0.15f,     // Рот открыт, если > 0.15
        val eyeRatioMin: Float = 0.18f,      // Глаза закрыты, если < 0.18
        val eyeRatioMax: Float = 0.35f,      // Глаза выпучены, если > 0.35
        val eyebrowRatioMax: Float = 0.30f,   // Брови вскинуты, если > 0.30 (относительно ширины глаз)
        val stabilityFrames: Int = 3         // Меньше кадров для более быстрой реакции
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
     * Вычисляет Eyebrow Position (EP).
     * Нормализуем расстояние бровей относительно расстояния между глазами.
     */
    private fun calculateEyebrowRatio(landmarks: List<Landmark>): Float {
        val leftInner = landmarks.find { it.index == 107 } ?: return 0f
        val leftOuter = landmarks.find { it.index == 66 } ?: return 0f
        val rightInner = landmarks.find { it.index == 336 } ?: return 0f
        val rightOuter = landmarks.find { it.index == 296 } ?: return 0f
        
        // Расстояние между глазами для нормализации
        val leftEye = landmarks.find { it.index == 33 } ?: return 0f
        val rightEye = landmarks.find { it.index == 263 } ?: return 0f
        val eyeDistance = distance(leftEye, rightEye)
        if (eyeDistance == 0f) return 0f

        val leftDist = distance(leftInner, leftOuter)
        val rightDist = distance(rightInner, rightOuter)
        
        return ((leftDist + rightDist) / 2f) / eyeDistance
    }

    /**
     * Определяет, находится ли состояние в "коридоре спокойствия".
     */
    private fun isNeutralState(mouthRatio: Float, eyeRatio: Float, eyebrowRatio: Float): Boolean {
        val mouthInRange = mouthRatio <= thresholds.mouthRatioMax
        val eyeInRange = eyeRatio in thresholds.eyeRatioMin..thresholds.eyeRatioMax
        val eyebrowInRange = eyebrowRatio <= thresholds.eyebrowRatioMax

        return mouthInRange && eyeInRange && eyebrowInRange
    }

    /**
     * Вычисляет уверенность в определении (0..1).
     */
    private fun calculateConfidence(mouthRatio: Float, eyeRatio: Float, eyebrowRatio: Float): Float {
        // Упрощенная уверенность: чем ближе к порогам, тем ниже уверенность
        val mouthConf = (1f - (mouthRatio / thresholds.mouthRatioMax)).coerceIn(0f, 1f)
        val eyeConf = if (eyeRatio < thresholds.eyeRatioMin) {
            (eyeRatio / thresholds.eyeRatioMin)
        } else if (eyeRatio > thresholds.eyeRatioMax) {
            (1f - (eyeRatio - thresholds.eyeRatioMax))
        } else {
            1f
        }.coerceIn(0f, 1f)
        val eyebrowConf = (1f - (eyebrowRatio / thresholds.eyebrowRatioMax)).coerceIn(0f, 1f)

        return (mouthConf + eyeConf + eyebrowConf) / 3f
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
