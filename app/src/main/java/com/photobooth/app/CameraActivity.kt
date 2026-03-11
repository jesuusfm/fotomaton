package com.photobooth.app

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.photobooth.app.databinding.ActivityCameraBinding
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteBuffer
import android.widget.ImageView

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var recordingTimer: CountDownTimer? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private lateinit var cameraExecutor: ExecutorService
    private var lastSavedPhotoUri: Uri? = null

    // Configuration from intent
    private lateinit var eventName: String
    private lateinit var mode: String // PHOTO or VIDEO
    private var filterMode = "normal" // normal or bw
    private var isPhotoBoothMode = false
    private var photoQuality = "MAX" // MAX, HIGH, STANDARD
    private var videoDuration = 10
    private var videoQuality = "FHD" // SD, HD, FHD, UHD
    private var slowMotionMode = "normal" // normal, 0.5x, boomerang, boomerang_reverse
    private var boomerangMinSpeed = 0.5f
    private var boomerangMaxSpeed = 1.0f
    private var boomerangFrequency = 3.0f   // slow segment duration (seconds)
    private var boomerangFastDuration = 3.0f // fast segment duration (seconds)
    private var frameMode = "none"
    private var framePath = ""
    private var backgroundMode = "none"
    private var backgroundPath = ""
    private var removeBackground = false
    private var faceFilterType = "none"
    private var faceFilterPath = ""
    private var cameraSource = "phone" // "phone" or "usb"
    
    // UVC camera
    private var uvcCameraHelper: com.jiangdg.usbcamera.UVCCameraHelper? = null
    private var uvcTextureView: com.serenegiant.usb.widget.UVCCameraTextureView? = null
    private var isUvcPreview = false
    private var isUvcCameraOpened = false

    // Photo booth
    private val photoBoothBitmaps = mutableListOf<Bitmap>()
    private var currentPhotoBoothCount = 0

    // Gallery for current session
    private val sessionPhotos = mutableListOf<Bitmap>()
    
    // Processing activity launcher
    private val processingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("CameraActivity", "ProcessingActivity result: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val uriString = data.getStringExtra("RESULT_URI")
                android.util.Log.d("CameraActivity", "Result URI: $uriString")
                if (uriString != null) {
                    val uri = Uri.parse(uriString)
                    if (slowMotionMode == "boomerang_reverse") {
                        android.util.Log.d("CameraActivity", "Background removed - now processing boomerang reverse")
                        processBoomerangVideo(uri)
                    } else {
                        android.util.Log.d("CameraActivity", "Showing preview for processed video")
                        showPreview(null, uri)
                    }
                } else {
                    android.util.Log.e("CameraActivity", "ERROR: No URI in result")
                }
            }
        } else {
            android.util.Log.e("CameraActivity", "Processing failed or cancelled")
            Toast.makeText(this, "Error en el procesamiento", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Photo preview launcher
    private val photoPreviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val uriString = data.getStringExtra("RESULT_URI")
                val hasFilter = data.getBooleanExtra("HAS_FILTER", false)
                if (uriString != null) {
                    val uri = Uri.parse(uriString)
                    showPreview(null, uri)
                    if (hasFilter) {
                        Toast.makeText(this, "Foto guardada con filtro", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        // If cancelled, temporary file is already deleted by PhotoPreviewActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get configuration
        eventName = intent.getStringExtra("EVENT_NAME") ?: "Evento"
        mode = intent.getStringExtra("MODE") ?: "PHOTO"
        filterMode = intent.getStringExtra("FILTER") ?: "normal"
        isPhotoBoothMode = intent.getBooleanExtra("PHOTO_BOOTH", false)
        photoQuality = intent.getStringExtra("PHOTO_QUALITY") ?: "MAX"
        videoDuration = intent.getIntExtra("VIDEO_DURATION", 10)
        videoQuality = intent.getStringExtra("VIDEO_QUALITY") ?: "UHD"
        slowMotionMode = intent.getStringExtra("SLOW_MOTION_MODE") ?: "normal"
        
        // Debug logging
        android.util.Log.d("CameraActivity", "=== CONFIGURACIÓN RECIBIDA ===")
        android.util.Log.d("CameraActivity", "mode=$mode, videoQuality=$videoQuality, photoQuality=$photoQuality")
        android.util.Log.d("CameraActivity", "VIDEO_QUALITY extra: ${intent.getStringExtra("VIDEO_QUALITY")}")
        
        boomerangMinSpeed  = intent.getFloatExtra("BOOMERANG_MIN_SPEED", 0.5f)
        boomerangMaxSpeed = intent.getFloatExtra("BOOMERANG_MAX_SPEED", 1.0f)
        boomerangFrequency = intent.getFloatExtra("BOOMERANG_SLOW_DURATION", 3.0f)
        boomerangFastDuration = intent.getFloatExtra("BOOMERANG_FAST_DURATION", 3.0f)
        frameMode = intent.getStringExtra("FRAME") ?: "none"
        framePath = intent.getStringExtra("FRAME_PATH") ?: ""
        backgroundMode = intent.getStringExtra("BACKGROUND") ?: "none"
        backgroundPath = intent.getStringExtra("BACKGROUND_PATH") ?: ""
        removeBackground = intent.getBooleanExtra("REMOVE_BG", false)
        faceFilterType = intent.getStringExtra("FACE_FILTER_TYPE") ?: "none"
        faceFilterPath = intent.getStringExtra("FACE_FILTER_PATH") ?: ""
        cameraSource = intent.getStringExtra("CAMERA_SOURCE") ?: "phone"
        
        // Update filter indicator
        updateFilterIndicator()
        
        // Apply filter overlay effect
        applyFilterOverlay()
        
        // Load and show frame overlay if frame is selected
        if (frameMode != "none" && framePath.isNotEmpty()) {
            loadFrameOverlay()
        }
        
        // Request permissions
        if (allPermissionsGranted()) {
            if (cameraSource == "usb") {
                startUvcCamera()
            } else {
                startCamera()
            }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Button listeners
        binding.buttonFlipCamera.setOnClickListener {
            flipCamera()
        }
        
        // Hide flip camera button for USB camera (only one external camera)
        if (cameraSource == "usb") {
            binding.buttonFlipCamera.visibility = View.GONE
        }

        binding.buttonCapture.setOnClickListener {
            if (recording != null || (cameraSource == "usb" && uvcCameraHelper?.isPushing == true)) {
                stopVideoRecording()
            } else {
                startCountdown()
            }
        }

        binding.buttonGallery.setOnClickListener {
            showGallery()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            if (mode == "PHOTO") {
                // Configure image capture based on quality
                val captureMode = when (photoQuality) {
                    "MAX" -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    "HIGH" -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    else -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                }
                
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(captureMode)
                    .build()
                
                val qualityName = when (photoQuality) {
                    "MAX" -> "Máxima (4K+)"
                    "HIGH" -> "Alta"
                    else -> "Estándar"
                }
                Toast.makeText(this, "📷 Calidad: $qualityName", Toast.LENGTH_SHORT).show()
                android.util.Log.d("CameraActivity", "Foto - Calidad: $photoQuality, CaptureMode: $captureMode")

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture
                    )
                } catch (exc: Exception) {
                    Toast.makeText(this, "Error al iniciar la cámara", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Map quality string to CameraX Quality
                val requestedQuality = when (videoQuality) {
                    "SD" -> Quality.SD
                    "HD" -> Quality.HD
                    "FHD" -> Quality.FHD
                    "UHD" -> Quality.UHD
                    else -> Quality.FHD
                }
                
                // Let CameraX pick the best available quality (handles front/back camera differences correctly)
                val qualitySelector = QualitySelector.fromOrderedList(
                    listOf(requestedQuality, Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
                android.util.Log.d("CameraActivity", "QualitySelector solicitado: $videoQuality")
                
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                
                android.util.Log.d("CameraActivity", "VideoCapture configurado. videoQuality param=$videoQuality")

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, videoCapture
                    )
                } catch (exc: Exception) {
                    Toast.makeText(this, "Error al iniciar la cámara", Toast.LENGTH_SHORT).show()
                }
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startUvcCamera() {
        // Hide CameraX preview, show UVC container
        binding.previewView.visibility = View.GONE
        binding.uvcPreviewContainer.visibility = View.VISIBLE
        binding.usbStatusContainer.visibility = View.VISIBLE
        binding.usbStatusText.text = "🔌 Buscando cámara USB..."
        binding.buttonUsbRetry.visibility = View.GONE

        try {
            // Cleanup previous UVC state (needed for retry: release() nulls mUSBMonitor
            // so that setDefaultFrameFormat/setDefaultPreviewSize don't throw)
            try {
                uvcCameraHelper?.closeCamera()
                uvcCameraHelper?.release()
            } catch (_: Throwable) {}

            // Create UVCCameraTextureView programmatically to avoid native lib crash at layout inflation
            val textureView = com.serenegiant.usb.widget.UVCCameraTextureView(this)
            textureView.layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            binding.uvcPreviewContainer.removeAllViews()
            binding.uvcPreviewContainer.addView(textureView)
            uvcTextureView = textureView
            val cameraView = textureView as com.serenegiant.usb.widget.CameraViewInterface

            // DJI Osmo Pocket 3 outputs 1080p over UVC (4K is internal only)
            uvcCameraHelper = com.jiangdg.usbcamera.UVCCameraHelper.getInstance(1920, 1080)
            uvcCameraHelper?.setDefaultFrameFormat(com.jiangdg.usbcamera.UVCCameraHelper.FRAME_FORMAT_MJPEG)

            cameraView.setCallback(object : com.serenegiant.usb.widget.CameraViewInterface.Callback {
                override fun onSurfaceCreated(view: com.serenegiant.usb.widget.CameraViewInterface, surface: android.view.Surface) {
                    if (!isUvcPreview && uvcCameraHelper?.isCameraOpened == true) {
                        uvcCameraHelper?.startPreview(cameraView)
                        isUvcPreview = true
                    }
                }
                override fun onSurfaceChanged(view: com.serenegiant.usb.widget.CameraViewInterface, surface: android.view.Surface, width: Int, height: Int) {}
                override fun onSurfaceDestroy(view: com.serenegiant.usb.widget.CameraViewInterface, surface: android.view.Surface) {
                    if (isUvcPreview && uvcCameraHelper?.isCameraOpened == true) {
                        uvcCameraHelper?.stopPreview()
                        isUvcPreview = false
                    }
                }
            })

            val devListener = object : com.jiangdg.usbcamera.UVCCameraHelper.OnMyDevConnectListener {
                override fun onAttachDev(device: android.hardware.usb.UsbDevice) {
                    runOnUiThread {
                        binding.usbStatusText.text = "🔌 Dispositivo USB detectado, solicitando permiso..."
                        uvcCameraHelper?.requestPermission(0)
                    }
                }
                override fun onDettachDev(device: android.hardware.usb.UsbDevice) {
                    runOnUiThread {
                        if (isUvcPreview) {
                            uvcCameraHelper?.closeCamera()
                            isUvcPreview = false
                        }
                        isUvcCameraOpened = false
                        binding.usbStatusContainer.visibility = View.VISIBLE
                        binding.usbStatusText.text = "🔌 Cámara USB desconectada"
                        binding.buttonUsbRetry.visibility = View.VISIBLE
                        binding.buttonUsbRetry.setOnClickListener { startUvcCamera() }
                        Toast.makeText(this@CameraActivity, "Cámara USB desconectada", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onConnectDev(device: android.hardware.usb.UsbDevice, isConnected: Boolean) {
                    runOnUiThread {
                        if (isConnected) {
                            isUvcCameraOpened = true
                            binding.usbStatusContainer.visibility = View.GONE
                            // Log supported sizes for debugging
                            val sizes = uvcCameraHelper?.getSupportedPreviewSizes()
                            val sizeStr = sizes?.joinToString(", ") { "${it.width}x${it.height}" } ?: "unknown"
                            android.util.Log.i("CameraActivity", "UVC supported sizes: $sizeStr")
                            val actualW = uvcCameraHelper?.previewWidth ?: 0
                            val actualH = uvcCameraHelper?.previewHeight ?: 0
                            Toast.makeText(this@CameraActivity, "📷 USB conectada (${actualW}x${actualH})", Toast.LENGTH_SHORT).show()
                        } else {
                            binding.usbStatusContainer.visibility = View.VISIBLE
                            binding.usbStatusText.text = "⚠️ No se pudo abrir la cámara USB"
                            binding.buttonUsbRetry.visibility = View.VISIBLE
                            binding.buttonUsbRetry.setOnClickListener { startUvcCamera() }
                        }
                    }
                }
                override fun onDisConnectDev(device: android.hardware.usb.UsbDevice) {
                    runOnUiThread {
                        isUvcCameraOpened = false
                    }
                }
            }

            uvcCameraHelper?.initUSBMonitor(this, cameraView, devListener)
            registerUsbMonitorSafe()

            // Force audio to phone speaker (DJI USB hijacks audio output)
            forceAudioToSpeaker()
        } catch (e: Throwable) {
            android.util.Log.e("CameraActivity", "Error initializing UVC camera", e)
            binding.usbStatusText.text = "⚠️ Error UVC: ${e.message}"
            binding.buttonUsbRetry.visibility = View.VISIBLE
            binding.buttonUsbRetry.setOnClickListener { startUvcCamera() }
            Toast.makeText(this, "Error cámara USB: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Register USBMonitor with FLAG_IMMUTABLE fix for Android 12+ (API 31+).
     * The AAR's USBMonitor.register() creates PendingIntent with flags=0, which
     * crashes on Android 12+. This method replicates register() using reflection
     * but with the correct PendingIntent flags.
     */
    private fun registerUsbMonitorSafe() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Pre-Android 12: standard registration works fine
            uvcCameraHelper?.registerUSB()
            return
        }

        // Android 12+: Need FLAG_IMMUTABLE for PendingIntent
        val usbMonitor = uvcCameraHelper?.getUSBMonitor()
            ?: throw IllegalStateException("USBMonitor is null after initUSBMonitor")

        val monitorClass = usbMonitor.javaClass

        // Check if destroyed
        val destroyedField = monitorClass.getDeclaredField("destroyed")
        destroyedField.isAccessible = true
        if (destroyedField.getBoolean(usbMonitor)) {
            throw IllegalStateException("USBMonitor already destroyed")
        }

        // Check if already registered
        val permIntentField = monitorClass.getDeclaredField("mPermissionIntent")
        permIntentField.isAccessible = true
        if (permIntentField.get(usbMonitor) != null) return

        // Get context from WeakReference
        val weakCtxField = monitorClass.getDeclaredField("mWeakContext")
        weakCtxField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val context = (weakCtxField.get(usbMonitor) as java.lang.ref.WeakReference<android.content.Context>).get()
            ?: throw IllegalStateException("Context is null")

        // Get ACTION_USB_PERMISSION (instance field unique per USBMonitor)
        val actionField = monitorClass.getDeclaredField("ACTION_USB_PERMISSION")
        actionField.isAccessible = true
        val action = actionField.get(usbMonitor) as String

        // Create PendingIntent with FLAG_IMMUTABLE (the fix)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, 0, android.content.Intent(action),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        permIntentField.set(usbMonitor, pendingIntent)

        // Register broadcast receiver
        val receiverField = monitorClass.getDeclaredField("mUsbReceiver")
        receiverField.isAccessible = true
        val receiver = receiverField.get(usbMonitor) as android.content.BroadcastReceiver

        val filter = android.content.IntentFilter(action)
        filter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED")
        filter.addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        // Reset device counts and start periodic device check
        val deviceCountsField = monitorClass.getDeclaredField("mDeviceCounts")
        deviceCountsField.isAccessible = true
        deviceCountsField.setInt(usbMonitor, 0)

        val handlerField = monitorClass.getDeclaredField("mAsyncHandler")
        handlerField.isAccessible = true
        val handler = handlerField.get(usbMonitor) as android.os.Handler

        val runnableField = monitorClass.getDeclaredField("mDeviceCheckRunnable")
        runnableField.isAccessible = true
        val runnable = runnableField.get(usbMonitor) as Runnable

        handler.postDelayed(runnable, 1000)

        android.util.Log.i("CameraActivity", "USBMonitor registered with FLAG_IMMUTABLE fix")
    }

    // Auto-detect USB camera when device is plugged in while activity is open
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // UVCCameraHelper handles USB attach/detach via its own USBMonitor
        // No manual re-init needed here
    }

    private fun startCountdown() {
        binding.countdownText.visibility = View.VISIBLE
        binding.buttonCapture.isEnabled = false
        binding.buttonFlipCamera.isEnabled = false
        binding.buttonGallery.isEnabled = false

        // Show photo booth progress if applicable
        if (isPhotoBoothMode) {
            binding.photoBoothCounter.visibility = View.VISIBLE
            binding.photoBoothCounter.text = "${currentPhotoBoothCount + 1}/4 fotos"
        }

        var countdown = 4 // Start at 4 so first tick shows 3
        
        object : CountDownTimer(3100, 1000) { // 3100ms to ensure all ticks happen
            override fun onTick(millisUntilFinished: Long) {
                countdown--
                if (countdown > 0) {
                    binding.countdownText.text = countdown.toString()
                    ToneGenerator.playBeep(1200) // 1200Hz beep for 3, 2, and 1
                }
            }

            override fun onFinish() {
                binding.countdownText.visibility = View.GONE
                
                if (mode == "PHOTO") {
                    // Small delay before capture sound for photos only
                    Handler(Looper.getMainLooper()).postDelayed({
                        ToneGenerator.playBeep(300) // 300Hz beep for capture
                    }, 100)
                }
                
                if (mode == "PHOTO") {
                    if (isPhotoBoothMode) {
                        if (cameraSource == "usb") takeUvcPhotoBoothPhoto() else takePhotoBoothPhoto()
                    } else {
                        if (cameraSource == "usb") takeUvcPhoto() else takePhoto()
                    }
                } else {
                    if (cameraSource == "usb") startUvcVideoRecording() else startVideoRecording()
                }
                
                binding.buttonCapture.isEnabled = true
                binding.buttonFlipCamera.isEnabled = true
                binding.buttonGallery.isEnabled = true
            }
        }.start()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Capture to bitmap
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    var bitmap = imageProxyToBitmap(image)
                    image.close()

                    // Mirror front camera to match what user saw in preview
                    if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                        val matrix = android.graphics.Matrix().apply { preScale(-1f, 1f) }
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
                    }
                    
                    // Apply effects in order: filter -> background -> frame -> face filter
                    if (filterMode != "normal") {
                        bitmap = applyFilter(bitmap, filterMode)
                    }
                    
                    if ((backgroundMode != "none" || backgroundPath.isNotEmpty()) && removeBackground) {
                        // Process with ML Kit to remove background and apply new one
                        processWithBackgroundRemoval(bitmap) { processedBitmap ->
                            var finalBitmap = processedBitmap
                            if (frameMode != "none" && framePath.isNotEmpty()) {
                                finalBitmap = applyFrame(finalBitmap, framePath)
                            }
                            
                            // Apply face filter if selected
                            if (faceFilterType != "none" && faceFilterPath.isNotEmpty()) {
                                applyFaceFilterToPhoto(finalBitmap) { filteredBitmap ->
                                    sessionPhotos.add(filteredBitmap)
                                    savePhotoToGallery(filteredBitmap)
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        showPreview(filteredBitmap, null)
                                    }, 200)
                                }
                            } else {
                                sessionPhotos.add(finalBitmap)
                                savePhotoToGallery(finalBitmap)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    showPreview(finalBitmap, null)
                                }, 200)
                            }
                        }
                    } else {
                        if (frameMode != "none" && framePath.isNotEmpty()) {
                            bitmap = applyFrame(bitmap, framePath)
                        }
                        
                        // Apply face filter if selected
                        if (faceFilterType != "none" && faceFilterPath.isNotEmpty()) {
                            applyFaceFilterToPhoto(bitmap) { filteredBitmap ->
                                sessionPhotos.add(filteredBitmap)
                                savePhotoToGallery(filteredBitmap)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    showPreview(filteredBitmap, null)
                                }, 200)
                            }
                        } else {
                            sessionPhotos.add(bitmap)
                            savePhotoToGallery(bitmap)
                            Handler(Looper.getMainLooper()).postDelayed({
                                showPreview(bitmap, null)
                            }, 200)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(baseContext, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    private fun applyFaceFilterToPhoto(bitmap: Bitmap, callback: (Bitmap) -> Unit) {
        Thread {
            try {
                // Detect faces
                val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                val options = com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                    .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .build()
                val detector = com.google.mlkit.vision.face.FaceDetection.getClient(options)
                
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isEmpty()) {
                            runOnUiThread {
                                Toast.makeText(this, "No se detectaron caras, guardando sin filtro", Toast.LENGTH_SHORT).show()
                                callback(bitmap)
                            }
                            detector.close()
                            return@addOnSuccessListener
                        }
                        
                        // Load filter image with transparency support
                        val filterBitmap = try {
                            assets.open(faceFilterPath).use { inputStream ->
                                val options = BitmapFactory.Options().apply {
                                    inPreferredConfig = Bitmap.Config.ARGB_8888
                                }
                                BitmapFactory.decodeStream(inputStream, null, options)
                            }
                        } catch (e: Exception) {
                            null
                        }
                        
                        if (filterBitmap == null) {
                            runOnUiThread {
                                Toast.makeText(this, "Error cargando filtro, guardando sin filtro", Toast.LENGTH_SHORT).show()
                                callback(bitmap)
                            }
                            detector.close()
                            return@addOnSuccessListener
                        }
                        
                        // Apply filter
                        val filteredBitmap = FaceFilterHelper.applyFaceFilter(
                            bitmap,
                            faces,
                            filterBitmap,
                            faceFilterType
                        )
                        
                        filterBitmap.recycle()
                        detector.close()
                        
                        runOnUiThread {
                            Toast.makeText(this, "Filtro aplicado a ${faces.size} cara(s)", Toast.LENGTH_SHORT).show()
                            callback(filteredBitmap)
                        }
                    }
                    .addOnFailureListener { e ->
                        runOnUiThread {
                            Toast.makeText(this, "Error detectando caras: ${e.message}", Toast.LENGTH_SHORT).show()
                            callback(bitmap)
                        }
                        detector.close()
                    }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error aplicando filtro: ${e.message}", Toast.LENGTH_SHORT).show()
                    callback(bitmap)
                }
            }
        }.start()
    }

    private fun takePhotoBoothPhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    var bitmap = imageProxyToBitmap(image)
                    image.close()

                    // Mirror front camera to match what user saw in preview
                    if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                        val matrix = android.graphics.Matrix().apply { preScale(-1f, 1f) }
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
                    }
                    
                    if (filterMode != "normal") {
                        bitmap = applyFilter(bitmap, filterMode)
                    }

                    // For photo booth, we don't apply background removal (too slow for 4 photos)
                    // But we can apply frames
                    if (frameMode != "none" && framePath.isNotEmpty()) {
                        bitmap = applyFrame(bitmap, framePath)
                    }
                    
                    photoBoothBitmaps.add(bitmap)
                    currentPhotoBoothCount++

                    if (currentPhotoBoothCount < 4) {
                        // Wait 3 seconds and take next photo
                        Handler(Looper.getMainLooper()).postDelayed({
                            startCountdown()
                        }, 3000)
                    } else {
                        // Hide counter
                        binding.photoBoothCounter.visibility = View.GONE
                        
                        // Create final photo booth image
                        val finalBitmap = createPhotoBoothGrid(photoBoothBitmaps)
                        sessionPhotos.add(finalBitmap)
                        savePhotoToGallery(finalBitmap)
                        showPreview(finalBitmap, null)
                        
                        // Reset
                        photoBoothBitmaps.clear()
                        currentPhotoBoothCount = 0
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(baseContext, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun startVideoRecording() {
        val videoCapture = videoCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoBooth/$eventName")
            }
        }

        val mediaStoreOutputOptions = androidx.camera.video.MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (ContextCompat.checkSelfPermission(this@CameraActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        binding.recordingIndicator.visibility = View.VISIBLE
                        binding.countdownText.visibility = View.VISIBLE
                        binding.buttonCapture.text = "⏹️" // Stop button icon
                        
                        // Show countdown timer during recording
                        val startTime = System.currentTimeMillis()
                        var elapsedSeconds = 0
                        binding.countdownText.text = "$elapsedSeconds/$videoDuration"
                        
                        recordingTimer = object : CountDownTimer((videoDuration * 1000).toLong(), 1000) {
                            override fun onTick(millisUntilFinished: Long) {
                                elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                                binding.countdownText.text = "$elapsedSeconds/$videoDuration"
                            }
                            
                            override fun onFinish() {
                                stopVideoRecording()
                            }
                        }
                        recordingTimer?.start()
                    }
                    is VideoRecordEvent.Finalize -> {
                        binding.recordingIndicator.visibility = View.GONE
                        binding.countdownText.visibility = View.GONE
                        binding.buttonCapture.text = if (mode == "PHOTO") "📷" else "🎥"
                        recordingTimer?.cancel()
                        recordingTimer = null
                        if (!recordEvent.hasError()) {
                            val uri = recordEvent.outputResults.outputUri
                            
                            // Get actual video resolution
                            try {
                                val retriever = android.media.MediaMetadataRetriever()
                                retriever.setDataSource(this@CameraActivity, uri)
                                val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                val bitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                                retriever.release()
                                
                                android.util.Log.d("CameraActivity", "=== VIDEO GRABADO ===")
                                android.util.Log.d("CameraActivity", "Resolución real: ${width}x${height}")
                                android.util.Log.d("CameraActivity", "Bitrate: $bitrate bps")
                                
                                Toast.makeText(this@CameraActivity, "📹 Video: ${width}x${height}", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                android.util.Log.e("CameraActivity", "Error getting video metadata: ${e.message}")
                            }
                            
                            android.util.Log.d("CameraActivity", "Video recording finalized. URI: $uri")
                            android.util.Log.d("CameraActivity", "slowMotionMode = $slowMotionMode")
                            android.util.Log.d("CameraActivity", "removeBackground = $removeBackground")
                            android.util.Log.d("CameraActivity", "backgroundPath = $backgroundPath")
                            android.util.Log.d("CameraActivity", "filterMode = $filterMode")
                            android.util.Log.d("CameraActivity", "frameMode = $frameMode")
                            
                            // Check if background removal is needed - launch ProcessingActivity
                            if (removeBackground && backgroundPath.isNotEmpty()) {
                                android.util.Log.d("CameraActivity", "Background removal needed - launching ProcessingActivity")
                                launchProcessingActivity(uri)
                            } else if (slowMotionMode == "boomerang_reverse") {
                                android.util.Log.d("CameraActivity", "Boomerang reverse mode - processing video")
                                processBoomerangVideo(uri)
                            } else if (slowMotionMode != "normal") {
                                android.util.Log.d("CameraActivity", "Slow motion mode - passing to preview with speed params")
                                Toast.makeText(this, "Video guardado", Toast.LENGTH_SHORT).show()
                                showPreview(null, uri)
                            } else if (filterMode != "normal" || (frameMode != "none" && framePath.isNotEmpty())) {
                                android.util.Log.d("CameraActivity", "Processing filter/frame")
                                Toast.makeText(this, "Video guardado. Procesando...", Toast.LENGTH_SHORT).show()
                                processVideoWithFilterAndFrame(uri)
                            } else {
                                android.util.Log.d("CameraActivity", "No processing needed - showing preview")
                                Toast.makeText(this, "Video guardado", Toast.LENGTH_SHORT).show()
                                showPreview(null, uri)
                            }
                        } else {
                            android.util.Log.e("CameraActivity", "Video recording error")
                            recording?.close()
                            recording = null
                            Toast.makeText(this, "Error al grabar video", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
    }

    private fun stopVideoRecording() {
        if (cameraSource == "usb") {
            stopUvcVideoRecording()
        } else {
            recording?.stop()
        }
        recordingTimer?.cancel()
    }

    // ============ UVC Camera Capture Methods ============

    private fun takeUvcPhoto() {
        if (uvcCameraHelper?.isCameraOpened != true) {
            Toast.makeText(this, "Cámara USB no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = java.io.File(getExternalFilesDir(null), "UVC_Photos")
        if (!dir.exists()) dir.mkdirs()
        val photoFile = java.io.File(dir, "UVC_${System.currentTimeMillis()}.jpg")

        uvcCameraHelper?.capturePicture(photoFile.absolutePath,
            object : com.serenegiant.usb.common.AbstractUVCCameraHandler.OnCaptureListener {
                override fun onCaptureResult(path: String?) {
                    if (path == null) {
                        runOnUiThread { Toast.makeText(this@CameraActivity, "Error capturando foto USB", Toast.LENGTH_SHORT).show() }
                        return
                    }
                    var bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap == null) {
                        android.util.Log.e("CameraActivity", "UVC photo decode failed for: $path (size=${java.io.File(path).length()})")
                        runOnUiThread { Toast.makeText(this@CameraActivity, "Error: foto capturada vacía", Toast.LENGTH_SHORT).show() }
                        return
                    }

                    // Apply effects
                    if (filterMode != "normal") {
                        bitmap = applyFilter(bitmap, filterMode)
                    }

                    if ((backgroundMode != "none" || backgroundPath.isNotEmpty()) && removeBackground) {
                        processWithBackgroundRemoval(bitmap) { processedBitmap ->
                            var finalBitmap = processedBitmap
                            if (frameMode != "none" && framePath.isNotEmpty()) {
                                finalBitmap = applyFrame(finalBitmap, framePath)
                            }
                            if (faceFilterType != "none" && faceFilterPath.isNotEmpty()) {
                                applyFaceFilterToPhoto(finalBitmap) { filteredBitmap ->
                                    sessionPhotos.add(filteredBitmap)
                                    savePhotoToGallery(filteredBitmap)
                                    Handler(Looper.getMainLooper()).postDelayed({ showPreview(filteredBitmap, null) }, 200)
                                }
                            } else {
                                sessionPhotos.add(finalBitmap)
                                savePhotoToGallery(finalBitmap)
                                Handler(Looper.getMainLooper()).postDelayed({ showPreview(finalBitmap, null) }, 200)
                            }
                        }
                    } else {
                        if (frameMode != "none" && framePath.isNotEmpty()) {
                            bitmap = applyFrame(bitmap, framePath)
                        }
                        if (faceFilterType != "none" && faceFilterPath.isNotEmpty()) {
                            applyFaceFilterToPhoto(bitmap) { filteredBitmap ->
                                sessionPhotos.add(filteredBitmap)
                                savePhotoToGallery(filteredBitmap)
                                Handler(Looper.getMainLooper()).postDelayed({ showPreview(filteredBitmap, null) }, 200)
                            }
                        } else {
                            sessionPhotos.add(bitmap)
                            savePhotoToGallery(bitmap)
                            Handler(Looper.getMainLooper()).postDelayed({ showPreview(bitmap, null) }, 200)
                        }
                    }
                    // Clean up temp file
                    try { java.io.File(path).delete() } catch (_: Exception) {}
                }
            })
    }

    private fun takeUvcPhotoBoothPhoto() {
        if (uvcCameraHelper?.isCameraOpened != true) {
            Toast.makeText(this, "Cámara USB no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = java.io.File(getExternalFilesDir(null), "UVC_Photos")
        if (!dir.exists()) dir.mkdirs()
        val photoFile = java.io.File(dir, "UVC_booth_${System.currentTimeMillis()}.jpg")

        uvcCameraHelper?.capturePicture(photoFile.absolutePath,
            object : com.serenegiant.usb.common.AbstractUVCCameraHandler.OnCaptureListener {
                override fun onCaptureResult(path: String?) {
                    if (path == null) {
                        runOnUiThread { Toast.makeText(this@CameraActivity, "Error capturando foto USB", Toast.LENGTH_SHORT).show() }
                        return
                    }
                    var bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap == null) {
                        android.util.Log.e("CameraActivity", "UVC booth photo decode failed for: $path")
                        runOnUiThread { Toast.makeText(this@CameraActivity, "Error: foto capturada vacía", Toast.LENGTH_SHORT).show() }
                        return
                    }

                    if (filterMode != "normal") {
                        bitmap = applyFilter(bitmap, filterMode)
                    }
                    if (frameMode != "none" && framePath.isNotEmpty()) {
                        bitmap = applyFrame(bitmap, framePath)
                    }

                    photoBoothBitmaps.add(bitmap)
                    currentPhotoBoothCount++

                    runOnUiThread {
                        if (currentPhotoBoothCount < 4) {
                            Handler(Looper.getMainLooper()).postDelayed({ startCountdown() }, 3000)
                        } else {
                            binding.photoBoothCounter.visibility = View.GONE
                            val finalBitmap = createPhotoBoothGrid(photoBoothBitmaps)
                            sessionPhotos.add(finalBitmap)
                            savePhotoToGallery(finalBitmap)
                            showPreview(finalBitmap, null)
                            photoBoothBitmaps.clear()
                            currentPhotoBoothCount = 0
                        }
                    }
                    try { java.io.File(path).delete() } catch (_: Exception) {}
                }
            })
    }

    private fun startUvcVideoRecording() {
        if (uvcCameraHelper?.isCameraOpened != true) {
            Toast.makeText(this, "Cámara USB no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = java.io.File(getExternalFilesDir(null), "UVC_Videos")
        if (!dir.exists()) dir.mkdirs()
        val videoFile = java.io.File(dir, "UVC_${System.currentTimeMillis()}.mp4")

        val params = com.serenegiant.usb.encoder.RecordParams().apply {
            recordPath = videoFile.absolutePath
            recordDuration = 0 // Manual stop
            isVoiceClose = false
        }

        uvcCameraHelper?.startPusher(params, object : com.serenegiant.usb.common.AbstractUVCCameraHandler.OnEncodeResultListener {
            override fun onEncodeResult(data: ByteArray?, offset: Int, length: Int, timestamp: Long, type: Int) {
                // Encoding in progress - nothing needed here
            }

            override fun onRecordResult(videoPath: String?) {
                if (videoPath == null) {
                    runOnUiThread { Toast.makeText(this@CameraActivity, "Error: grabación no produjo archivo", Toast.LENGTH_SHORT).show() }
                    return
                }
                android.util.Log.i("CameraActivity", "UVC recording done: $videoPath (size=${java.io.File(videoPath).length()})")
                // Save UVC video to MediaStore gallery so it can be shared
                saveUvcVideoToGallery(videoPath) { galleryUri ->
                    if (galleryUri == null) {
                        Toast.makeText(this@CameraActivity, "Error: no se pudo guardar el video", Toast.LENGTH_SHORT).show()
                        return@saveUvcVideoToGallery
                    }
                    val uri = galleryUri
                    if (removeBackground && backgroundPath.isNotEmpty()) {
                        launchProcessingActivity(uri)
                    } else if (slowMotionMode == "boomerang_reverse") {
                        processBoomerangVideo(uri)
                    } else if (slowMotionMode != "normal") {
                        Toast.makeText(this@CameraActivity, "Video guardado", Toast.LENGTH_SHORT).show()
                        showPreview(null, uri)
                    } else if (filterMode != "normal" || (frameMode != "none" && framePath.isNotEmpty())) {
                        Toast.makeText(this@CameraActivity, "Video guardado. Procesando...", Toast.LENGTH_SHORT).show()
                        processVideoWithFilterAndFrame(uri)
                    } else {
                        Toast.makeText(this@CameraActivity, "Video guardado", Toast.LENGTH_SHORT).show()
                        showPreview(null, uri)
                    }
                }
            }
        })

        // UVC recording started - show indicator and countdown
        binding.recordingIndicator.visibility = View.VISIBLE
        binding.countdownText.visibility = View.VISIBLE
        binding.buttonCapture.text = "⏹️"
        val startTime = System.currentTimeMillis()
        var elapsedSeconds = 0
        binding.countdownText.text = "$elapsedSeconds/$videoDuration"

        recordingTimer = object : CountDownTimer((videoDuration * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                binding.countdownText.text = "$elapsedSeconds/$videoDuration"
            }
            override fun onFinish() {
                stopUvcVideoRecording()
            }
        }
        recordingTimer?.start()
    }

    private fun stopUvcVideoRecording() {
        uvcCameraHelper?.stopPusher()
        binding.recordingIndicator.visibility = View.GONE
        binding.countdownText.visibility = View.GONE
        binding.buttonCapture.text = "🎥"
        recordingTimer?.cancel()
        recordingTimer = null
    }

    /** Save a UVC video file to the MediaStore gallery (so it's shareable) */
    private fun saveUvcVideoToGallery(videoPath: String, callback: (Uri?) -> Unit) {
        Thread {
            try {
                val srcFile = java.io.File(videoPath)
                if (!srcFile.exists() || srcFile.length() == 0L) {
                    runOnUiThread { callback(null) }
                    return@Thread
                }
                val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoBooth/$eventName")
                    }
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        srcFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    android.util.Log.i("CameraActivity", "UVC video saved to gallery: $uri")
                }
                // Clean up temp file
                try { srcFile.delete() } catch (_: Exception) {}
                runOnUiThread { callback(uri) }
            } catch (e: Exception) {
                android.util.Log.e("CameraActivity", "Error saving UVC video to gallery", e)
                runOnUiThread { callback(null) }
            }
        }.start()
    }

    /** Force audio output to phone speaker when USB device hijacks audio routing */
    private fun forceAudioToSpeaker() {
        try {
            val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            // Set per-track routing for our beeps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val speaker = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                    .find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ToneGenerator.preferredOutputDevice = speaker
                // Also set global communication routing to speaker
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    speaker?.let { audioManager.setCommunicationDevice(it) }
                }
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            android.util.Log.i("CameraActivity", "Audio forced to phone speaker")
        } catch (e: Exception) {
            android.util.Log.w("CameraActivity", "Could not force audio to speaker", e)
        }
    }

    /** Restore default audio routing */
    private fun restoreAudioRouting() {
        try {
            val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            ToneGenerator.preferredOutputDevice = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } catch (_: Exception) {}
    }

    private fun imageProxyToBitmap(image: androidx.camera.core.ImageProxy): Bitmap {
        // Check image format
        return when (image.format) {
            android.graphics.ImageFormat.YUV_420_888 -> {
                // YUV format - use converter
                val yuvToRgbConverter = YuvToRgbConverter(this)
                val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                yuvToRgbConverter.yuvToRgb(image.image!!, bitmap)
                bitmap
            }
            android.graphics.ImageFormat.JPEG -> {
                // JPEG format - decode directly
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            else -> {
                throw IllegalArgumentException("Unsupported image format: ${image.format}")
            }
        }
    }

    private fun applyBlackAndWhiteFilter(bitmap: Bitmap): Bitmap {
        return applyFilter(bitmap, "bw")
    }
    
    private fun applyFilter(bitmap: Bitmap, filter: String): Bitmap {
        val filteredBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(filteredBitmap)
        val paint = Paint()
        
        when (filter) {
            "bw" -> {
                // Black and white
                val colorMatrix = ColorMatrix()
                colorMatrix.setSaturation(0f)
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
            "sepia" -> {
                // Sepia tone
                val matrix = ColorMatrix()
                matrix.setSaturation(0f)
                val sepiaMatrix = ColorMatrix()
                sepiaMatrix.set(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(sepiaMatrix)
                paint.colorFilter = ColorMatrixColorFilter(matrix)
            }
            "vintage" -> {
                // Vintage effect (low saturation + warm tones)
                val matrix = ColorMatrix()
                matrix.setSaturation(0.5f)
                val warmMatrix = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 15f,
                    0f, 1.0f, 0f, 0f, 10f,
                    0f, 0f, 0.8f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(warmMatrix)
                paint.colorFilter = ColorMatrixColorFilter(matrix)
            }
            "contrast" -> {
                // High contrast
                val matrix = ColorMatrix(floatArrayOf(
                    2f, 0f, 0f, 0f, -128f,
                    0f, 2f, 0f, 0f, -128f,
                    0f, 0f, 2f, 0f, -128f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(matrix)
            }
            "invert" -> {
                // Invert colors
                val matrix = ColorMatrix(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(matrix)
            }
            "cold" -> {
                // Cold/blue tone
                val matrix = ColorMatrix(floatArrayOf(
                    0.8f, 0f, 0f, 0f, 0f,
                    0f, 0.9f, 0f, 0f, 0f,
                    0f, 0f, 1.2f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(matrix)
            }
            else -> {
                // Normal - no filter
            }
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return filteredBitmap
    }

    private fun createPhotoBoothGrid(bitmaps: List<Bitmap>): Bitmap {
        if (bitmaps.size != 4) return bitmaps.firstOrNull() ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        
        val width = bitmaps[0].width
        val height = bitmaps[0].height
        val finalBitmap = Bitmap.createBitmap(width, height * 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        
        bitmaps.forEachIndexed { index, bitmap ->
            canvas.drawBitmap(bitmap, 0f, (height * index).toFloat(), null)
        }
        
        return finalBitmap
    }

    private fun savePhotoToGallery(bitmap: Bitmap) {
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoBooth/$eventName")
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
        }
        
        // Return the URI so we can use it for sharing
        lastSavedPhotoUri = uri
    }

    private fun showPreview(bitmap: Bitmap?, videoUri: Uri?) {
        android.util.Log.d("CameraActivity", "showPreview called - bitmap: ${bitmap != null}, videoUri: $videoUri")
        
        val intent = Intent(this, PreviewActivity::class.java)
        
        if (bitmap != null) {
            // Use the saved photo URI instead of temp file
            if (lastSavedPhotoUri != null) {
                intent.putExtra("IMAGE_URI", lastSavedPhotoUri.toString())
                intent.putExtra("TYPE", "PHOTO")
            } else {
                // Fallback to temp file if URI not available
                try {
                    val tempFile = File(cacheDir, "preview_${System.currentTimeMillis()}.jpg")
                    tempFile.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                    intent.putExtra("IMAGE_PATH", tempFile.absolutePath)
                    intent.putExtra("TYPE", "PHOTO")
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error al preparar preview: ${e.message}", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        } else if (videoUri != null) {
            intent.putExtra("VIDEO_URI", videoUri.toString())
            intent.putExtra("TYPE", "VIDEO")
            intent.putExtra("SLOW_MOTION_MODE", slowMotionMode)
            if (slowMotionMode == "0.5x") {
                intent.putExtra("PLAYBACK_SPEED", 0.5f)
            } else if (slowMotionMode == "boomerang" || slowMotionMode == "boomerang_reverse") {
                intent.putExtra("BOOMERANG_MIN_SPEED", boomerangMinSpeed)
                intent.putExtra("BOOMERANG_MAX_SPEED", boomerangMaxSpeed)
                intent.putExtra("BOOMERANG_SLOW_DURATION", boomerangFrequency)
                intent.putExtra("BOOMERANG_FAST_DURATION", boomerangFastDuration)
            }
        }
        
        intent.putExtra("EVENT_NAME", eventName)
        android.util.Log.d("CameraActivity", "Starting PreviewActivity")
        startActivity(intent)
    }

    private fun processSlowMotionVideo(inputUri: Uri) {
        android.util.Log.d("SlowMotion", "processSlowMotionVideo START")
        Thread {
            try {
                runOnUiThread {
                    Toast.makeText(this, "Procesando video en cámara lenta...", Toast.LENGTH_SHORT).show()
                }
                
                // Copy input video to cache (works with Scoped Storage)
                val inputFile = File(cacheDir, "input_slow_${System.currentTimeMillis()}.mp4")
                contentResolver.openInputStream(inputUri)?.use { input ->
                    inputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                val inputPath = inputFile.absolutePath
                android.util.Log.d("SlowMotion", "Copied to: $inputPath, size: ${inputFile.length()}")
                
                if (!inputFile.exists() || inputFile.length() == 0L) {
                    runOnUiThread {
                        Toast.makeText(this, "Error: No se pudo copiar el video", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                    return@Thread
                }
                
                val outputFile = File(cacheDir, "slow_motion_${System.currentTimeMillis()}.mp4")
                val outputPath = outputFile.absolutePath
                
                // Use -itsscale to change speed WITHOUT re-encoding (avoids codec issues)
                // -itsscale multiplies input timestamps: 2.0 = video plays 2x slower (0.5x speed)
                // -c copy = no re-encoding needed, just changes timestamps
                // -an = drop audio (can't slow audio without re-encoding)
                val command = when (slowMotionMode) {
                    "0.5x" -> {
                        // 0.5x speed = 2x slower
                        "-y -itsscale 2.0 -i \"$inputPath\" -c copy -an \"$outputPath\""
                    }
                    "boomerang" -> {
                        // For boomerang we need frame-level manipulation
                        // Use a moderate slow-down as approximation
                        val avgFactor = 1.0f / ((boomerangMinSpeed + boomerangMaxSpeed) / 2.0f)
                        "-y -itsscale $avgFactor -i \"$inputPath\" -c copy -an \"$outputPath\""
                    }
                    else -> {
                        "-y -itsscale 2.0 -i \"$inputPath\" -c copy -an \"$outputPath\""
                    }
                }
                
                android.util.Log.d("SlowMotion", "Mode: $slowMotionMode")
                android.util.Log.d("SlowMotion", "Command: $command")
                
                val session = FFmpegKit.execute(command)
                
                android.util.Log.d("SlowMotion", "Return code: ${session.returnCode}")
                android.util.Log.d("SlowMotion", "Output: ${session.output}")
                
                if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0) {
                    // Success - save to gallery
                    val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + "_slow"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoBooth/$eventName")
                        }
                    }
                    
                    val outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    outputUri?.let { uri ->
                        contentResolver.openOutputStream(uri)?.use { output ->
                            outputFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        
                        // Clean up temp files
                        outputFile.delete()
                        inputFile.delete()
                        
                        runOnUiThread {
                            Toast.makeText(this, "Video en cámara lenta listo", Toast.LENGTH_SHORT).show()
                            showPreview(null, uri)
                        }
                    } ?: run {
                        outputFile.delete()
                        inputFile.delete()
                        runOnUiThread {
                            Toast.makeText(this, "Error al guardar video", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Error - show original video
                    android.util.Log.e("SlowMotion", "FFmpeg failed or output file empty")
                    outputFile.delete()
                    inputFile.delete()
                    runOnUiThread {
                        Toast.makeText(this, "Error procesando video, mostrando original", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    showPreview(null, inputUri)
                }
            }
        }.start()
    }

    private fun processBoomerangVideo(inputUri: Uri) {
        android.util.Log.d("Boomerang", "processBoomerangVideo START - MediaExtractor+MediaCodec")
        Thread {
            var decoder: android.media.MediaCodec? = null
            var encoder: android.media.MediaCodec? = null
            var muxer: android.media.MediaMuxer? = null
            var extractor: android.media.MediaExtractor? = null

            try {
                runOnUiThread {
                    binding.progressOverlay.visibility = View.VISIBLE
                    binding.progressLabel.text = "Decodificando video..."
                    binding.progressBar.progress = 0
                    binding.progressPercent.text = "0%"
                }

                // Copy URI to a temp file for MediaExtractor
                val inputFile = File(cacheDir, "input_boom_${System.currentTimeMillis()}.mp4")
                contentResolver.openInputStream(inputUri)?.use { inp ->
                    inputFile.outputStream().use { out -> inp.copyTo(out) }
                }
                val inputPath = inputFile.absolutePath

                // Step 1: Find video track
                extractor = android.media.MediaExtractor()
                extractor.setDataSource(inputPath)

                var videoTrackIndex = -1
                var inputFormat: android.media.MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        videoTrackIndex = i
                        inputFormat = fmt
                        break
                    }
                }

                if (videoTrackIndex < 0 || inputFormat == null) {
                    inputFile.delete()
                    runOnUiThread {
                        binding.progressOverlay.visibility = View.GONE
                        Toast.makeText(this, "Error: no se encontró pista de video", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                    return@Thread
                }

                extractor.selectTrack(videoTrackIndex)

                val srcWidth = inputFormat.getInteger(android.media.MediaFormat.KEY_WIDTH)
                val srcHeight = inputFormat.getInteger(android.media.MediaFormat.KEY_HEIGHT)
                val rotation = try { inputFormat.getInteger("rotation-degrees") } catch (_: Exception) {
                    try {
                        val ret = android.media.MediaMetadataRetriever()
                        ret.setDataSource(inputPath)
                        val r = ret.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                        ret.release()
                        r
                    } catch (_: Exception) { 0 }
                }
                val origBitrate = try { inputFormat.getInteger(android.media.MediaFormat.KEY_BIT_RATE) } catch (_: Exception) { 10_000_000 }
                val inputMime = inputFormat.getString(android.media.MediaFormat.KEY_MIME)!!

                android.util.Log.d("Boomerang", "Source: ${srcWidth}x${srcHeight}, rotation=$rotation, mime=$inputMime")

                // Target 480p for speed (scale down preserving aspect ratio)
                val maxDim = 480
                val scale: Float
                val isRotated = rotation == 90 || rotation == 270
                // srcWidth/srcHeight are raw stream dimensions (before rotation)
                val displayW = if (isRotated) srcHeight else srcWidth
                val displayH = if (isRotated) srcWidth else srcHeight

                scale = if (displayW > displayH) {
                    (maxDim.toFloat() / displayH).coerceAtMost(1f)
                } else {
                    (maxDim.toFloat() / displayW).coerceAtMost(1f)
                }

                val encWidth = ((displayW * scale).toInt() / 2) * 2
                val encHeight = ((displayH * scale).toInt() / 2) * 2
                val frameYuvSize = encWidth * encHeight * 3 / 2

                android.util.Log.d("Boomerang", "Encoder: ${encWidth}x${encHeight}, scale=$scale")

                // Step 2: Decode all frames using MediaCodec decoder (sequential, fast)
                decoder = android.media.MediaCodec.createDecoderByType(inputMime)
                // Configure for ByteBuffer output (no Surface)
                val decoderFormat = android.media.MediaFormat.createVideoFormat(inputMime, srcWidth, srcHeight)
                // Copy CSD buffers if present
                for (csdKey in listOf("csd-0", "csd-1", "csd-2")) {
                    if (inputFormat.containsKey(csdKey)) {
                        decoderFormat.setByteBuffer(csdKey, inputFormat.getByteBuffer(csdKey))
                    }
                }
                decoder.configure(inputFormat, null, null, 0)
                decoder.start()

                val decoderInfo = android.media.MediaCodec.BufferInfo()
                var extractorDone = false
                var decoderDone = false
                val yuvFrames = mutableListOf<ByteArray>()

                while (!decoderDone) {
                    // Feed extractor data to decoder
                    if (!extractorDone) {
                        val inIdx = decoder.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = decoder.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                extractorDone = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    // Get decoded frames
                    val outIdx = decoder.dequeueOutputBuffer(decoderInfo, 10_000)
                    if (outIdx >= 0) {
                        if (decoderInfo.size > 0) {
                            // Use getOutputImage() for correct color planes (handles NV12/NV21/YV12 transparently)
                            val image = decoder.getOutputImage(outIdx)
                            if (image != null) {
                                var bmp = imageToBitmap(image, isRotated)
                                image.close()
                                if (bmp != null) {
                                    val scaled = if (bmp.width != encWidth || bmp.height != encHeight) {
                                        Bitmap.createScaledBitmap(bmp, encWidth, encHeight, true)
                                    } else bmp

                                    val yuv = bitmapToNV12(scaled, encWidth, encHeight)
                                    yuvFrames.add(yuv)

                                    if (scaled !== bmp) scaled.recycle()
                                    bmp.recycle()
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)

                        if (decoderInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            decoderDone = true
                        }

                        if (yuvFrames.size % 10 == 0) {
                            runOnUiThread {
                                binding.progressLabel.text = "Decodificando..."
                                binding.progressBar.progress = (yuvFrames.size % 200) / 2
                                binding.progressPercent.text = "${yuvFrames.size} frames"
                            }
                        }
                    }
                }

                decoder.stop()
                decoder.release()
                decoder = null
                extractor.release()
                extractor = null
                inputFile.delete()

                android.util.Log.d("Boomerang", "Decoded ${yuvFrames.size} frames to NV12 at ${encWidth}x${encHeight}")

                if (yuvFrames.isEmpty()) {
                    runOnUiThread {
                        binding.progressOverlay.visibility = View.GONE
                        Toast.makeText(this, "Error: no se decodificaron frames", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                    return@Thread
                }

                runOnUiThread {
                    binding.progressLabel.text = "Codificando boomerang..."
                    binding.progressBar.progress = 0
                    binding.progressPercent.text = "0%"
                }

                // Step 3: Build forward + reverse sequence
                val allFrames = yuvFrames + yuvFrames.asReversed()
                val fps = 30

                // Step 4: Encode with MediaCodec H.264
                val outputFile = File(cacheDir, "boomerang_${System.currentTimeMillis()}.mp4")
                val encMime = android.media.MediaFormat.MIMETYPE_VIDEO_AVC
                val encFormat = android.media.MediaFormat.createVideoFormat(encMime, encWidth, encHeight)
                encFormat.setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT,
                    android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                encFormat.setInteger(android.media.MediaFormat.KEY_BIT_RATE, origBitrate.coerceIn(3_000_000, 15_000_000))
                encFormat.setInteger(android.media.MediaFormat.KEY_FRAME_RATE, fps)
                encFormat.setInteger(android.media.MediaFormat.KEY_I_FRAME_INTERVAL, 1)

                encoder = android.media.MediaCodec.createEncoderByType(encMime)
                encoder.configure(encFormat, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder.start()

                muxer = android.media.MediaMuxer(outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                var trackIndex = -1
                var muxerStarted = false
                val bufferInfo = android.media.MediaCodec.BufferInfo()
                val frameDurationUs = 1_000_000L / fps

                for ((idx, yuvData) in allFrames.withIndex()) {
                    // Feed encoder
                    val inIdx = encoder.dequeueInputBuffer(10_000_000L)
                    if (inIdx >= 0) {
                        val inBuf = encoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        inBuf.put(yuvData)
                        encoder.queueInputBuffer(inIdx, 0, yuvData.size, idx.toLong() * frameDurationUs, 0)
                    }

                    // Drain encoder
                    while (true) {
                        val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 0)
                        when {
                            outIdx == android.media.MediaCodec.INFO_TRY_AGAIN_LATER -> break
                            outIdx == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                trackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                            outIdx >= 0 -> {
                                val outBuf = encoder.getOutputBuffer(outIdx)!!
                                if (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                    bufferInfo.size = 0
                                }
                                if (bufferInfo.size > 0 && muxerStarted) {
                                    outBuf.position(bufferInfo.offset)
                                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                                }
                                encoder.releaseOutputBuffer(outIdx, false)
                            }
                        }
                    }

                    if (idx % 10 == 0) {
                        val pct = ((idx.toFloat() / allFrames.size) * 100).toInt()
                        runOnUiThread {
                            binding.progressBar.progress = pct
                            binding.progressPercent.text = "$pct%"
                        }
                    }
                }

                // Signal EOS
                val eosIdx = encoder.dequeueInputBuffer(10_000_000L)
                if (eosIdx >= 0) {
                    encoder.queueInputBuffer(eosIdx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                var drainRetries = 0
                while (drainRetries < 100) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx == android.media.MediaCodec.INFO_TRY_AGAIN_LATER -> drainRetries++
                        outIdx == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        outIdx >= 0 -> {
                            drainRetries = 0
                            val outBuf = encoder.getOutputBuffer(outIdx)!!
                            if (bufferInfo.size > 0 && muxerStarted) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outIdx, false)
                            if (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }

                encoder.stop(); encoder.release(); encoder = null
                if (muxerStarted) muxer.stop()
                muxer.release(); muxer = null

                android.util.Log.d("Boomerang", "Output: ${outputFile.absolutePath}, size: ${outputFile.length()}")

                if (outputFile.exists() && outputFile.length() > 0) {
                    val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + "_boom"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoBooth/$eventName")
                        }
                    }
                    val outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    outputUri?.let { uri ->
                        contentResolver.openOutputStream(uri)?.use { out ->
                            outputFile.inputStream().use { inp -> inp.copyTo(out) }
                        }
                        outputFile.delete()
                        runOnUiThread {
                            binding.progressOverlay.visibility = View.GONE
                            showPreview(null, uri)
                        }
                    } ?: run {
                        outputFile.delete()
                        runOnUiThread {
                            binding.progressOverlay.visibility = View.GONE
                            Toast.makeText(this, "Error al guardar boomerang", Toast.LENGTH_SHORT).show()
                            showPreview(null, inputUri)
                        }
                    }
                } else {
                    runOnUiThread {
                        binding.progressOverlay.visibility = View.GONE
                        Toast.makeText(this, "Error creando boomerang", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("Boomerang", "Error: ${e.message}", e)
                try { decoder?.stop() } catch (_: Exception) {}
                try { decoder?.release() } catch (_: Exception) {}
                try { encoder?.stop() } catch (_: Exception) {}
                try { encoder?.release() } catch (_: Exception) {}
                try { muxer?.stop() } catch (_: Exception) {}
                try { muxer?.release() } catch (_: Exception) {}
                try { extractor?.release() } catch (_: Exception) {}

                runOnUiThread {
                    binding.progressOverlay.visibility = View.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    showPreview(null, inputUri)
                }
            }
        }.start()
    }

    /**
     * Convert decoder YUV output to Bitmap, handling rotation
     */
    /**
     * Convert a MediaCodec output Image to a Bitmap using proper plane access.
     * This correctly handles NV12, NV21, YV12, I420 and all flexible formats.
     */
    private fun imageToBitmap(image: android.media.Image, applyRotation: Boolean): Bitmap? {
        return try {
            val width = image.width
            val height = image.height
            val planes = image.planes

            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yBuf = yPlane.buffer
            val uBuf = uPlane.buffer
            val vBuf = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            // Build NV21 (Y + interleaved VU) for YuvImage
            val nv21 = ByteArray(width * height * 3 / 2)

            // Copy Y plane row by row (skip padding)
            for (row in 0 until height) {
                yBuf.position(row * yRowStride)
                val copyLen = width.coerceAtMost(yBuf.remaining())
                yBuf.get(nv21, row * width, copyLen)
            }

            // Interleave V, U into NV21 UV plane
            val uvOffset = width * height
            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    val uvIdx = row * uvRowStride + col * uvPixelStride
                    val nv21Idx = uvOffset + row * width + col * 2
                    if (uvIdx < vBuf.capacity() && uvIdx < uBuf.capacity() && nv21Idx + 1 < nv21.size) {
                        nv21[nv21Idx]     = vBuf.get(uvIdx)
                        nv21[nv21Idx + 1] = uBuf.get(uvIdx)
                    }
                }
            }

            val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
            val baos = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, baos)
            val jpegBytes = baos.toByteArray()
            var bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null

            if (applyRotation) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(90f)
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                bmp.recycle()
                bmp = rotated
            }

            bmp
        } catch (e: Exception) {
            android.util.Log.e("Boomerang", "imageToBitmap error: ${e.message}")
            null
        }
    }

    private fun bitmapToNV12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(16, 235).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(16, 240).toByte()
                    yuv[uvIndex++] = v.coerceIn(16, 240).toByte()
                }
            }
        }

        return yuv
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.Video.Media.DATA)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                cursor.moveToFirst()
                cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Launch ProcessingActivity to handle video background removal with progress UI
     */
    private fun launchProcessingActivity(inputUri: Uri) {
        android.util.Log.d("CameraActivity", "=== Launching ProcessingActivity ===")
        android.util.Log.d("CameraActivity", "Input URI: $inputUri")
        
        // Copy video to cache to work around Scoped Storage restrictions
        val inputFile = File(cacheDir, "input_processing_${System.currentTimeMillis()}.mp4")
        try {
            contentResolver.openInputStream(inputUri)?.use { input ->
                inputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CameraActivity", "ERROR copying video: ${e.message}")
            Toast.makeText(this, "Error: No se pudo acceder al video", Toast.LENGTH_SHORT).show()
            showPreview(null, inputUri)
            return
        }
        
        val inputPath = inputFile.absolutePath
        android.util.Log.d("CameraActivity", "Copied to: $inputPath, size: ${inputFile.length()}")
        
        if (!inputFile.exists() || inputFile.length() == 0L) {
            android.util.Log.e("CameraActivity", "ERROR: Copied file is empty or doesn't exist")
            Toast.makeText(this, "Error: No se pudo acceder al video", Toast.LENGTH_SHORT).show()
            showPreview(null, inputUri)
            return
        }
        
        android.util.Log.d("CameraActivity", "Background path: $backgroundPath")
        android.util.Log.d("CameraActivity", "Frame path: $framePath")
        android.util.Log.d("CameraActivity", "Filter mode: $filterMode")
        android.util.Log.d("CameraActivity", "Slow motion: $slowMotionMode")
        
        val intent = Intent(this, ProcessingActivity::class.java).apply {
            putExtra("INPUT_PATH", inputPath)
            putExtra("BACKGROUND_PATH", backgroundPath)
            putExtra("FRAME_PATH", if (frameMode != "none") framePath else "")
            putExtra("FILTER_MODE", filterMode)
            putExtra("SLOW_MOTION_MODE", slowMotionMode)
            putExtra("EVENT_NAME", eventName)
        }
        
        android.util.Log.d("CameraActivity", "Starting ProcessingActivity...")
        processingLauncher.launch(intent)
    }
    
    /**
     * Process video with ML Kit background removal - frame by frame
     * This is a heavy operation that can take several minutes
     * @deprecated Use launchProcessingActivity instead for better UX
     */
    private fun processVideoWithBackgroundRemoval(inputUri: Uri) {
        Thread {
            try {
                runOnUiThread {
                    Toast.makeText(this, "Extrayendo frames del video...", Toast.LENGTH_SHORT).show()
                }
                
                val inputPath = getRealPathFromURI(inputUri)
                if (inputPath == null) {
                    runOnUiThread {
                        Toast.makeText(this, "Error: No se pudo acceder al video", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                    return@Thread
                }
                
                // Create temp directories
                val framesDir = File(cacheDir, "frames_${System.currentTimeMillis()}")
                val processedDir = File(cacheDir, "processed_frames_${System.currentTimeMillis()}")
                framesDir.mkdirs()
                processedDir.mkdirs()
                
                // Get video info (FPS)
                val mediaInfoSession = FFprobeKit.getMediaInformation(inputPath)
                val mediaInfo = mediaInfoSession.mediaInformation
                var fps = 30.0
                mediaInfo?.streams?.firstOrNull { it.type == "video" }?.let { stream ->
                    val frameRateStr = stream.averageFrameRate ?: "30"
                    if (frameRateStr.contains("/")) {
                        val parts = frameRateStr.split("/")
                        fps = parts[0].toDouble() / parts[1].toDouble()
                    } else {
                        fps = frameRateStr.toDoubleOrNull() ?: 30.0
                    }
                }
                
                // Extract frames from video
                val extractCommand = "-i \"$inputPath\" -vf fps=$fps \"${framesDir.absolutePath}/frame_%05d.png\""
                android.util.Log.d("VideoBackground", "Extracting frames: $extractCommand")
                val extractSession = FFmpegKit.execute(extractCommand)
                
                if (!ReturnCode.isSuccess(extractSession.returnCode)) {
                    runOnUiThread {
                        Toast.makeText(this, "Error extrayendo frames", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                    framesDir.deleteRecursively()
                    processedDir.deleteRecursively()
                    return@Thread
                }
                
                // Get frame files
                val frameFiles = framesDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                val totalFrames = frameFiles.size
                
                if (totalFrames == 0) {
                    runOnUiThread {
                        Toast.makeText(this, "No se pudieron extraer frames", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                    framesDir.deleteRecursively()
                    processedDir.deleteRecursively()
                    return@Thread
                }
                
                runOnUiThread {
                    Toast.makeText(this, "Procesando $totalFrames frames con ML Kit...", Toast.LENGTH_LONG).show()
                }
                
                // Load background image
                val bgBitmap = try {
                    val inputStream = assets.open(backgroundPath)
                    BitmapFactory.decodeStream(inputStream).also { inputStream.close() }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Error cargando fondo", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                    framesDir.deleteRecursively()
                    processedDir.deleteRecursively()
                    return@Thread
                }
                
                // Process each frame with ML Kit
                val segmenter = Segmentation.getClient(
                    SelfieSegmenterOptions.Builder()
                        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                        .enableRawSizeMask()
                        .build()
                )
                
                var processedCount = 0
                val latch = java.util.concurrent.CountDownLatch(totalFrames)
                
                frameFiles.forEachIndexed { index, frameFile ->
                    val frameBitmap = BitmapFactory.decodeFile(frameFile.absolutePath)
                    if (frameBitmap == null) {
                        latch.countDown()
                        return@forEachIndexed
                    }
                    
                    val inputImage = InputImage.fromBitmap(frameBitmap, 0)
                    
                    segmenter.process(inputImage)
                        .addOnSuccessListener { mask ->
                            val processed = applyBackgroundToFrame(frameBitmap, mask, bgBitmap)
                            
                            // Save processed frame
                            val outputFile = File(processedDir, "frame_%05d.png".format(index + 1))
                            FileOutputStream(outputFile).use { out ->
                                processed.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            
                            processed.recycle()
                            frameBitmap.recycle()
                            
                            processedCount++
                            if (processedCount % 10 == 0) {
                                runOnUiThread {
                                    Toast.makeText(this, "Procesados $processedCount/$totalFrames frames", Toast.LENGTH_SHORT).show()
                                }
                            }
                            
                            latch.countDown()
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("VideoBackground", "Frame processing failed: ${e.message}")
                            // Copy original frame on failure
                            frameFile.copyTo(File(processedDir, frameFile.name), overwrite = true)
                            frameBitmap.recycle()
                            latch.countDown()
                        }
                }
                
                // Wait for all frames to be processed (with timeout)
                latch.await(10, java.util.concurrent.TimeUnit.MINUTES)
                
                bgBitmap.recycle()
                segmenter.close()
                
                runOnUiThread {
                    Toast.makeText(this, "Recomponiendo video...", Toast.LENGTH_SHORT).show()
                }
                
                // Recompose video from processed frames
                val outputFile = File(cacheDir, "bg_processed_${System.currentTimeMillis()}.mp4")
                
                // Build FFmpeg command to create video from frames
                var composeCommand = "-framerate $fps -i \"${processedDir.absolutePath}/frame_%05d.png\" "
                
                // Add audio from original video
                composeCommand += "-i \"$inputPath\" -map 0:v -map 1:a? "
                
                // Add filter if needed
                if (filterMode != "normal") {
                    val videoFilter = when (filterMode) {
                        "bw" -> "hue=s=0"
                        "sepia" -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"
                        "vintage" -> "hue=s=0.5,curves=vintage"
                        "contrast" -> "eq=contrast=2:brightness=-0.1"
                        "invert" -> "negate"
                        "cold" -> "colorbalance=bs=-0.2:ms=-0.1:hs=0.1"
                        else -> "null"
                    }
                    composeCommand += "-vf \"$videoFilter\" "
                }
                
                composeCommand += "-c:v libx264 -crf 18 -preset fast -pix_fmt yuv420p -c:a copy \"${outputFile.absolutePath}\""
                
                android.util.Log.d("VideoBackground", "Composing video: $composeCommand")
                val composeSession = FFmpegKit.execute(composeCommand)
                
                // Clean up frame directories
                framesDir.deleteRecursively()
                processedDir.deleteRecursively()
                
                if (ReturnCode.isSuccess(composeSession.returnCode)) {
                    // Apply frame overlay if needed, or save directly
                    if (frameMode != "none" && framePath.isNotEmpty()) {
                        applyFrameToProcessedVideo(outputFile, inputUri)
                    } else if (slowMotionMode != "normal") {
                        // Apply slow motion to processed video
                        val processedUri = Uri.fromFile(outputFile)
                        processSlowMotionVideo(processedUri)
                    } else {
                        // Save final video
                        saveProcessedVideo(outputFile)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Error recomponiendo video", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("VideoBackground", "Error: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this, "Error procesando video: ${e.message}", Toast.LENGTH_SHORT).show()
                    showPreview(null, inputUri)
                }
            }
        }.start()
    }
    
    /**
     * Apply background to a single frame using ML Kit mask
     */
    private fun applyBackgroundToFrame(original: Bitmap, mask: SegmentationMask, bgBitmap: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        
        // Scale background to match frame size
        val scaledBg = Bitmap.createScaledBitmap(bgBitmap, width, height, true)
        
        // Create result bitmap
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Get mask dimensions (may differ from image if enableRawSizeMask is used)
        val maskWidth = mask.width
        val maskHeight = mask.height
        
        val maskBuffer = mask.buffer
        maskBuffer.rewind()
        
        // Create float array for mask values
        val maskArray = FloatArray(maskWidth * maskHeight)
        maskBuffer.asFloatBuffer().get(maskArray)
        
        // Copy pixels
        val originalPixels = IntArray(width * height)
        val bgPixels = IntArray(width * height)
        original.getPixels(originalPixels, 0, width, 0, 0, width, height)
        scaledBg.getPixels(bgPixels, 0, width, 0, 0, width, height)
        
        val resultPixels = IntArray(width * height)
        
        // Scale factors for mask to image coordinates
        val scaleX = maskWidth.toFloat() / width
        val scaleY = maskHeight.toFloat() / height
        
        // Blend based on mask with improved thresholds
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                
                // Map image coordinates to mask coordinates
                val maskX = (x * scaleX).toInt().coerceIn(0, maskWidth - 1)
                val maskY = (y * scaleY).toInt().coerceIn(0, maskHeight - 1)
                val maskIndex = maskY * maskWidth + maskX
                
                val confidence = maskArray[maskIndex]
                
                // More aggressive threshold for better background removal
                if (confidence > 0.7f) {
                    resultPixels[i] = originalPixels[i]
                } else if (confidence > 0.3f) {
                    val alpha = (confidence - 0.3f) / 0.4f
                    resultPixels[i] = blendPixels(originalPixels[i], bgPixels[i], alpha)
                } else {
                    resultPixels[i] = bgPixels[i]
                }
            }
        }
        
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        scaledBg.recycle()
        
        return result
    }
    
    /**
     * Apply frame overlay to processed video and save
     */
    private fun applyFrameToProcessedVideo(inputFile: File, originalUri: Uri) {
        try {
            val frameFile = File(cacheDir, "frame_overlay_${System.currentTimeMillis()}.png")
            assets.open(framePath).use { input ->
                frameFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            val outputFile = File(cacheDir, "final_${System.currentTimeMillis()}.mp4")
            
            val command = "-i \"${inputFile.absolutePath}\" -i \"${frameFile.absolutePath}\" -filter_complex \"[1:v]scale2ref[frame][video];[video][frame]overlay=0:0\" -c:a copy -preset ultrafast -b:v 8M \"${outputFile.absolutePath}\""
            
            val session = FFmpegKit.execute(command)
            
            frameFile.delete()
            inputFile.delete()
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                if (slowMotionMode != "normal") {
                    processSlowMotionVideo(Uri.fromFile(outputFile))
                } else {
                    saveProcessedVideo(outputFile)
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Error aplicando marco", Toast.LENGTH_SHORT).show()
                    showPreview(null, originalUri)
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Save processed video to gallery
     */
    private fun saveProcessedVideo(file: File) {
        try {
            val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + "_bg_processed"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoBooth/$eventName")
                }
            }
            
            val outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            outputUri?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                
                file.delete()
                
                runOnUiThread {
                    Toast.makeText(this, "Video procesado correctamente", Toast.LENGTH_SHORT).show()
                    showPreview(null, uri)
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error guardando video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun processVideoWithFilter(inputUri: Uri) {
        processVideoWithFilterAndFrame(inputUri)
    }
    
    private fun processVideoWithFilterAndFrame(inputUri: Uri) {
        Thread {
            var inputFile: File? = null
            try {
                runOnUiThread {
                    val message = if (filterMode != "normal" && frameMode != "none" && framePath.isNotEmpty()) {
                        "Aplicando filtro y marco al video..."
                    } else if (frameMode != "none" && framePath.isNotEmpty()) {
                        "Aplicando marco al video..."
                    } else {
                        "Aplicando filtro al video..."
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
                
                // Copy input video to cache (works with Scoped Storage)
                inputFile = File(cacheDir, "input_filter_${System.currentTimeMillis()}.mp4")
                contentResolver.openInputStream(inputUri)?.use { input ->
                    inputFile!!.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                val inputPath = inputFile!!.absolutePath
                android.util.Log.d("VideoProcessing", "Copied to: $inputPath, size: ${inputFile!!.length()}")
                
                if (!inputFile!!.exists() || inputFile!!.length() == 0L) {
                    runOnUiThread {
                        Toast.makeText(this, "Error: No se pudo copiar el video", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                    return@Thread
                }
                
                val outputFile = File(cacheDir, "processed_${System.currentTimeMillis()}.mp4")
                val outputPath = outputFile.absolutePath
                
                var frameFile: File? = null
                
                // Copy frame from assets to cache if needed
                if (frameMode != "none" && framePath.isNotEmpty()) {
                    frameFile = File(cacheDir, "frame_${System.currentTimeMillis()}.png")
                    assets.open(framePath).use { input ->
                        frameFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                // Build FFmpeg command
                val command = if (frameFile != null && filterMode != "normal") {
                    // Both filter and frame
                    val videoFilter = when (filterMode) {
                        "bw" -> "hue=s=0"
                        "sepia" -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"
                        "vintage" -> "hue=s=0.5,curves=vintage"
                        "contrast" -> "eq=contrast=2:brightness=-0.1"
                        "invert" -> "negate"
                        "cold" -> "colorbalance=bs=-0.2:ms=-0.1:hs=0.1"
                        else -> "null"
                    }
                    "-y -i \"$inputPath\" -i \"${frameFile.absolutePath}\" -filter_complex \"[0:v]$videoFilter[filtered];[1:v]scale=iw:ih[scaled];[filtered][scaled]overlay=0:0\" -c:v libx264 -crf 18 -preset fast -pix_fmt yuv420p -c:a copy \"$outputPath\""
                } else if (frameFile != null) {
                    // Only frame overlay - scale frame to match video size exactly
                    "-y -i \"$inputPath\" -i \"${frameFile.absolutePath}\" -filter_complex \"[1:v]scale2ref[frame][video];[video][frame]overlay=0:0\" -c:v libx264 -crf 18 -preset fast -pix_fmt yuv420p -c:a copy \"$outputPath\""
                } else if (filterMode != "normal") {
                    // Only filter
                    val videoFilter = when (filterMode) {
                        "bw" -> "hue=s=0"
                        "sepia" -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"
                        "vintage" -> "hue=s=0.5,curves=vintage"
                        "contrast" -> "eq=contrast=2:brightness=-0.1"
                        "invert" -> "negate"
                        "cold" -> "colorbalance=bs=-0.2:ms=-0.1:hs=0.1"
                        else -> "null"
                    }
                    "-y -i \"$inputPath\" -vf \"$videoFilter\" -c:v libx264 -crf 18 -preset fast -pix_fmt yuv420p -c:a copy \"$outputPath\""
                } else {
                    // No processing needed (shouldn't happen)
                    "-y -i \"$inputPath\" -c copy \"$outputPath\""
                }
                
                android.util.Log.d("VideoProcessing", "FFmpeg command: $command")
                
                val session = FFmpegKit.execute(command)
                
                android.util.Log.d("VideoProcessing", "Return code: ${session.returnCode}")
                android.util.Log.d("VideoProcessing", "Output: ${session.output}")
                
                // Clean up frame file
                frameFile?.delete()
                
                if (ReturnCode.isSuccess(session.returnCode)) {
                    val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + "_processed"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoBooth/$eventName")
                        }
                    }
                    
                    val outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    outputUri?.let { uri ->
                        contentResolver.openOutputStream(uri)?.use { output ->
                            outputFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        
                        outputFile.delete()
                        inputFile?.delete()
                        
                        runOnUiThread {
                            Toast.makeText(this, "Video procesado correctamente", Toast.LENGTH_SHORT).show()
                            showPreview(null, uri)
                        }
                    } ?: run {
                        outputFile.delete()
                        inputFile?.delete()
                        runOnUiThread {
                            Toast.makeText(this, "Error al guardar video", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    outputFile.delete()
                    inputFile?.delete()
                    runOnUiThread {
                        Toast.makeText(this, "Error procesando video", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                inputFile?.delete()
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    showPreview(null, inputUri)
                }
            }
        }.start()
    }
    
    private fun updateFilterIndicator() {
        val filterText = when (filterMode) {
            "normal" -> "✨ Normal"
            "bw" -> "⬜ Blanco y Negro"
            "sepia" -> "🟤 Sepia"
            "vintage" -> "📷 Retro"
            "contrast" -> "⚡ Alto Contraste"
            "invert" -> "🌈 Invertir"
            "cold" -> "❄️ Tonos Fríos"
            else -> "✨ Normal"
        }
        binding.filterIndicator.text = filterText
        binding.filterIndicator.visibility = if (filterMode == "normal") View.GONE else View.VISIBLE
    }
    
    /**
     * Apply visual filter overlay to simulate filter effect in real-time
     */
    private fun applyFilterOverlay() {
        when (filterMode) {
            "bw" -> {
                // Black and white: desaturate overlay
                binding.filterOverlay.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                binding.filterOverlay.alpha = 0f
                binding.filterOverlay.visibility = View.GONE
            }
            "sepia" -> {
                // Sepia tone: warm brown overlay
                binding.filterOverlay.setBackgroundColor(android.graphics.Color.parseColor("#80704214"))
                binding.filterOverlay.alpha = 0.3f
                binding.filterOverlay.visibility = View.VISIBLE
            }
            "vintage" -> {
                // Vintage: slightly desaturated warm tone
                binding.filterOverlay.setBackgroundColor(android.graphics.Color.parseColor("#80B8860B"))
                binding.filterOverlay.alpha = 0.2f
                binding.filterOverlay.visibility = View.VISIBLE
            }
            "contrast" -> {
                // High contrast: no overlay needed, just indicator
                binding.filterOverlay.visibility = View.GONE
            }
            "invert" -> {
                // Inverted colors: no good way to simulate, just show indicator
                binding.filterOverlay.visibility = View.GONE
            }
            "cold" -> {
                // Cold tones: blue overlay
                binding.filterOverlay.setBackgroundColor(android.graphics.Color.parseColor("#8000BFFF"))
                binding.filterOverlay.alpha = 0.25f
                binding.filterOverlay.visibility = View.VISIBLE
            }
            else -> {
                binding.filterOverlay.visibility = View.GONE
            }
        }
    }
    
    /**
     * Load and display frame overlay on camera preview
     */
    private fun loadFrameOverlay() {
        try {
            if (framePath.isEmpty()) {
                binding.frameOverlay.visibility = View.GONE
                return
            }
            
            val inputStream = assets.open(framePath)
            val frameBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (frameBitmap != null) {
                binding.frameOverlay.setImageBitmap(frameBitmap)
                binding.frameOverlay.visibility = View.VISIBLE
            } else {
                binding.frameOverlay.visibility = View.GONE
            }
        } catch (e: Exception) {
            android.util.Log.e("CameraActivity", "Error loading frame overlay: ${e.message}", e)
            binding.frameOverlay.visibility = View.GONE
        }
    }
    
    /**
     * Apply a decorative frame overlay on top of the photo
     * Frame files should be loaded from assets/marcos/[theme]/[frame].png
     */
    private fun applyFrame(bitmap: Bitmap, framePath: String): Bitmap {
        try {
            if (framePath.isEmpty()) {
                return bitmap
            }
            
            // Load frame from assets
            val inputStream = assets.open(framePath)
            val frameBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (frameBitmap == null) {
                android.util.Log.w("CameraActivity", "Frame bitmap is null: $framePath")
                return bitmap
            }
            
            // Create output bitmap with the same dimensions
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            
            // Draw original photo
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            
            // Scale and draw frame on top
            val scaledFrame = Bitmap.createScaledBitmap(frameBitmap, bitmap.width, bitmap.height, true)
            canvas.drawBitmap(scaledFrame, 0f, 0f, null)
            
            // Recycle temporary bitmaps
            if (scaledFrame != frameBitmap) {
                scaledFrame.recycle()
            }
            frameBitmap.recycle()
            
            return result
        } catch (e: Exception) {
            android.util.Log.e("CameraActivity", "Error applying frame: ${e.message}", e)
            return bitmap
        }
    }
    
    /**
     * Remove background using ML Kit Selfie Segmentation and apply new background
     * Background files should be named: bg_city.jpg, bg_beach.jpg, etc. in res/drawable
     */
    private fun processWithBackgroundRemoval(bitmap: Bitmap, callback: (Bitmap) -> Unit) {
        try {
            // Create ML Kit input image
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            // Configure selfie segmenter with raw size mask for better quality
            val options = SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .enableRawSizeMask()
                .build()
            val segmenter = Segmentation.getClient(options)
            
            segmenter.process(inputImage)
                .addOnSuccessListener { segmentationMask ->
                    val result = applyBackgroundWithMask(bitmap, segmentationMask)
                    callback(result)
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("CameraActivity", "Background removal failed: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(this, "Error al eliminar fondo", Toast.LENGTH_SHORT).show()
                    }
                    callback(bitmap) // Return original on failure
                }
        } catch (e: Exception) {
            android.util.Log.e("CameraActivity", "Error in background removal: ${e.message}", e)
            callback(bitmap)
        }
    }
    
    /**
     * Apply new background using segmentation mask
     */
    private fun applyBackgroundWithMask(original: Bitmap, mask: SegmentationMask): Bitmap {
        try {
            val width = original.width
            val height = original.height
            
            // Load background image from assets
            val bgBitmap = if (backgroundPath.isNotEmpty()) {
                // Load from assets path
                try {
                    val inputStream = assets.open(backgroundPath)
                    val bgOptions = BitmapFactory.Options()
                    bgOptions.inMutable = true
                    BitmapFactory.decodeStream(inputStream, null, bgOptions).also {
                        inputStream.close()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CameraActivity", "Background not found in assets: $backgroundPath")
                    return original
                }
            } else {
                // Fallback to drawable resource (legacy)
                val resourceId = resources.getIdentifier(backgroundMode, "drawable", packageName)
                if (resourceId == 0) {
                    android.util.Log.w("CameraActivity", "Background not found: $backgroundMode")
                    return original
                }
                val bgOptions = BitmapFactory.Options()
                bgOptions.inMutable = true
                BitmapFactory.decodeResource(resources, resourceId, bgOptions)
            }
            
            if (bgBitmap == null) {
                android.util.Log.w("CameraActivity", "Failed to decode background image")
                return original
            }
            
            // Scale background to match photo size
            val scaledBg = Bitmap.createScaledBitmap(bgBitmap, width, height, true)
            
            // Create result bitmap
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Get mask dimensions (may differ from image if enableRawSizeMask is used)
            val maskWidth = mask.width
            val maskHeight = mask.height
            
            val maskBuffer = mask.buffer
            maskBuffer.rewind()
            
            // Create float array for mask values
            val maskArray = FloatArray(maskWidth * maskHeight)
            maskBuffer.asFloatBuffer().get(maskArray)
            
            // Copy pixels
            val originalPixels = IntArray(width * height)
            val bgPixels = IntArray(width * height)
            original.getPixels(originalPixels, 0, width, 0, 0, width, height)
            scaledBg.getPixels(bgPixels, 0, width, 0, 0, width, height)
            
            val resultPixels = IntArray(width * height)
            
            // Scale factors for mask to image coordinates
            val scaleX = maskWidth.toFloat() / width
            val scaleY = maskHeight.toFloat() / height
            
            // Blend based on mask with improved thresholds
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    
                    // Map image coordinates to mask coordinates
                    val maskX = (x * scaleX).toInt().coerceIn(0, maskWidth - 1)
                    val maskY = (y * scaleY).toInt().coerceIn(0, maskHeight - 1)
                    val maskIndex = maskY * maskWidth + maskX
                    
                    val confidence = maskArray[maskIndex]
                    
                    // More aggressive threshold for better background removal
                    if (confidence > 0.7f) {
                        // Foreground - use original pixel
                        resultPixels[i] = originalPixels[i]
                    } else if (confidence > 0.3f) {
                        // Edge - blend for smooth transition
                        val alpha = (confidence - 0.3f) / 0.4f
                        resultPixels[i] = blendPixels(originalPixels[i], bgPixels[i], alpha)
                    } else {
                        // Background - use new background
                        resultPixels[i] = bgPixels[i]
                    }
                }
            }
            
            result.setPixels(resultPixels, 0, width, 0, 0, width, height)
            
            // Clean up
            scaledBg.recycle()
            if (bgBitmap != scaledBg) bgBitmap.recycle()
            
            return result
        } catch (e: Exception) {
            android.util.Log.e("CameraActivity", "Error applying background: ${e.message}", e)
            return original
        }
    }
    
    /**
     * Blend two pixels with given alpha (0 = pixel2, 1 = pixel1)
     */
    private fun blendPixels(pixel1: Int, pixel2: Int, alpha: Float): Int {
        val a1 = Color.alpha(pixel1)
        val r1 = Color.red(pixel1)
        val g1 = Color.green(pixel1)
        val b1 = Color.blue(pixel1)
        
        val a2 = Color.alpha(pixel2)
        val r2 = Color.red(pixel2)
        val g2 = Color.green(pixel2)
        val b2 = Color.blue(pixel2)
        
        val a = (a1 * alpha + a2 * (1 - alpha)).toInt()
        val r = (r1 * alpha + r2 * (1 - alpha)).toInt()
        val g = (g1 * alpha + g2 * (1 - alpha)).toInt()
        val b = (b1 * alpha + b2 * (1 - alpha)).toInt()
        
        return Color.argb(a, r, g, b)
    }
    
    private fun showGallery() {
        if (sessionPhotos.isEmpty()) {
            Toast.makeText(this, "No hay fotos en esta sesión", Toast.LENGTH_SHORT).show()
            return
        }
        
        val intent = Intent(this, GalleryActivity::class.java)
        // Convert bitmaps to byte arrays
        val byteArrays = sessionPhotos.map { bitmap ->
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        intent.putExtra("PHOTO_COUNT", byteArrays.size)
        byteArrays.forEachIndexed { index, bytes ->
            intent.putExtra("PHOTO_$index", bytes)
        }
        startActivity(intent)
    }

    private fun flipCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                if (cameraSource == "usb") startUvcCamera() else startCamera()
            } else {
                Toast.makeText(this, "Permisos no concedidos", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        // Clean up UVC camera
        if (cameraSource == "usb") {
            uvcCameraHelper?.closeCamera()
            uvcCameraHelper?.release()
            restoreAudioRouting()
        }
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).toTypedArray()
    }
}
