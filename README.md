# Neutral Emotion Detector - Android приложение

Приложение для обнаружения и отслеживания нейтрального (спокойного) эмоционального состояния человека по кадрам видеопотока с камеры в реальном времени.

## Технологии

- **Язык**: Kotlin
- **Минимальная версия Android**: API 24 (Android 7.0)
- **Архитектура**: MVVM (Model-View-ViewModel)
- **Библиотеки**:
  - OpenCV 4.10.0 – компьютерное зрение, предобработка изображений
  - MediaPipe Face Mesh – детекция 468 лицевых landmarks
  - CameraX – работа с камерой
  - TensorFlow Lite (опционально) – нейросетевой классификатор

## Математика и алгоритмы

### 1. Геометрический анализ (OpenCV + MediaPipe)

Извлекаем 468 точек лица с помощью MediaPipe Face Mesh. Для определения «спокойствия» вычисляются следующие коэффициенты:

#### Mouth Opening Ratio (MOR)
Отношение вертикального расстояния между губами к ширине рта.

```
MOR = (lip_upper_y - lip_lower_y) / (lip_right_x - lip_left_x)
```



### 2. Нейросетевой классификатор (TFLite)

Поверх извлеченных координат (Landmarks) запускается легковесная нейросеть (MLP или MobileNetV2-backbone), которая принимает на вход вектор нормализованных координат и выдает вероятность класса «Neutral».

## Этапы разработки и отладки


## Структура проекта

```
app/
├── src/main/java/com/example/neutraldetection/
│   ├── MainActivity.kt              # Основная активность, UI
│   ├── ImageAnalyzer.kt             # Анализатор кадров (CameraX + OpenCV)
│   ├── MediaPipeFaceDetector.kt     # Детектор лица MediaPipe
│   ├── EmotionAnalyzer.kt           # Логика определения эмоций
│   ├── NeutralDetectionApp.kt       # Application класс для инициализации OpenCV
│   └── (дополнительные утилиты)
├── src/main/res/
│   ├── layout/activity_main.xml     # Интерфейс приложения
│   └── values/                      # Ресурсы строк, цветов, стилей
├── build.gradle.kts                 # Конфигурация модуля
└── proguard-rules.pro
```


