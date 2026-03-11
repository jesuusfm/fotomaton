# 📸 PhotoBooth App — Documentación Técnica Exhaustiva

> Aplicación Android de fotomatón profesional con soporte dual de cámara (teléfono + USB/DJI Osmo Pocket 3), efectos en tiempo real, filtros faciales con ML Kit, y sistema completo de captura, procesamiento y compartición de fotos y videos.

---

## 📋 Índice

1. [Visión General](#1-visión-general)
2. [Arquitectura del Proyecto](#2-arquitectura-del-proyecto)
3. [Configuración de Build](#3-configuración-de-build)
4. [Flujo de Navegación](#4-flujo-de-navegación)
5. [Sistema de Eventos](#5-sistema-de-eventos)
6. [Captura con Cámara del Teléfono (CameraX)](#6-captura-con-cámara-del-teléfono-camerax)
7. [⭐ Cámara USB/UVC — Implementación Completa](#7--cámara-usbuvc--implementación-completa)
8. [Pipeline de Efectos de Foto](#8-pipeline-de-efectos-de-foto)
9. [Pipeline de Efectos de Video](#9-pipeline-de-efectos-de-video)
10. [Modo Photo Booth (4 fotos)](#10-modo-photo-booth-4-fotos)
11. [Sistema de Preview y Compartición](#11-sistema-de-preview-y-compartición)
12. [Procesamiento Pesado (ProcessingActivity)](#12-procesamiento-pesado-processingactivity)
13. [Sistema de Marcos y Fondos](#13-sistema-de-marcos-y-fondos)
14. [Filtros Faciales con ML Kit](#14-filtros-faciales-con-ml-kit)
15. [Sistema de Audio](#15-sistema-de-audio)
16. [Compartición Diferida (PendingShares)](#16-compartición-diferida-pendingshares)
17. [Galería de Eventos](#17-galería-de-eventos)
18. [Manifest y Permisos](#18-manifest-y-permisos)
19. [Layouts XML](#19-layouts-xml)
20. [Limitaciones Conocidas](#20-limitaciones-conocidas)
21. [Instalación y Ejecución](#21-instalación-y-ejecución)

---

## 1. Visión General

| Propiedad | Valor |
|-----------|-------|
| **Paquete** | `com.photobooth.app` |
| **Lenguaje** | Kotlin |
| **Min SDK** | 26 (Android 8.0 Oreo) |
| **Target SDK** | 34 (Android 14) |
| **Compile SDK** | 34 |
| **Build System** | Gradle 8.13 + Kotlin DSL |
| **Arquitectura** | Activity-based con View Binding |
| **Actividades** | 13 |
| **Archivos Kotlin** | 23 |

### Características principales

- **Doble cámara**: Cámara del teléfono (CameraX) + cámara USB externa (UVC)
- **DJI Osmo Pocket 3**: Soporte nativo con workarounds para limitaciones UVC
- **7 filtros de color**: Normal, B/N, Sepia, Vintage, Contraste, Invertido, Frío
- **Marcos decorativos**: Organizados por temas, cargados desde assets
- **Fondos virtuales**: Eliminación de fondo con ML Kit + reemplazo
- **Filtros faciales**: Bigote, gorro, gafas, máscara, orejas, nariz (ML Kit Face Detection)
- **Modo Photo Booth**: Secuencia automática de 4 fotos con composición en tira vertical
- **Video**: Grabación con duración configurable (5-30s), cámara lenta, boomerang
- **Compartición**: Email, WhatsApp, WhatsApp Business, envío directo por número, cola diferida

---

## 2. Arquitectura del Proyecto

```
fotomaton2/
├── app/
│   ├── build.gradle.kts                          # Configuración de build y dependencias
│   ├── proguard-rules.pro                        # Reglas de ProGuard
│   ├── libs/
│   │   ├── AndroidUSBCamera-2.3.8.aar            # Librería UVC camera
│   │   └── libusbcommon_v4.1.1.aar              # Dependencia transitiva USB
│   └── src/main/
│       ├── AndroidManifest.xml                   # Manifest con permisos y actividades
│       ├── java/com/photobooth/app/
│       │   ├── EventNameActivity.kt              # Launcher - gestión de eventos
│       │   ├── MenuActivity.kt                   # Selección foto/video
│       │   ├── PhotoConfigActivity.kt            # Configuración de foto
│       │   ├── VideoConfigActivity.kt            # Configuración de video
│       │   ├── CameraActivity.kt                 # ⭐ Motor principal (~2900 líneas)
│       │   ├── PreviewActivity.kt                # Vista previa + compartición
│       │   ├── ProcessingActivity.kt             # Procesamiento pesado de video
│       │   ├── GalleryActivity.kt                # Galería de sesión (bitmaps)
│       │   ├── EventGalleryActivity.kt           # Galería de evento (MediaStore)
│       │   ├── FramePreviewActivity.kt           # Selector de marcos
│       │   ├── BackgroundPreviewActivity.kt      # Selector de fondos
│       │   ├── FaceFilterActivity.kt             # Selector de filtros faciales
│       │   ├── PendingSharesActivity.kt          # Cola de compartición diferida
│       │   ├── FaceFilterHelper.kt               # Lógica de posicionamiento facial
│       │   ├── PendingSharesManager.kt           # Persistencia JSON de shares
│       │   ├── PendingShare.kt                   # Data class para shares
│       │   ├── ToneGenerator.kt                  # Generación de tonos/beeps
│       │   ├── YuvToRgbConverter.kt              # Conversión YUV→RGB
│       │   ├── GalleryAdapter.kt                 # Adaptador para galería de bitmaps
│       │   ├── ThemeAdapter.kt                   # Adaptador para temas
│       │   ├── FrameAdapter.kt                   # Adaptador para marcos
│       │   └── BackgroundAdapter.kt              # Adaptador para fondos
│       ├── res/
│       │   ├── layout/                           # 24 archivos XML de layout
│       │   ├── values/                           # Colores, strings, themes
│       │   └── xml/                              # Configuraciones XML
│       └── assets/
│           ├── marcos/                           # Marcos organizados por tema
│           │   ├── verano/
│           │   ├── navidad/
│           │   ├── cumpleaños/
│           │   ├── invierno/
│           │   ├── comunion/
│           │   ├── boda/
│           │   └── otros/
│           ├── fondos/                           # Fondos organizados por tema
│           ├── face_filters/                     # Filtros faciales (PNG)
│           └── music/                            # Música para añadir a videos
├── build.gradle.kts                              # Configuración root de Gradle
├── settings.gradle.kts                           # Settings del proyecto
├── gradle.properties                             # Propiedades de Gradle
├── gradlew / gradlew.bat                         # Wrapper de Gradle
└── aar_decompiled/                               # Fuentes descompiladas (dev only)
```

---

## 3. Configuración de Build

### `app/build.gradle.kts` — Dependencias completas

```kotlin
// SDK
compileSdk = 34
minSdk = 26
targetSdk = 34
jvmTarget = "1.8"

// Dependencias principales
dependencies {
    // === Core Android ===
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // === CameraX (cámara del teléfono) ===
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-video:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // === USB Camera (cámara externa UVC) ===
    implementation(files("libs/AndroidUSBCamera-2.3.8.aar"))
    implementation(files("libs/libusbcommon_v4.1.1.aar"))

    // === ML Kit (IA) ===
    implementation("com.google.mlkit:face-detection:16.1.5")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta5")

    // === Video Processing ===
    implementation("com.arthenica:ffmpeg-kit-full:6.1.1")  // FFmpeg

    // === UI ===
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")  // Thumbnails

    // === Testing ===
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

### View Binding

```kotlin
buildFeatures {
    viewBinding = true
    dataBinding = true
}
```

---

## 4. Flujo de Navegación

```
┌──────────────────────┐
│  EventNameActivity   │  ← LAUNCHER (punto de entrada)
│  Gestión de eventos  │
└──────────┬───────────┘
           │ (nombre de evento)
           ▼
┌──────────────────────┐
│    MenuActivity      │
│  📷 Foto  │  🎥 Video│
└────┬──────┴────┬─────┘
     │           │
     ▼           ▼
┌────────────┐ ┌─────────────┐
│PhotoConfig │ │ VideoConfig  │
│Activity    │ │ Activity     │
│            │ │              │
│• Cámara    │ │• Cámara      │
│• Filtros   │ │• Duración    │
│• Marco     │ │• Calidad     │
│• Fondo     │ │• Slow motion │
│• Cara      │ │• Filtro/Marco│
│• PhotoBooth│ │• Boomerang   │
└────┬───────┘ └──────┬──────┘
     │                │
     ▼                ▼
┌──────────────────────────────────────┐
│         CameraActivity              │
│  ⭐ MOTOR PRINCIPAL (~2900 líneas)  │
│                                      │
│  Dos caminos:                        │
│  • Cámara teléfono (CameraX)        │
│  • Cámara USB (UVC/DJI)             │
│                                      │
│  → takePhoto() / takeUvcPhoto()     │
│  → startVideoRecording()            │
│  → startUvcVideoRecording()         │
│  → takePhotoBoothPhoto()            │
└──────────┬───────────────────────────┘
           │
     ┌─────┴──────┐
     │             │
     ▼             ▼
┌──────────┐  ┌───────────────┐
│Processing│  │ PreviewActivity│
│Activity  │  │               │
│          │  │• Reproducir   │
│• ML Kit  │  │• Mutear       │
│• FFmpeg  │  │• Añadir música│
│• Fondos  │  │• Compartir    │
│• Frames  │  │• Email/WA     │
└────┬─────┘  └───────────────┘
     │
     ▼
┌──────────────────┐
│ PreviewActivity  │
│ (resultado final)│
└──────────────────┘
```

### Actividades auxiliares (accesibles desde Config):

```
PhotoConfig/VideoConfig ──→ FramePreviewActivity      (selector de marcos)
                       ──→ BackgroundPreviewActivity   (selector de fondos)
                       ──→ FaceFilterActivity           (selector de filtros faciales)

MenuActivity ──→ EventGalleryActivity                  (galería del evento)
             ──→ PendingSharesActivity                 (cola de envíos pendientes)
```

---

## 5. Sistema de Eventos

### EventNameActivity (Launcher)

- **Propósito**: Punto de entrada. Gestionar eventos (bodas, cumpleaños, etc.)
- **Persistencia**: `SharedPreferences` para último evento, `MediaStore` para escaneo
- **Funcionalidades**:
  - Listar eventos existentes con conteo de fotos/videos
  - Crear nuevos eventos
  - Eliminar eventos (con manejo de permisos Android 11+)
  - Escanear `MediaStore` buscando carpetas en `Pictures/PhotoBooth/` y `Movies/PhotoBooth/`

### MediaStore — Estructura de almacenamiento

```
Almacenamiento externo/
├── Pictures/
│   └── PhotoBooth/
│       ├── CumpleañosGloria/
│       │   ├── IMG_20260311_235400.jpg
│       │   └── IMG_20260311_235430.jpg
│       └── BodaMaría/
│           └── IMG_20260312_120000.jpg
└── Movies/
    └── PhotoBooth/
        ├── CumpleañosGloria/
        │   └── VID_20260311_235500.mp4
        └── BodaMaría/
            └── VID_20260312_120100.mp4
```

---

## 6. Captura con Cámara del Teléfono (CameraX)

### Inicialización — `startCamera()`

```kotlin
val cameraProvider = ProcessCameraProvider.getInstance(this).get()
val preview = Preview.Builder().build()
    .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

// Modo PHOTO
imageCapture = ImageCapture.Builder()
    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
    .build()

// Modo VIDEO
val qualitySelector = QualitySelector.fromOrderedList(
    listOf(Quality.FHD, Quality.HD, Quality.SD),
    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
)
videoCapture = VideoCapture.withOutput(
    Recorder.Builder().setQualitySelector(qualitySelector).build()
)
```

### Captura de foto — `takePhoto()`

1. Captura `ImageProxy` via `imageCapture.takePicture()`
2. Convierte `ImageProxy` → `Bitmap` (JPEG decode o YUV→RGB)
3. Aplica pipeline de efectos (filtro → fondo → marco → cara)
4. Guarda en `MediaStore` con `ContentValues`
5. Lanza `PreviewActivity`

### Grabación de video — `startVideoRecording()`

1. Crea `MediaStoreOutputOptions` con ruta en `Movies/PhotoBooth/$eventName`
2. Inicia grabación con `videoCapture.output.prepareRecording()`
3. Timer con `CountDownTimer` para duración configurada
4. Al finalizar, aplica efectos FFmpeg si es necesario

---

## 7. ⭐ Cámara USB/UVC — Implementación Completa

> Esta es la sección más compleja de la aplicación. Documenta el soporte para cámaras USB externas (específicamente DJI Osmo Pocket 3) usando el protocolo UVC (USB Video Class).

### 7.1 Librerías utilizadas

| Librería | Versión | Formato | Propósito |
|----------|---------|---------|-----------|
| AndroidUSBCamera | 2.3.8 | AAR local | Control UVC, preview, grabación H.264 |
| libusbcommon | 4.1.1 | AAR local | Comunicación USB de bajo nivel |

Ambos archivos AAR están en `app/libs/` e incluyen librerías nativas (`.so`) para:
- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

### 7.2 Variables de estado UVC

```kotlin
private var uvcCameraHelper: UVCCameraHelper? = null     // Singleton helper
private var uvcTextureView: UVCCameraTextureView? = null  // View de preview
private var isUvcPreview = false                           // ¿Preview activo?
private var isUvcCameraOpened = false                      // ¿Dispositivo conectado?
```

### 7.3 Inicialización — `startUvcCamera()`

#### Paso 1: Crear TextureView programáticamente

```kotlin
val textureView = com.serenegiant.usb.widget.UVCCameraTextureView(this)
textureView.layoutParams = android.widget.FrameLayout.LayoutParams(
    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
)
binding.uvcPreviewContainer.removeAllViews()
binding.uvcPreviewContainer.addView(textureView)
```

> **¿Por qué programáticamente y no en XML?** La librería AndroidUSBCamera carga librerías nativas durante la inflación del layout. Si se pone en XML y las librerías no están disponibles, la app crashea en `setContentView()`. Al crearlo programáticamente, podemos capturar errores.

#### Paso 2: Crear UVCCameraHelper (Singleton)

```kotlin
uvcCameraHelper = UVCCameraHelper.getInstance(1920, 1080)
uvcCameraHelper?.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG)
```

- **Singleton**: `getInstance()` solo crea la instancia la primera vez. Llamadas posteriores con diferentes dimensiones son ignoradas.
- **1920×1080**: Resolución por defecto. La DJI Osmo Pocket 3 envía 1080p por UVC.
- **MJPEG**: Formato de frame. Más compatible que YUYV, menor ancho de banda USB.

#### Paso 3: Configurar callbacks de Surface

```kotlin
cameraView.setCallback(object : CameraViewInterface.Callback {
    override fun onSurfaceCreated(view, surface) {
        // Solo inicia preview si la cámara está abierta Y no hay preview activo
        if (!isUvcPreview && uvcCameraHelper?.isCameraOpened == true) {
            uvcCameraHelper?.startPreview(cameraView)
            isUvcPreview = true
        }
    }

    override fun onSurfaceChanged(view, surface, width, height) {
        // ⭐ CRÍTICO: Reiniciar preview cuando la surface cambia
        // Esto ocurre después de setAspectRatio() que causa relayout
        if (uvcCameraHelper?.isCameraOpened == true) {
            uvcCameraHelper?.stopPreview()
            isUvcPreview = false
            uvcCameraHelper?.startPreview(cameraView)
            isUvcPreview = true
        }
        applyCenterCropTransform()
    }

    override fun onSurfaceDestroy(view, surface) {
        if (isUvcPreview && uvcCameraHelper?.isCameraOpened == true) {
            uvcCameraHelper?.stopPreview()
            isUvcPreview = false
        }
    }
})
```

> **Race condition resuelto**: La librería internamente llama `startPreview()` con 500ms de delay tras `onConnect`. Si `onSurfaceCreated` llega antes, nuestro callback inicia el preview. Si llega después, `onSurfaceChanged` lo reinicia con la surface correcta.

### 7.4 Conexión de dispositivo USB

#### `onAttachDev` — Dispositivo USB detectado

```kotlin
override fun onAttachDev(device: UsbDevice) {
    binding.usbStatusText.text = "🔌 Dispositivo USB detectado, solicitando permiso..."
    uvcCameraHelper?.requestPermission(0)  // Solicita permiso USB para device[0]
}
```

#### `onConnectDev` — Dispositivo conectado exitosamente

Esta es la callback más compleja:

```kotlin
override fun onConnectDev(device: UsbDevice, isConnected: Boolean) {
    if (isConnected) {
        isUvcCameraOpened = true

        // 1. Log de resoluciones soportadas
        val sizes = uvcCameraHelper?.getSupportedPreviewSizes()
        // Ejemplo de output: "1920x1080, 1280x720, 640x480, 320x240"

        // 2. ⭐ Auto-detección de resolución portrait
        val portraitSize = sizes?.filter { it.height > it.width }
            ?.maxByOrNull { it.width * it.height }
        if (portraitSize != null) {
            uvcCameraHelper?.updateResolution(portraitSize.width, portraitSize.height)
        }

        // 3. Toast con resolución actual
        Toast.makeText(this, "📷 USB conectada (${actualW}x${actualH})", ...)

        // 4. Desactivar constraint de aspect ratio
        uvcTextureView?.setAspectRatio(0.0)

        // 5. Safety net con 1500ms de delay
        Handler(Looper.getMainLooper()).postDelayed({
            uvcTextureView?.setAspectRatio(0.0)
            uvcCameraHelper?.stopPreview()
            uvcCameraHelper?.startPreview(cameraView)
            applyCenterCropTransform()
        }, 1500)
    }
}
```

##### Auto-detección de resolución portrait

La DJI Osmo Pocket 3 puede anunciar resoluciones portrait (1080×1920) cuando está configurada en modo vertical. El código busca automáticamente la resolución con `height > width` más grande y cambia a ella usando `updateResolution()`.

##### El truco de `setAspectRatio(0.0)`

La librería AndroidUSBCamera usa `AspectRatioTextureView` que internamente aplica un aspect ratio en `onMeasure()`:

```java
// Dentro de AspectRatioTextureView.java (descompilado)
public void setAspectRatio(double aspectRatio) {
    if (aspectRatio < 0.0) throw new IllegalArgumentException();  // NO acepta negativo
    mRequestedAspect = aspectRatio;
    requestLayout();
}

@Override
protected void onMeasure(int widthSpec, int heightSpec) {
    if (mRequestedAspect > 0.0) {
        // Aplica constraint → causa preview cuadrado o reducido
    }
    // Si mRequestedAspect == 0.0, NO aplica constraint → MATCH_PARENT
}
```

La librería internamente llama `setAspectRatio(previewWidth / previewHeight)`, que con integer division `1920/1080 = 1` (cuadrado!). Nosotros sobreescribimos con `0.0` para que el TextureView llene todo el contenedor.

##### Safety net de 1500ms

Reinicio de preview demorado para asegurar que:
1. La librería ha terminado su propia inicialización (500ms internos)
2. El layout se ha estabilizado después de `setAspectRatio(0.0)`
3. La surface texture está lista
4. El `applyCenterCropTransform()` se aplica con dimensiones correctas

#### `onDettachDev` — Dispositivo desconectado

```kotlin
override fun onDettachDev(device: UsbDevice) {
    if (isUvcPreview) {
        uvcCameraHelper?.closeCamera()
        isUvcPreview = false
    }
    isUvcCameraOpened = false
    // Muestra UI de reconexión con botón de retry
}
```

### 7.5 ⭐ `registerUsbMonitorSafe()` — Workaround Android 12+

**Problema**: La librería AndroidUSBCamera (AAR v2.3.8) crea un `PendingIntent` con `flags=0` internamente. En Android 12+ (API 31), esto lanza `IllegalArgumentException` porque se requiere `FLAG_IMMUTABLE` o `FLAG_MUTABLE`.

**Solución**: Replicar toda la lógica de `USBMonitor.register()` usando reflexión (Java Reflection API) para inyectar el flag correcto.

```
Flujo de registerUsbMonitorSafe():

1. Si Android < 12 → usar registerUSB() normal → return
2. Obtener USBMonitor via getUSBMonitor()
3. Verificar campo 'destroyed' no sea true
4. Verificar 'mPermissionIntent' no exista ya
5. Extraer Context de WeakReference 'mWeakContext'
6. Obtener ACTION_USB_PERMISSION string
7. ⭐ CREAR PendingIntent con FLAG_IMMUTABLE       ← EL FIX
8. Inyectar PendingIntent en campo 'mPermissionIntent'
9. Registrar BroadcastReceiver para:
   - ACTION_USB_PERMISSION (nuestra acción custom)
   - USB_DEVICE_ATTACHED
   - USB_DEVICE_DETACHED
10. Iniciar chequeo periódico de dispositivos
```

**Campos accedidos por reflexión:**

| Campo | Tipo | Propósito |
|-------|------|-----------|
| `destroyed` | `boolean` | Estado de destrucción del monitor |
| `mPermissionIntent` | `PendingIntent` | Intent para solicitar permiso USB |
| `mWeakContext` | `WeakReference<Context>` | Contexto de la actividad |
| `ACTION_USB_PERMISSION` | `String` | Acción custom del BroadcastReceiver |
| `mDeviceCounts` | `int` | Contador de dispositivos detectados |
| `mOnDeviceConnectListener` | `OnDeviceConnectListener` | Listener del monitor |
| `mHandler` | `Handler` | Handler para posts diferidos |

**Nota para Android 13+ (TIRAMISU):** El `registerReceiver()` requiere el flag `RECEIVER_EXPORTED`:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
} else {
    context.registerReceiver(receiver, filter)
}
```

### 7.6 Transform CenterCrop — `applyCenterCropTransform()`

**Problema**: El buffer UVC es 1920×1080 (landscape 16:9). En un teléfono en portrait, el TextureView es ~1080×2340 (9:19.5). Sin transformación, la imagen se estira no uniformemente.

**Solución**: Aplicar una `Matrix` al TextureView que escala uniformemente (como ImageView scaleType=centerCrop).

```kotlin
private fun applyCenterCropTransform() {
    val viewWidth = tv.width.toFloat()      // ~1080
    val viewHeight = tv.height.toFloat()     // ~2340
    val bufferWidth = previewWidth.toFloat()  // 1920
    val bufferHeight = previewHeight.toFloat() // 1080

    // Ratios de escala
    val scaleRatioX = viewWidth / bufferWidth    // 1080/1920 = 0.5625
    val scaleRatioY = viewHeight / bufferHeight  // 2340/1080 = 2.1667

    // Escala uniforme: la mayor para llenar todo
    val maxScale = maxOf(scaleRatioX, scaleRatioY) // 2.1667

    // Compensación del estiramiento no uniforme
    val scaleX = maxScale / scaleRatioX  // 2.1667 / 0.5625 = 3.852
    val scaleY = maxScale / scaleRatioY  // 2.1667 / 2.1667 = 1.0

    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    tv.setTransform(matrix)
}
```

**Resultado visual:**
```
Buffer original (1920×1080):
┌─────────────────────────────────┐
│  La imagen completa 16:9       │
└─────────────────────────────────┘

Pantalla del teléfono (portrait):
┌──────────┐
│ ┌──────┐ │
│ │Centro│ │  ← Solo la franja central 9:16
│ │de la │ │     se muestra, el resto se recorta
│ │imagen│ │
│ └──────┘ │
└──────────┘
```

> **Importante**: Este transform es SOLO de visualización. No afecta a las fotos/videos capturados, que usan los frames raw de la cámara.

### 7.7 Captura de foto UVC — `takeUvcPhoto()`

```
Flujo completo:

1. Verificar uvcCameraHelper?.isCameraOpened == true
2. Crear directorio: getExternalFilesDir(null)/UVC_Photos/
3. Crear archivo temporal: UVC_[timestamp].jpg
4. Llamar uvcCameraHelper?.capturePicture(path, listener)
5. En onCaptureResult:
   a. Decodificar bitmap desde archivo
   b. Verificar bitmap != null (error handling)
   c. Aplicar pipeline de efectos:
      - Filtro de color (si filterMode != "normal")
      - Eliminación de fondo (si removeBackground)
      - Marco overlay (si frameMode != "none")
      - Filtro facial (si faceFilterMode != "none")
   d. Guardar en MediaStore (savePhotoToGallery)
   e. Mostrar PreviewActivity
   f. Eliminar archivo temporal
```

### 7.8 Grabación de video UVC — `startUvcVideoRecording()`

```
Flujo completo:

1. Verificar cámara abierta
2. Crear directorio: getExternalFilesDir(null)/UVC_Videos/
3. Crear archivo: UVC_[timestamp] (SIN extensión .mp4)
   └── La librería añade .mp4 internamente
4. Configurar RecordParams:
   - recordPath = path sin extensión
   - recordDuration = 0 (manual stop)
   - isVoiceClose = false
5. Llamar startPusher(params, listener)
   └── Internamente crea H264EncodeConsumer con previewWidth×previewHeight
6. Mostrar indicador de grabación + timer
7. CountDownTimer con duración configurada
8. Al finalizar → stopUvcVideoRecording()
9. onRecordResult callback:
   a. Verificar archivo existe y tiene tamaño > 0
   b. ⭐ cropUvcVideoToPortrait(videoPath) → FFmpeg crop
   c. saveUvcVideoToGallery(croppedPath)
   d. Routing de post-procesamiento:
      - Background removal → ProcessingActivity
      - Boomerang reverse → processBoomerangVideo()
      - Slow motion → showPreview()
      - Filtro/Marco → processVideoWithFilterAndFrame()
      - Normal → showPreview()
```

#### Encoder interno de la librería (H264EncodeConsumer)

```
UVCCameraHelper.startPusher()
  → AbstractUVCCameraHandler.handleStartPusher()
    → CameraThread.startVideoRecord()
      → new H264EncodeConsumer(getWidth(), getHeight())
        → startMediaCodec()
          → MediaFormat.createVideoFormat("video/avc", mWidth, mHeight)
          → MediaCodec.createEncoderByType("video/avc")
          → MediaCodec.configure(format, null, null, CONFIGURE_FLAG_ENCODE)
```

- **Codec**: H.264/AVC
- **Dimensiones**: Las mismas que `previewWidth × previewHeight` del helper
- **Bitrate**: Calculado como `7.5f × mWidth × mHeight`
- **Container**: MP4 (via MediaMuxer)

#### `cropUvcVideoToPortrait()` — Recorte FFmpeg a portrait

```
Flujo:

1. FFprobe para obtener dimensiones del video:
   "-v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0"

2. Si video ya es portrait (height > width) → skip, retornar path original

3. Calcular región de recorte 9:16:
   cropW = (srcHeight × 9/16) redondeado a par  // 1080 × 0.5625 = 607 → 606
   cropX = (srcWidth - cropW) / 2               // (1920 - 606) / 2 = 657

4. Comando FFmpeg:
   -y -i "input"
   -vf "crop=606:1080:657:0,scale=1080:1920:flags=lanczos"
   -c:v libx264 -crf 18 -preset fast -pix_fmt yuv420p
   -c:a copy
   "output"

5. Si éxito → eliminar original, retornar cropped
6. Si fallo → retornar original (fallback seguro)
```

**Diagrama del recorte:**
```
Video UVC original (1920×1080):
┌──────────┬────────────┬──────────┐
│          │            │          │
│  657px   │  606px     │  657px   │
│  (desc.) │  (centro   │  (desc.) │
│          │   9:16)    │          │
│          │            │          │
└──────────┴────────────┴──────────┘

Después de crop + scale:
┌────────────┐
│            │
│ 1080×1920  │
│  (9:16     │
│   portrait)│
│            │
└────────────┘
```

### 7.9 `saveUvcVideoToGallery()` — Guardar en MediaStore

```kotlin
// 1. Validar archivo source
val srcFile = File(videoPath)
if (!srcFile.exists() || srcFile.length() == 0L) → callback(null)

// 2. Crear entrada en MediaStore
val contentValues = ContentValues().apply {
    put(DISPLAY_NAME, "VID_yyyyMMdd_HHmmss")
    put(MIME_TYPE, "video/mp4")
    put(RELATIVE_PATH, "Movies/PhotoBooth/$eventName")  // Android 10+
}
val uri = contentResolver.insert(Video.Media.EXTERNAL_CONTENT_URI, contentValues)

// 3. Copiar archivo a MediaStore via OutputStream
contentResolver.openOutputStream(uri)?.use { out ->
    srcFile.inputStream().use { inp -> inp.copyTo(out) }
}

// 4. Eliminar archivo temporal
srcFile.delete()

// 5. Retornar content:// URI
callback(uri)
```

### 7.10 `updateResolution()` — Cambio dinámico de resolución (librería)

```java
// UVCCameraHelper.java (descompilado)
public void updateResolution(int width, int height) {
    if (this.previewWidth == width && this.previewHeight == height) return;

    this.previewWidth = width;
    this.previewHeight = height;

    // Destruir handler actual
    if (this.mCameraHandler != null) {
        this.mCameraHandler.release();
        this.mCameraHandler = null;
    }

    // Actualizar aspect ratio
    this.mCamView.setAspectRatio(this.previewWidth / this.previewHeight);

    // Crear NUEVO handler con nuevas dimensiones
    this.mCameraHandler = UVCCameraHandler.createHandler(
        this.mActivity, this.mCamView, 2,
        this.previewWidth, this.previewHeight,
        this.mFrameFormat
    );

    // Reabrir cámara + reiniciar preview
    openCamera(this.mCtrlBlock);
    new Thread(() -> startPreview(mCamView)).start();
}
```

### 7.11 Limitaciones del protocolo UVC con DJI Osmo Pocket 3

| Limitación | Detalle | Workaround |
|-----------|---------|------------|
| **Solo 1920×1080** | UVC siempre envía landscape independientemente del modo de la DJI | `cropUvcVideoToPortrait()` con FFmpeg |
| **No hay 4K por UVC** | 4K es interno (grabación a SD) | Se usa 1080p |
| **Modo vertical = pillarbox** | En modo 9:16, la DJI envía contenido portrait centrado con barras negras en frame 16:9 | Crop center 9:16 + scale |
| **Integer division** | `setAspectRatio(1920/1080)` = `setAspectRatio(1)` (cuadrado) | `setAspectRatio(0.0)` |
| **PendingIntent crash** | Android 12+ requiere FLAG_IMMUTABLE | `registerUsbMonitorSafe()` con reflexión |
| **SurfaceTexture race** | Library's 500ms delay vs onSurfaceCreated timing | Safety net + onSurfaceChanged restart |
| **Audio hijack** | DJI se convierte en dispositivo de audio USB | `forceAudioToSpeaker()` |
| **RenderHandler null** | `onPause()` destruye RenderHandler → crash | NO usar onPause/onResume |

### 7.12 `forceAudioToSpeaker()` — Fix de audio USB

Cuando se conecta la DJI por USB, Android la detecta como dispositivo de audio y redirige todo el audio allí (los beeps de cuenta atrás no se oyen).

```kotlin
private fun forceAudioToSpeaker() {
    val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

    // Buscar el speaker integrado
    val speaker = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    // Configurar ToneGenerator para usar speaker
    ToneGenerator.preferredOutputDevice = speaker

    // Android 12+: configurar dispositivo de comunicación
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        speaker?.let { audioManager.setCommunicationDevice(it) }
    }
}
```

---

## 8. Pipeline de Efectos de Foto

### Orden de aplicación

```
Captura (bitmap raw)
  │
  ▼
1. Filtro de color ──────── applyFilter(bitmap, filterMode)
  │
  ▼
2. Eliminación de fondo ── processWithBackgroundRemoval(bitmap)
  │                         └── ML Kit SelfieSegmenter
  ▼
3. Marco overlay ─────────── applyFrame(bitmap, framePath)
  │
  ▼
4. Filtro facial ──────────── applyFaceFilterToPhoto(bitmap)
  │                            └── ML Kit FaceDetection + FaceFilterHelper
  ▼
5. Guardar en galería ────── savePhotoToGallery(bitmap)
  │
  ▼
6. Preview ────────────────── showPreview(bitmap, null)
```

### Filtros de color — `applyFilter()`

Implementados con `ColorMatrix` de Android:

| Filtro | Implementación |
|--------|---------------|
| **B/N (bw)** | `ColorMatrix().setSaturation(0f)` |
| **Sepia** | Desaturar + multiplicar por matriz `[1.2, 0, 0; 1.0, 0, 0; 0.8, 0, 0]` |
| **Vintage** | Desaturar parcial (0.5) + shift amarillo `[+10, +10, -20]` |
| **Contraste** | `setScale(1.5, 1.5, 1.5, 1.0)` + offset `[-0.25×255]` |
| **Invertido** | `setScale(-1, -1, -1, 1)` + offset `[255, 255, 255, 0]` |
| **Frío** | `setScale(0.9, 0.95, 1.2, 1.0)` (boost azul, reduce rojo) |

### Eliminación de fondo — ML Kit Selfie Segmenter

```kotlin
val segmenter = Segmentation.getClient(
    SelfieSegmenterOptions.Builder()
        .setDetectorMode(SINGLE_IMAGE_MODE)
        .enableRawSizeMask()
        .build()
)

val result = segmenter.process(InputImage.fromBitmap(bitmap, 0))
// result.mask → ByteBuffer de confianza [0.0, 1.0] por pixel

// Para cada pixel:
if (confidence > 0.7) → mantener persona
else if (confidence > 0.3) → blend (transición suave)
else → reemplazar con fondo
```

---

## 9. Pipeline de Efectos de Video

### Slow Motion — FFmpeg

```bash
# 0.5x speed
-y -i "input.mp4"
-filter_complex "[0:v]setpts=2.0*PTS[v];[0:a]atempo=0.5[a]"
-map "[v]" -map "[a]"
-c:v libx264 -crf 18 -preset fast
"output.mp4"
```

### Boomerang — MediaCodec nativo

El boomerang usa decodificación/recodificación nativa (sin FFmpeg) para control preciso:

```
Flujo:
1. MediaExtractor → extraer todos los frames del video
2. MediaCodec decoder → YUV frames
3. Almacenar frames en memoria (byte arrays)
4. Crear secuencia: frames[0→N] + frames[N→0] (ida y vuelta)
5. Aplicar curva de velocidad variable
6. MediaCodec encoder → H.264
7. MediaMuxer → MP4
```

### Filtros en video — FFmpeg

```bash
# B/N
-vf "hue=s=0"

# Sepia
-vf "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"

# Vintage
-vf "hue=s=0.5,curves=vintage"

# Contraste
-vf "eq=contrast=2:brightness=-0.1"

# Invertido
-vf "negate"

# Frío
-vf "colorbalance=bs=-0.2:ms=-0.1:hs=0.1"
```

### Marco en video — FFmpeg overlay

```bash
# Solo marco (escalar frame al tamaño del video)
-y -i "video.mp4" -i "frame.png"
-filter_complex "[1:v]scale2ref[frame][video];[video][frame]overlay=0:0"
-c:v libx264 -crf 18 -preset fast -pix_fmt yuv420p -c:a copy
"output.mp4"

# Filtro + marco
-y -i "video.mp4" -i "frame.png"
-filter_complex "[0:v]hue=s=0[filtered];[1:v]scale=iw:ih[scaled];[filtered][scaled]overlay=0:0"
-c:v libx264 -crf 18 -preset fast -pix_fmt yuv420p -c:a copy
"output.mp4"
```

---

## 10. Modo Photo Booth (4 fotos)

### Flujo

```
1. Usuario pulsa captura
2. Cuenta atrás 3s con beeps
3. Captura foto #1
4. Pausa 3s
5. Cuenta atrás 3s
6. Captura foto #2
7. Pausa 3s
8. Cuenta atrás 3s
9. Captura foto #3
10. Pausa 3s
11. Cuenta atrás 3s
12. Captura foto #4
13. createPhotoBoothGrid() → composición final
14. Guardar + preview
```

### Composición — `createPhotoBoothGrid()`

Crea una **tira vertical** con las 4 fotos:

```
┌──────────────┐
│   Foto #1    │
├──────────────┤
│   Foto #2    │
├──────────────┤
│   Foto #3    │
├──────────────┤
│   Foto #4    │
└──────────────┘
```

Cada foto se escala al mismo ancho, manteniendo aspect ratio.

---

## 11. Sistema de Preview y Compartición

### PreviewActivity

**Funciones disponibles:**

| Acción | Implementación |
|--------|---------------|
| **Reproducir video** | `VideoView.setVideoURI(content://...)` con looping |
| **Mutear audio** | MediaExtractor + MediaMuxer: copiar solo track de video |
| **Añadir música** | Selector de música desde assets + FFmpeg mux |
| **Compartir por email** | `Intent(ACTION_SEND)` con `setType("image/*")` o `"video/*"` |
| **Compartir por WhatsApp** | Intent directo a `com.whatsapp` o `com.whatsapp.w4b` |
| **WhatsApp a número** | `Intent(ACTION_VIEW)` con URL `wa.me/$numero` |
| **Guardar contacto** | `PendingSharesManager.addPendingShare()` |
| **Otra foto** | Dialog de confirmación → volver a CameraActivity |

### Oscillating Playback (Boomerang preview)

```kotlin
fun startSpeedOscillation() {
    // Alterna velocidad entre 0.5x y 2.0x
    // Crea efecto visual de boomerang sin re-encodear
    videoView.setPlaybackParams(PlaybackParams().setSpeed(currentSpeed))
}
```

---

## 12. Procesamiento Pesado (ProcessingActivity)

Se usa para eliminación de fondo en **videos** (no fotos):

```
Flujo completo:

1. EXTRAER FRAMES (FFmpeg)
   ffmpeg -i video.mp4 -vf fps=N frame_%04d.png
   └── FPS detectado via FFprobe

2. PROCESAR CADA FRAME (ML Kit, paralelo)
   Para cada frame PNG:
   - Cargar bitmap
   - ML Kit SelfieSegmenter → máscara
   - Aplicar fondo virtual con blending
   - Guardar frame procesado

   Sincronización: CountDownLatch con timeout de 5s por frame

3. RECOMPONER VIDEO (FFmpeg)
   ffmpeg -framerate N -i frame_%04d.png -i audio.aac
   -c:v libx264 -pix_fmt yuv420p output.mp4

4. APLICAR MARCO (opcional, FFmpeg)
   ffmpeg -i output.mp4 -i frame.png
   -filter_complex overlay ...

5. APLICAR SLOW MOTION (opcional, FFmpeg)
   ffmpeg -i output.mp4
   -filter_complex "setpts=2.0*PTS;atempo=0.5" ...

6. GUARDAR EN MEDIASTORE
   ContentValues + contentResolver.insert()

7. RETORNAR URI via setResult(RESULT_OK)

Progress UI:
┌────────────────────────────────┐
│  Procesando video...           │
│                                │
│  ████████████░░░░░░░░  60%     │
│                                │
│  Paso 2 de 5: Segmentación     │
└────────────────────────────────┘
```

---

## 13. Sistema de Marcos y Fondos

### Estructura de assets

```
assets/
├── marcos/
│   ├── verano/
│   │   ├── marco_playa.png
│   │   └── marco_sol.png
│   ├── navidad/
│   │   ├── marco_arbol.png
│   │   └── marco_nieve.png
│   ├── cumpleaños/
│   ├── invierno/
│   ├── comunion/
│   ├── boda/
│   └── otros/
└── fondos/
    ├── naturaleza/
    │   ├── playa.jpg
    │   └── montaña.jpg
    ├── ciudad/
    └── abstracto/
```

### Detección automática

```kotlin
// FramePreviewActivity
fun loadThemes() {
    val themes = assets.list("marcos")?.map { folderName ->
        Theme(
            path = "marcos/$folderName",
            displayName = folderName.capitalize(),
            icon = getEmojiForTheme(folderName)
        )
    }
}

fun loadFramesForTheme(theme: Theme) {
    val frames = assets.list(theme.path)
        ?.filter { it.endsWith(".png") || it.endsWith(".jpg") }
        ?.map { FrameInfo(path = "${theme.path}/$it", name = it) }
}
```

> **Sin código necesario**: Para añadir marcos/fondos, solo hay que copiar imágenes PNG/JPG a la carpeta correspondiente en `assets/`. La app los detecta automáticamente.

---

## 14. Filtros Faciales con ML Kit

### FaceFilterHelper (Singleton)

```kotlin
object FaceFilterHelper {
    fun applyFaceFilter(
        bitmap: Bitmap,
        filterBitmap: Bitmap,
        filterType: String,  // "mustache", "hat", "glasses", "mask", "ears", "nose", "full"
        context: Context
    ): Bitmap
}
```

### Algoritmos de posicionamiento por tipo

| Tipo | Anclaje | Escala | Posición |
|------|---------|--------|----------|
| **Hat (🎩)** | Sobre la cabeza | ancho cara × 1.5 | Y = top face - height×0.8 |
| **Ears (🐱)** | Cara completa | ancho cara × 1.8 | Centrado en cara |
| **Glasses (👓)** | Entre los ojos | distancia ojos × 2.5 | Y = centro de ojos |
| **Mustache (👨)** | Nariz→boca | ancho boca × 1.3 | Y = entre nariz y boca |
| **Mask (😷)** | Bajo nariz | ancho cara | Y = nariz base |
| **Nose (🔴)** | Punta nariz | directo | Centro = nariz tip |
| **Full (🎭)** | Cara completa | bounding box | Sobre toda la cara |

### Rotación

```kotlin
// Usa headEulerAngleZ para rotar el filtro con la cabeza
val rotation = face.headEulerAngleZ
canvas.rotate(rotation, centerX, centerY)
canvas.drawBitmap(scaledFilter, x, y, antiAliasPaint)
```

---

## 15. Sistema de Audio

### ToneGenerator.kt

Genera tonos de beep para la cuenta atrás:

```kotlin
object ToneGenerator {
    var preferredOutputDevice: AudioDeviceInfo? = null

    fun playBeep(frequencyHz: Int = 880, durationMs: Int = 200) {
        val sampleRate = 44100
        val numSamples = sampleRate * durationMs / 1000
        val buffer = ShortArray(numSamples)

        // Generar onda sinusoidal
        for (i in 0 until numSamples) {
            buffer[i] = (sin(2.0 * PI * frequencyHz * i / sampleRate) * Short.MAX_VALUE).toInt().toShort()
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(...)
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build())
            .setBufferSizeInBytes(buffer.size * 2)
            .build()

        // Forzar salida por speaker (no por USB)
        preferredOutputDevice?.let { audioTrack.setPreferredDevice(it) }

        audioTrack.play()
        audioTrack.write(buffer, 0, buffer.size)
    }
}
```

---

## 16. Compartición Diferida (PendingShares)

### Flujo

```
1. Usuario graba foto/video
2. En PreviewActivity, pulsa "Guardar contacto"
3. Introduce email o número de WhatsApp
4. PendingSharesManager guarda en JSON
5. Más tarde, desde EventGalleryActivity:
   - Badge con número de pendientes
   - Abrir PendingSharesActivity
   - Enviar cada uno por email/WhatsApp
   - Marcar como enviado
```

### Estructura de datos

```kotlin
data class PendingShare(
    val id: String,              // UUID
    val eventName: String,       // "CumpleañosGloria"
    val filePath: String?,       // Ruta local (legacy)
    val fileUri: String?,        // content:// URI
    val fileType: String,        // "PHOTO" | "VIDEO"
    val contact: String,         // "email@test.com" | "+34612345678"
    val contactType: String,     // "EMAIL" | "PHONE"
    val timestamp: Long,         // System.currentTimeMillis()
    val sent: Boolean            // false → pendiente, true → enviado
)
```

### Persistencia

```
Archivo: context.filesDir/pending_shares.json
Formato: JSONArray de objetos PendingShare
Sin base de datos, sin Room, sin SharedPreferences → JSON puro
```

---

## 17. Galería de Eventos

### EventGalleryActivity

- **Consulta**: `MediaStore` con filtro `RELATIVE_PATH LIKE "%PhotoBooth/$eventName%"`
- **Filtros**: FilterChips (Todos / Fotos / Videos)
- **Grid**: 3 columnas con thumbnails via Glide
- **Acciones**: Compartir, eliminar (con confirmación Android 11+)
- **Badge**: Número de comparticiones pendientes

---

## 18. Manifest y Permisos

### Permisos declarados

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
```

### Features opcionales

```xml
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.usb.host" android:required="false" />
```

> Ambos son `required="false"` para que la app funcione en dispositivos sin cámara o sin USB host.

### CameraActivity — Configuración especial

```xml
<activity
    android:name=".CameraActivity"
    android:launchMode="singleTask"
    android:screenOrientation="portrait">

    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
    </intent-filter>
    <meta-data
        android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
        android:resource="@xml/device_filter" />
</activity>
```

- **`singleTask`**: Solo una instancia. Reconexión USB reutiliza la misma actividad.
- **`USB_DEVICE_ATTACHED`**: La app se abre automáticamente cuando se conecta un dispositivo USB compatible.
- **`device_filter`**: Filtro XML que define qué dispositivos USB disparan la apertura automática.

---

## 19. Layouts XML

### Resumen de los 24 layouts

| Layout | Tipo | Componentes clave |
|--------|------|-------------------|
| `activity_event_name.xml` | Activity | RecyclerView + TextInput + FAB |
| `activity_menu.xml` | Activity | 2 botones: Foto/Video |
| `activity_photo_config.xml` | Activity | Spinners, switches, botones de selección |
| `activity_video_config.xml` | Activity | SeekBars, spinners, switches |
| `activity_camera.xml` | Activity | PreviewView + FrameLayout(UVC) + overlays |
| `activity_preview.xml` | Activity | ImageView/VideoView + share buttons |
| `activity_processing.xml` | Activity | ProgressBar + label + steps |
| `activity_gallery.xml` | Activity | RecyclerView grid 2 cols |
| `activity_event_gallery.xml` | Activity | FilterChips + RecyclerView 3 cols |
| `activity_frame_preview.xml` | Activity | CameraPreview + 2 RecyclerViews |
| `activity_background_preview.xml` | Activity | ImageView + 2 RecyclerViews |
| `activity_face_filter.xml` | Activity | RecyclerView grid 3 cols |
| `activity_pending_shares.xml` | Activity | RecyclerView lista |
| `item_event.xml` | Item | MaterialCardView con nombre + conteo |
| `item_gallery_photo.xml` | Item | CardView 200dp con thumbnail |
| `item_frame.xml` | Item | CardView 100×120dp + checkmark overlay |
| `item_background.xml` | Item | CardView 100×100dp + purple overlay |
| `item_theme.xml` | Item | CardView 70×70dp con emoji |
| `item_face_filter.xml` | Item | CardView con imagen + nombre + tipo |
| `item_media.xml` | Item | ImageView + video indicator |
| `item_pending_share.xml` | Item | Thumbnail + contacto + actions |
| `dialog_take_another.xml` | Dialog | Sí/No confirmación |
| `dialog_share_options.xml` | Dialog | 5 opciones de compartición |

### Layout de CameraActivity (el más complejo)

```xml
<FrameLayout>
    <!-- Cámara del teléfono (CameraX) -->
    <PreviewView android:id="@+id/previewView" />

    <!-- Cámara USB (contenedor dinámico) -->
    <FrameLayout android:id="@+id/uvcPreviewContainer" />

    <!-- Overlay de marco (transparente) -->
    <ImageView android:id="@+id/frameOverlayView" />

    <!-- Overlay de filtro (ColorFilter) -->
    <View android:id="@+id/filterOverlay" />

    <!-- Indicador de grabación (punto rojo) -->
    <View android:id="@+id/recordingIndicator" />

    <!-- Texto de cuenta atrás -->
    <TextView android:id="@+id/countdownText" />

    <!-- Estado USB -->
    <LinearLayout android:id="@+id/usbStatusContainer">
        <TextView android:id="@+id/usbStatusText" />
        <Button android:id="@+id/buttonUsbRetry" />
    </LinearLayout>

    <!-- Controles -->
    <Button android:id="@+id/buttonCapture" />
    <Button android:id="@+id/buttonFlipCamera" />
    <Button android:id="@+id/buttonGallery" />

    <!-- Overlay de procesamiento -->
    <FrameLayout android:id="@+id/progressOverlay">
        <ProgressBar android:id="@+id/progressBar" />
        <TextView android:id="@+id/progressLabel" />
        <TextView android:id="@+id/progressPercent" />
    </FrameLayout>
</FrameLayout>
```

---

## 20. Limitaciones Conocidas

| # | Limitación | Detalle | Estado |
|---|-----------|---------|--------|
| 1 | **UVC siempre 1920×1080** | DJI Osmo Pocket 3 no anuncia resoluciones portrait por descriptores USB | Mitigado con FFmpeg crop |
| 2 | **No hay 4K por USB** | El protocolo UVC del DJI Osmo limita a 1080p | Sin solución (limitación hardware) |
| 3 | **Calidad de recorte portrait** | Escalar 606×1080 → 1080×1920 implica upscaling | Lanczos minimiza artefactos |
| 4 | **No onPause/onResume UVC** | Llamar onPause destruye RenderHandler → crash | Eliminado lifecycle overrides |
| 5 | **Singleton UVCCameraHelper** | getInstance() ignora dimensiones tras primera creación | updateResolution() como alternativa |
| 6 | **Audio USB hijack** | DJI se convierte en dispositivo de audio → beeps silenciosos | forceAudioToSpeaker() |
| 7 | **Android 12+ PendingIntent** | AAR usa flags=0 → crash | Reflexión con FLAG_IMMUTABLE |
| 8 | **Integer division aspect** | 1920/1080=1 (no 1.78) → preview cuadrado | setAspectRatio(0.0) |
| 9 | **ML Kit procesamiento lento** | Background removal en video puede tardar minutos | CountDownLatch con timeout |
| 10 | **No FileProvider** | Se usa MediaStore content:// URIs directamente | Funciona en Android 10+ |

---

## 21. Instalación y Ejecución

### Prerrequisitos

1. **Android Studio** (Arctic Fox o superior)
2. **JDK 8+**
3. **Android SDK Platform 34**
4. **NDK** (para librerías nativas de UVC)

### Build desde terminal

```bash
# Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK (requiere signing config)
./gradlew assembleRelease
```

### Build desde Android Studio

1. Abrir el proyecto `fotomaton2/`
2. Esperar sincronización de Gradle
3. Conectar dispositivo Android vía USB (con USB Debugging habilitado)
4. Pulsar Run ▶️

### Instalación manual del APK

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Uso con DJI Osmo Pocket 3

1. Configurar la DJI en modo **Webcam** (Configuración → Conexión → USB → Webcam)
2. Conectar la DJI al teléfono Android con cable USB-C
3. Aceptar el permiso USB cuando aparezca
4. La app detectará automáticamente la cámara y mostrará "📷 USB conectada (1920x1080)"
5. Si la DJI anuncia resolución portrait, la app cambiará automáticamente

### Añadir marcos/fondos/filtros faciales

```
Marcos:      assets/marcos/[nombre_tema]/archivo.png
Fondos:      assets/fondos/[nombre_tema]/archivo.jpg
Filtros:     assets/face_filters/[tipo]/archivo.png
Música:      assets/music/cancion.mp3

Tipos de filtro facial: mustache, hat, glasses, mask, ears, nose, full
```

Los marcos deben ser PNG con transparencia. La zona transparente mostrará la foto/video.

---

## Diagrama de Dependencias

```
┌─────────────────────────────────────────────┐
│              PhotoBooth App                  │
├─────────────┬──────────────┬────────────────┤
│   CameraX   │  USB Camera  │   ML Kit       │
│   1.3.1     │  AAR 2.3.8   │   Face + Seg   │
├─────────────┼──────────────┼────────────────┤
│   FFmpeg    │  libusbcommon│   Glide        │
│   Kit 6.1.1 │  AAR 4.1.1   │   4.16.0       │
├─────────────┴──────────────┴────────────────┤
│           Material Design 3                  │
│           AndroidX Core + AppCompat          │
│           View Binding + Data Binding        │
└─────────────────────────────────────────────┘
```

---

*Documentación generada el 12 de marzo de 2026. Refleja el estado actual del código fuente.*
