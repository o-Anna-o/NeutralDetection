package com.example.neutraldetection

import android.app.Application
import org.opencv.android.OpenCVLoader

class NeutralDetectionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initOpenCV()
    }

    private fun initOpenCV() {
        if (!OpenCVLoader.initDebug()) {
            // Обработка ошибки загрузки OpenCV
            // В реальном приложении можно показать уведомление пользователю
        } else {
            // OpenCV успешно загружен
        }
    }
}