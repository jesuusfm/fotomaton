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
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
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
    private var videoDuration = 10
    private var slowMotionMode = "normal" // normal, 0.5x, boomerang
    private var boomerangMinSpeed = 0.5f
    private var boomerangMaxSpeed = 1.0f
    private var boomerangFrequency = 0.1f
    private var frameMode = "none"
    private var framePath = ""
    private var backgroundMode = "none"
    private var backgroundPath = ""
    private var removeBackground = false
    private var faceFilterType = "none"
    private var faceFilterPath = ""
    
    // Sound for video recording end
    private var toneGenerator: ToneGenerator? = null

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
                    android.util.Log.d("CameraActivity", "Showing preview for processed video")
                    showPreview(null, uri)
                } else {
                    android.util.Log.e("CameraActivity", "ERROR: No URI in result")
                }
            }
        } else {
            android.util.Log.e("CameraActivity", "Processing failed or cancelled")
            Toast.makeText(this, "Error en el procesamiento", Toast.LENGTH_SHORT).show()
        }
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
        videoDuration = intent.getIntExtra("VIDEO_DURATION", 10)
        slowMotionMode = intent.getStringExtra("SLOW_MOTION_MODE") ?: "normal"
        boomerangMinSpeed  = intent.getFloatExtra("BOOMERANG_MIN_SPEED", 0.5f)
        boomerangMaxSpeed = intent.getFloatExtra("BOOMERANG_MAX_SPEED", 1.0f)
        boomerangFrequency = intent.getFloatExtra("BOOMERANG_FREQUENCY", 0.1f)
        frameMode = intent.getStringExtra("FRAME") ?: "none"
        framePath = intent.getStringExtra("FRAME_PATH") ?: ""
        backgroundMode = intent.getStringExtra("BACKGROUND") ?: "none"
        backgroundPath = intent.getStringExtra("BACKGROUND_PATH") ?: ""
        removeBackground = intent.getBooleanExtra("REMOVE_BG", false)
        faceFilterType = intent.getStringExtra("FACE_FILTER_TYPE") ?: "none"
        faceFilterPath = intent.getStringExtra("FACE_FILTER_PATH") ?: ""
        
        // Initialize tone generator for video end sound
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            android.util.Log.w("CameraActivity", "Could not create ToneGenerator: ${e.message}")
        }
        
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
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Button listeners
        binding.buttonFlipCamera.setOnClickListener {
            flipCamera()
        }

        binding.buttonCapture.setOnClickListener {
            if (recording != null) {
                // Stop recording if currently recording
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
                imageCapture = ImageCapture.Builder()
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture
                    )
                } catch (exc: Exception) {
                    Toast.makeText(this, "Error al iniciar la cámara", Toast.LENGTH_SHORT).show()
                }
            } else {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

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
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) // Beep for 3, 2, 1
                }
            }

            override fun onFinish() {
                binding.countdownText.visibility = View.GONE
                
                if (mode == "PHOTO") {
                    // Small delay before capture sound for photos only
                    Handler(Looper.getMainLooper()).postDelayed({
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 200) // Capture sound
                    }, 100)
                }
                
                if (mode == "PHOTO") {
                    if (isPhotoBoothMode) {
                        takePhotoBoothPhoto()
                    } else {
                        takePhoto()
                    }
                } else {
                    startVideoRecording()
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
                    
                    // Apply effects in order: filter -> background -> frame
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
                            sessionPhotos.add(finalBitmap)
                            savePhotoToGallery(finalBitmap)
                            Handler(Looper.getMainLooper()).postDelayed({
                                showPreview(finalBitmap, null)
                            }, 200)
                        }
                    } else {
                        if (frameMode != "none" && framePath.isNotEmpty()) {
                            bitmap = applyFrame(bitmap, framePath)
                        }
                        sessionPhotos.add(bitmap)
                        savePhotoToGallery(bitmap)
                        Handler(Looper.getMainLooper()).postDelayed({
                            showPreview(bitmap, null)
                        }, 200)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(baseContext, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun takePhotoBoothPhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    var bitmap = imageProxyToBitmap(image)
                    image.close()
                    
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
                        
                        // Play sound to indicate recording ended
                        playVideoEndSound()
                        
                        if (!recordEvent.hasError()) {
                            val uri = recordEvent.outputResults.outputUri
                            
                            android.util.Log.d("CameraActivity", "Video recording finalized. URI: $uri")
                            android.util.Log.d("CameraActivity", "slowMotionMode = $slowMotionMode")
                            android.util.Log.d("CameraActivity", "removeBackground = $removeBackground")
                            android.util.Log.d("CameraActivity", "backgroundPath = $backgroundPath")
                            android.util.Log.d("CameraActivity", "filterMode = $filterMode")
                            android.util.Log.d("CameraActivity", "frameMode = $frameMode")
                            android.util.Log.d("CameraActivity", "faceFilterType = $faceFilterType")
                            
                            // Check if heavy processing is needed - launch ProcessingActivity
                            val needsProcessingActivity = (removeBackground && backgroundPath.isNotEmpty()) ||
                                    (faceFilterType != "none" && faceFilterPath.isNotEmpty())
                            
                            if (needsProcessingActivity) {
                                android.util.Log.d("CameraActivity", "Heavy processing needed - launching ProcessingActivity")
                                launchProcessingActivity(uri)
                            } else if (slowMotionMode != "normal") {
                                android.util.Log.d("CameraActivity", "Processing slow motion")
                                Toast.makeText(this, "Video guardado. Procesando...", Toast.LENGTH_SHORT).show()
                                processSlowMotionVideo(uri)
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
        recording?.stop()
        recordingTimer?.cancel()
    }
    
    /**
     * Play sound when video recording ends
     */
    private fun playVideoEndSound() {
        try {
            // Try to play custom sound if available
            val soundResId = resources.getIdentifier("video_end", "raw", packageName)
            if (soundResId != 0) {
                val mediaPlayer = MediaPlayer.create(this, soundResId)
                mediaPlayer?.apply {
                    setOnCompletionListener { release() }
                    start()
                }
            } else {
                // Use system beep sound
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            }
        } catch (e: Exception) {
            android.util.Log.w("CameraActivity", "Could not play sound: ${e.message}")
        }
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
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
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
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
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
                
                android.util.Log.d("SlowMotion", "Getting real path from URI: $inputUri")
                
                // Get real path from URI
                val inputPath = getRealPathFromURI(inputUri)
                android.util.Log.d("SlowMotion", "Real path: $inputPath")
                
                if (inputPath == null) {
                    runOnUiThread {
                        Toast.makeText(this, "Error: No se pudo acceder al video", Toast.LENGTH_SHORT).show()
                        showPreview(null, inputUri)
                    }
                    return@Thread
                }
                
                // Create output file in cache
                val outputFile = File(cacheDir, "slow_motion_${System.currentTimeMillis()}.mp4")
                val outputPath = outputFile.absolutePath
                
                // Build FFmpeg command based on slow motion mode
                val command = when (slowMotionMode) {
                    "0.5x" -> {
                        // Simple slow motion at 0.5x speed
                        // setpts=2.0*PTS makes video 2x slower (0.5x speed)
                        // atempo=0.5 slows down audio to match
                        "-i \"$inputPath\" -filter:v \"setpts=2.0*PTS\" -filter:a \"atempo=0.5\" -b:v 5M -r 30 \"$outputPath\""
                    }
                    "boomerang" -> {
                        // Boomerang effect: oscillating speed with custom parameters
                        // Calculate average speed to determine output duration
                        val avgSpeed = (boomerangMinSpeed + boomerangMaxSpeed) / 2.0f
                        val speedRange = (boomerangMaxSpeed - boomerangMinSpeed) / 2.0f
                        
                        // For setpts: higher values = slower video
                        // We need to invert the speed logic for setpts
                        // If speed ranges from 0.5x to 1.0x, setpts divisor ranges from 2.0 to 1.0
                        val divisorMin = 1.0f / boomerangMaxSpeed  // At max speed, lowest divisor
                        val divisorMax = 1.0f / boomerangMinSpeed  // At min speed, highest divisor
                        val divisorAvg = (divisorMin + divisorMax) / 2.0f
                        val divisorRange = (divisorMax - divisorMin) / 2.0f
                        
                        // Apply average tempo to audio (must be between 0.5 and 2.0)
                        val audioTempo = avgSpeed.coerceIn(0.5f, 2.0f)
                        
                        "-i \"$inputPath\" -filter:v \"setpts='PTS*($divisorAvg+$divisorRange*sin(N*$boomerangFrequency))'\" -filter:a \"atempo=$audioTempo\" -b:v 5M -r 30 \"$outputPath\""
                    }
                    else -> {
                        // Default to 0.5x if mode is unknown
                        "-i \"$inputPath\" -filter:v \"setpts=2.0*PTS\" -filter:a \"atempo=0.5\" -b:v 5M -r 30 \"$outputPath\""
                    }
                }
                
                android.util.Log.d("SlowMotion", "Mode: $slowMotionMode")
                android.util.Log.d("SlowMotion", "Input path: $inputPath")
                android.util.Log.d("SlowMotion", "Output path: $outputPath")
                android.util.Log.d("SlowMotion", "Command: $command")
                
                // Execute FFmpeg command
                val session = FFmpegKit.execute(command)
                
                android.util.Log.d("SlowMotion", "Return code: ${session.returnCode}")
                android.util.Log.d("SlowMotion", "Output: ${session.output}")
                android.util.Log.d("SlowMotion", "Failed executions: ${session.failStackTrace}")
                
                if (ReturnCode.isSuccess(session.returnCode)) {
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
                        
                        // Clean up temp file
                        outputFile.delete()
                        
                        runOnUiThread {
                            Toast.makeText(this, "Video en cámara lenta listo", Toast.LENGTH_SHORT).show()
                            showPreview(null, uri)
                        }
                    } ?: run {
                        outputFile.delete()
                        runOnUiThread {
                            Toast.makeText(this, "Error al guardar video", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Error
                    outputFile.delete()
                    runOnUiThread {
                        Toast.makeText(this, "Error procesando video", Toast.LENGTH_SHORT).show()
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
        
        val inputPath = getRealPathFromURI(inputUri)
        android.util.Log.d("CameraActivity", "Real path: $inputPath")
        
        if (inputPath == null) {
            android.util.Log.e("CameraActivity", "ERROR: Could not get real path from URI")
            Toast.makeText(this, "Error: No se pudo acceder al video", Toast.LENGTH_SHORT).show()
            showPreview(null, inputUri)
            return
        }
        
        android.util.Log.d("CameraActivity", "Background path: $backgroundPath")
        android.util.Log.d("CameraActivity", "Frame path: $framePath")
        android.util.Log.d("CameraActivity", "Filter mode: $filterMode")
        android.util.Log.d("CameraActivity", "Slow motion: $slowMotionMode")
        android.util.Log.d("CameraActivity", "Face filter type: $faceFilterType")
        android.util.Log.d("CameraActivity", "Face filter path: $faceFilterPath")
        
        val intent = Intent(this, ProcessingActivity::class.java).apply {
            putExtra("INPUT_PATH", inputPath)
            putExtra("BACKGROUND_PATH", backgroundPath)
            putExtra("FRAME_PATH", if (frameMode != "none") framePath else "")
            putExtra("FILTER_MODE", filterMode)
            putExtra("SLOW_MOTION_MODE", slowMotionMode)
            putExtra("EVENT_NAME", eventName)
            putExtra("FACE_FILTER_TYPE", faceFilterType)
            putExtra("FACE_FILTER_PATH", faceFilterPath)
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
                
                composeCommand += "-c:v mpeg4 -pix_fmt yuv420p -c:a copy -q:v 5 -b:v 8M \"${outputFile.absolutePath}\""
                
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
                
                val inputPath = getRealPathFromURI(inputUri)
                if (inputPath == null) {
                    runOnUiThread {
                        Toast.makeText(this, "Error: No se pudo acceder al video", Toast.LENGTH_SHORT).show()
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
                    "-i \"$inputPath\" -i \"${frameFile.absolutePath}\" -filter_complex \"[0:v]$videoFilter[filtered];[1:v]scale=iw:ih[scaled];[filtered][scaled]overlay=0:0\" -c:a copy -preset ultrafast -b:v 8M \"$outputPath\""
                } else if (frameFile != null) {
                    // Only frame overlay - scale frame to match video size exactly
                    "-i \"$inputPath\" -i \"${frameFile.absolutePath}\" -filter_complex \"[1:v]scale2ref[frame][video];[video][frame]overlay=0:0\" -c:a copy -preset ultrafast -b:v 8M \"$outputPath\""
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
                    "-i \"$inputPath\" -vf \"$videoFilter\" -c:a copy -preset ultrafast -b:v 8M \"$outputPath\""
                } else {
                    // No processing needed (shouldn't happen)
                    "-i \"$inputPath\" -c copy \"$outputPath\""
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
                        
                        runOnUiThread {
                            Toast.makeText(this, "Video procesado correctamente", Toast.LENGTH_SHORT).show()
                            showPreview(null, uri)
                        }
                    } ?: run {
                        outputFile.delete()
                        runOnUiThread {
                            Toast.makeText(this, "Error al guardar video", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    outputFile.delete()
                    runOnUiThread {
                        Toast.makeText(this, "Error procesando video", Toast.LENGTH_SHORT).show()
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
                startCamera()
            } else {
                Toast.makeText(this, "Permisos no concedidos", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        toneGenerator?.release()
        toneGenerator = null
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
