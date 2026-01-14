package com.photobooth.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.photobooth.app.databinding.ActivityProcessingBinding
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProcessingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProcessingBinding
    
    // Processing parameters from intent
    private var inputPath: String = ""
    private var backgroundPath: String = ""
    private var framePath: String = ""
    private var filterMode: String = "normal"
    private var slowMotionMode: String = "normal"
    private var eventName: String = "Evento"
    private var faceFilterType: String = "none"
    private var faceFilterPath: String = ""
    
    private var totalSteps = 4
    private var currentStep = 1
    
    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("ProcessingActivity", "=== ProcessingActivity Started ===")
        binding = ActivityProcessingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get parameters from intent
        inputPath = intent.getStringExtra("INPUT_PATH") ?: ""
        backgroundPath = intent.getStringExtra("BACKGROUND_PATH") ?: ""
        framePath = intent.getStringExtra("FRAME_PATH") ?: ""
        filterMode = intent.getStringExtra("FILTER_MODE") ?: "normal"
        slowMotionMode = intent.getStringExtra("SLOW_MOTION_MODE") ?: "normal"
        eventName = intent.getStringExtra("EVENT_NAME") ?: "Evento"
        faceFilterType = intent.getStringExtra("FACE_FILTER_TYPE") ?: "none"
        faceFilterPath = intent.getStringExtra("FACE_FILTER_PATH") ?: ""
        
        android.util.Log.d("ProcessingActivity", "Input path: $inputPath")
        android.util.Log.d("ProcessingActivity", "Background path: $backgroundPath")
        android.util.Log.d("ProcessingActivity", "Frame path: $framePath")
        android.util.Log.d("ProcessingActivity", "Filter mode: $filterMode")
        android.util.Log.d("ProcessingActivity", "Slow motion: $slowMotionMode")
        android.util.Log.d("ProcessingActivity", "Event name: $eventName")
        android.util.Log.d("ProcessingActivity", "Face filter type: $faceFilterType")
        android.util.Log.d("ProcessingActivity", "Face filter path: $faceFilterPath")
        
        // Disable back button during processing
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - prevent back during processing
            }
        })
        
        // Calculate total steps
        val hasBackgroundRemoval = backgroundPath.isNotEmpty()
        val hasFaceFilter = faceFilterType != "none" && faceFilterPath.isNotEmpty()
        
        totalSteps = 1 // Extract frames (always if any processing)
        if (hasBackgroundRemoval) totalSteps++ // ML Kit background removal
        if (hasFaceFilter) totalSteps++ // Face filter processing
        totalSteps++ // Recompose video
        if (filterMode != "normal") totalSteps++
        if (framePath.isNotEmpty()) totalSteps++
        if (slowMotionMode != "normal") totalSteps++
        totalSteps++ // Final save
        
        if (inputPath.isEmpty() || (!hasBackgroundRemoval && !hasFaceFilter)) {
            updateStatus("Error: Parámetros inválidos")
            finishWithError()
            return
        }
        
        // Start processing in background thread
        Thread {
            processVideo()
        }.start()
    }
    
    private fun updateUI(status: String, progress: Int, step: Int? = null) {
        runOnUiThread {
            binding.textStatus.text = status
            binding.progressBar.progress = progress
            binding.textProgress.text = "$progress%"
            if (step != null) {
                currentStep = step
                binding.textStep.text = "Paso $currentStep de $totalSteps"
            }
        }
    }
    
    private fun updateStatus(status: String) {
        runOnUiThread {
            binding.textStatus.text = status
        }
    }
    
    private fun processVideo() {
        android.util.Log.d("ProcessingActivity", "========================================")
        android.util.Log.d("ProcessingActivity", "processVideo() STARTED")
        android.util.Log.d("ProcessingActivity", "========================================")
        
        val hasBackgroundRemoval = backgroundPath.isNotEmpty()
        val hasFaceFilter = faceFilterType != "none" && faceFilterPath.isNotEmpty()
        
        try {
            // Step 1: Extract frames
            currentStep = 1
            android.util.Log.d("ProcessingActivity", "Step 1: Extracting frames")
            updateUI("Extrayendo frames del video...", 0, 1)
            
            val framesDir = File(cacheDir, "frames_${System.currentTimeMillis()}")
            val processedDir = File(cacheDir, "processed_frames_${System.currentTimeMillis()}")
            framesDir.mkdirs()
            processedDir.mkdirs()
            android.util.Log.d("ProcessingActivity", "Frames dir: ${framesDir.absolutePath}")
            android.util.Log.d("ProcessingActivity", "Processed dir: ${processedDir.absolutePath}")
            
            // Get video FPS
            android.util.Log.d("ProcessingActivity", "Getting video info with FFprobe...")
            val mediaInfoSession = FFprobeKit.getMediaInformation(inputPath)
            val mediaInfo = mediaInfoSession.mediaInformation
            var fps = 30.0
            mediaInfo?.streams?.firstOrNull { it.type == "video" }?.let { stream ->
                val frameRateStr = stream.averageFrameRate ?: "30"
                android.util.Log.d("ProcessingActivity", "Frame rate string: $frameRateStr")
                fps = if (frameRateStr.contains("/")) {
                    val parts = frameRateStr.split("/")
                    parts[0].toDouble() / parts[1].toDouble()
                } else {
                    frameRateStr.toDoubleOrNull() ?: 30.0
                }
            }
            android.util.Log.d("ProcessingActivity", "Video FPS: $fps")
            
            // Extract frames
            val extractCommand = "-i \"$inputPath\" -vf fps=$fps \"${framesDir.absolutePath}/frame_%05d.png\""
            android.util.Log.d("ProcessingActivity", "FFmpeg extract command: $extractCommand")
            val extractSession = FFmpegKit.execute(extractCommand)
            android.util.Log.d("ProcessingActivity", "FFmpeg return code: ${extractSession.returnCode}")
            
            if (!ReturnCode.isSuccess(extractSession.returnCode)) {
                android.util.Log.e("ProcessingActivity", "FAILED to extract frames!")
                updateStatus("Error extrayendo frames")
                cleanupAndFinish(framesDir, processedDir, null)
                return
            }
            
            android.util.Log.d("ProcessingActivity", "Frame extraction SUCCESS")
            updateUI("Frames extraídos", 10, 1)
            
            // Get frame files
            val frameFiles = framesDir.listFiles()?.sortedBy { it.name } ?: emptyList()
            val totalFrames = frameFiles.size
            android.util.Log.d("ProcessingActivity", "Total frames extracted: $totalFrames")
            
            if (totalFrames == 0) {
                android.util.Log.e("ProcessingActivity", "ERROR: No frames extracted!")
                updateStatus("No se pudieron extraer frames")
                cleanupAndFinish(framesDir, processedDir, null)
                return
            }
            
            // Load resources
            var bgBitmap: Bitmap? = null
            var faceFilterBitmap: Bitmap? = null
            var segmenter: com.google.mlkit.vision.segmentation.Segmenter? = null
            var faceDetector: FaceDetector? = null
            
            // Load background if needed
            if (hasBackgroundRemoval) {
                currentStep++
                android.util.Log.d("ProcessingActivity", "Loading background from: $backgroundPath")
                updateUI("Cargando fondo...", 12, currentStep)
                bgBitmap = try {
                    assets.open(backgroundPath).use { BitmapFactory.decodeStream(it) }
                } catch (e: Exception) {
                    android.util.Log.e("ProcessingActivity", "ERROR loading background: ${e.message}", e)
                    updateStatus("Error cargando fondo: ${e.message}")
                    cleanupAndFinish(framesDir, processedDir, null)
                    return
                }
                android.util.Log.d("ProcessingActivity", "Background loaded: ${bgBitmap.width}x${bgBitmap.height}")
                
                // Initialize segmenter
                segmenter = Segmentation.getClient(
                    SelfieSegmenterOptions.Builder()
                        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                        .enableRawSizeMask()
                        .build()
                )
            }
            
            // Load face filter if needed
            if (hasFaceFilter) {
                currentStep++
                android.util.Log.d("ProcessingActivity", "Loading face filter from: $faceFilterPath")
                updateUI("Cargando filtro facial...", 14, currentStep)
                faceFilterBitmap = try {
                    assets.open(faceFilterPath).use { BitmapFactory.decodeStream(it) }
                } catch (e: Exception) {
                    android.util.Log.e("ProcessingActivity", "ERROR loading face filter: ${e.message}", e)
                    updateStatus("Error cargando filtro facial")
                    cleanupAndFinish(framesDir, processedDir, null)
                    return
                }
                android.util.Log.d("ProcessingActivity", "Face filter loaded: ${faceFilterBitmap.width}x${faceFilterBitmap.height}")
                
                // Initialize face detector with landmarks
                val faceDetectorOptions = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .build()
                faceDetector = FaceDetection.getClient(faceDetectorOptions)
            }
            
            updateUI("Procesando frames (0/$totalFrames)...", 15, currentStep)
            
            val processedCount = AtomicInteger(0)
            val latch = CountDownLatch(totalFrames)
            
            android.util.Log.d("ProcessingActivity", "Starting frame processing loop...")
            frameFiles.forEachIndexed { index, frameFile ->
                if (index == 0) {
                    android.util.Log.d("ProcessingActivity", "Processing FIRST frame: ${frameFile.absolutePath}")
                }
                
                var frameBitmap = BitmapFactory.decodeFile(frameFile.absolutePath)
                if (frameBitmap == null) {
                    android.util.Log.e("ProcessingActivity", "ERROR: Could not decode frame $index")
                    latch.countDown()
                    return@forEachIndexed
                }
                
                // Make bitmap mutable for drawing
                frameBitmap = frameBitmap.copy(Bitmap.Config.ARGB_8888, true)
                
                val inputImage = InputImage.fromBitmap(frameBitmap, 0)
                
                // Process background removal first (if enabled)
                if (hasBackgroundRemoval && segmenter != null && bgBitmap != null) {
                    val finalFrameBitmap = frameBitmap
                    segmenter.process(inputImage)
                        .addOnSuccessListener { mask ->
                            var processed = applyBackgroundToFrame(finalFrameBitmap, mask, bgBitmap)
                            
                            // Then apply face filter (if enabled)
                            if (hasFaceFilter && faceDetector != null && faceFilterBitmap != null) {
                                val processedImage = InputImage.fromBitmap(processed, 0)
                                faceDetector.process(processedImage)
                                    .addOnSuccessListener { faces ->
                                        processed = applyFaceFilter(processed, faces, faceFilterBitmap, faceFilterType)
                                        saveProcessedFrame(processed, processedDir, index, processedCount, totalFrames, latch)
                                    }
                                    .addOnFailureListener {
                                        saveProcessedFrame(processed, processedDir, index, processedCount, totalFrames, latch)
                                    }
                            } else {
                                saveProcessedFrame(processed, processedDir, index, processedCount, totalFrames, latch)
                            }
                            finalFrameBitmap.recycle()
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.w("ProcessingActivity", "Frame $index bg removal failed: ${e.message}")
                            // Try face filter on original
                            if (hasFaceFilter && faceDetector != null && faceFilterBitmap != null) {
                                faceDetector.process(inputImage)
                                    .addOnSuccessListener { faces ->
                                        val processed = applyFaceFilter(finalFrameBitmap, faces, faceFilterBitmap, faceFilterType)
                                        saveProcessedFrame(processed, processedDir, index, processedCount, totalFrames, latch)
                                    }
                                    .addOnFailureListener {
                                        frameFile.copyTo(File(processedDir, frameFile.name), overwrite = true)
                                        processedCount.incrementAndGet()
                                        latch.countDown()
                                    }
                            } else {
                                frameFile.copyTo(File(processedDir, frameFile.name), overwrite = true)
                                processedCount.incrementAndGet()
                                latch.countDown()
                            }
                        }
                } else if (hasFaceFilter && faceDetector != null && faceFilterBitmap != null) {
                    // Only face filter, no background removal
                    val finalFrameBitmap = frameBitmap
                    faceDetector.process(inputImage)
                        .addOnSuccessListener { faces ->
                            val processed = applyFaceFilter(finalFrameBitmap, faces, faceFilterBitmap, faceFilterType)
                            saveProcessedFrame(processed, processedDir, index, processedCount, totalFrames, latch)
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.w("ProcessingActivity", "Frame $index face detection failed: ${e.message}")
                            frameFile.copyTo(File(processedDir, frameFile.name), overwrite = true)
                            processedCount.incrementAndGet()
                            latch.countDown()
                        }
                } else {
                    // No processing needed - just copy
                    frameFile.copyTo(File(processedDir, frameFile.name), overwrite = true)
                    processedCount.incrementAndGet()
                    latch.countDown()
                }
            }
            
            // Wait for all frames (max 15 minutes)
            android.util.Log.d("ProcessingActivity", "Waiting for all $totalFrames frames to be processed...")
            val allProcessed = latch.await(15, TimeUnit.MINUTES)
            android.util.Log.d("ProcessingActivity", "Wait completed. allProcessed: $allProcessed, count: ${processedCount.get()}")
            
            // Cleanup
            bgBitmap?.recycle()
            faceFilterBitmap?.recycle()
            segmenter?.close()
            faceDetector?.close()
            framesDir.deleteRecursively()
            
            // Check processed frames
            val processedFiles = processedDir.listFiles()
            android.util.Log.d("ProcessingActivity", "Processed frames in dir: ${processedFiles?.size ?: 0}")
            
            // Step: Recompose video
            currentStep++
            android.util.Log.d("ProcessingActivity", "Step $currentStep: Recomposing video")
            updateUI("Recomponiendo video...", 70, currentStep)
            
            var currentOutputFile = File(cacheDir, "bg_processed_${System.currentTimeMillis()}.mp4")
            
            // Build FFmpeg command to create video from frames + audio
            var composeCommand = "-framerate $fps -i \"${processedDir.absolutePath}/frame_%05d.png\" " +
                    "-i \"$inputPath\" -map 0:v -map 1:a? "
            
            // Add filter if needed
            if (filterMode != "normal") {
                val videoFilter = getFFmpegFilter(filterMode)
                composeCommand += "-vf \"$videoFilter\" "
            }
            
            composeCommand += "-c:v mpeg4 -pix_fmt yuv420p -c:a copy -q:v 5 -b:v 8M \"${currentOutputFile.absolutePath}\""
            
            android.util.Log.d("ProcessingActivity", "FFmpeg compose command: $composeCommand")
            val composeSession = FFmpegKit.execute(composeCommand)
            android.util.Log.d("ProcessingActivity", "Compose return code: ${composeSession.returnCode}")
            android.util.Log.d("ProcessingActivity", "Compose output: ${composeSession.output}")
            
            // Clean up processed frames
            android.util.Log.d("ProcessingActivity", "Cleaning up processed frames")
            processedDir.deleteRecursively()
            
            if (!ReturnCode.isSuccess(composeSession.returnCode)) {
                android.util.Log.e("ProcessingActivity", "ERROR: Failed to recompose video")
                updateStatus("Error recomponiendo video")
                currentOutputFile.delete()
                finishWithError()
                return
            }
            
            updateUI("Video recompuesto", 80, 3)
            
            // Step 4: Apply frame overlay if needed
            if (framePath.isNotEmpty()) {
                currentStep++
                android.util.Log.d("ProcessingActivity", "Step $currentStep: Applying frame overlay")
                updateUI("Aplicando marco...", 82, currentStep)
                
                val frameFile = File(cacheDir, "frame_overlay_${System.currentTimeMillis()}.png")
                assets.open(framePath).use { input ->
                    frameFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                val framedOutputFile = File(cacheDir, "framed_${System.currentTimeMillis()}.mp4")
                val frameCommand = "-i \"${currentOutputFile.absolutePath}\" -i \"${frameFile.absolutePath}\" " +
                        "-filter_complex \"[1:v]scale2ref[frame][video];[video][frame]overlay=0:0\" " +
                        "-c:a copy -preset ultrafast -b:v 8M \"${framedOutputFile.absolutePath}\""
                
                val frameSession = FFmpegKit.execute(frameCommand)
                
                frameFile.delete()
                currentOutputFile.delete()
                
                if (ReturnCode.isSuccess(frameSession.returnCode)) {
                    currentOutputFile = framedOutputFile
                    updateUI("Marco aplicado", 88, currentStep)
                } else {
                    updateStatus("Error aplicando marco")
                    framedOutputFile.delete()
                    finishWithError()
                    return
                }
            }
            
            // Step 5: Apply slow motion if needed
            if (slowMotionMode != "normal") {
                currentStep++
                updateUI("Aplicando cámara lenta...", 90, currentStep)
                
                val slowOutputFile = File(cacheDir, "slow_${System.currentTimeMillis()}.mp4")
                val slowFactor = if (slowMotionMode == "0.5x") 2.0 else 1.5
                
                val slowCommand = "-i \"${currentOutputFile.absolutePath}\" " +
                        "-filter_complex \"[0:v]setpts=${slowFactor}*PTS[v];[0:a]atempo=${1/slowFactor}[a]\" " +
                        "-map \"[v]\" -map \"[a]\" -preset ultrafast -b:v 8M \"${slowOutputFile.absolutePath}\""
                
                val slowSession = FFmpegKit.execute(slowCommand)
                
                currentOutputFile.delete()
                
                if (ReturnCode.isSuccess(slowSession.returnCode)) {
                    currentOutputFile = slowOutputFile
                    updateUI("Cámara lenta aplicada", 95, currentStep)
                } else {
                    // Continue without slow motion
                    currentOutputFile = slowOutputFile
                }
            }
            
            // Final step: Save to gallery
            currentStep = totalSteps
            android.util.Log.d("ProcessingActivity", "========================================")
            android.util.Log.d("ProcessingActivity", "Final step: Saving to gallery")
            android.util.Log.d("ProcessingActivity", "Output file: ${currentOutputFile.absolutePath}")
            android.util.Log.d("ProcessingActivity", "Output file exists: ${currentOutputFile.exists()}")
            android.util.Log.d("ProcessingActivity", "Output file size: ${currentOutputFile.length()}")
            android.util.Log.d("ProcessingActivity", "========================================")
            updateUI("Guardando video...", 97, currentStep)
            
            val savedUri = saveVideoToGallery(currentOutputFile)
            currentOutputFile.delete()
            
            if (savedUri != null) {
                android.util.Log.d("ProcessingActivity", "SUCCESS! Video saved to: $savedUri")
                updateUI("¡Video procesado correctamente!", 100, currentStep)
                Thread.sleep(500)
                finishWithSuccess(savedUri)
            } else {
                android.util.Log.e("ProcessingActivity", "ERROR: Failed to save video to gallery")
                updateStatus("Error guardando video")
                finishWithError()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ProcessingActivity", "FATAL ERROR: ${e.message}", e)
            e.printStackTrace()
            updateStatus("Error: ${e.message}")
            finishWithError()
        }
    }
    
    private fun saveProcessedFrame(
        bitmap: Bitmap,
        processedDir: File,
        index: Int,
        processedCount: AtomicInteger,
        totalFrames: Int,
        latch: CountDownLatch
    ) {
        try {
            val outputFile = File(processedDir, "frame_%05d.png".format(index + 1))
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            
            val count = processedCount.incrementAndGet()
            val progress = 15 + (count * 50 / totalFrames)
            if (count % 10 == 0) {
                updateUI("Procesando frames ($count/$totalFrames)...", progress, currentStep)
            }
        } catch (e: Exception) {
            android.util.Log.e("ProcessingActivity", "Error saving frame $index: ${e.message}")
        } finally {
            latch.countDown()
        }
    }
    
    /**
     * Apply face filter overlay based on face detection results
     */
    private fun applyFaceFilter(original: Bitmap, faces: List<Face>, filterBitmap: Bitmap, filterType: String): Bitmap {
        if (faces.isEmpty()) {
            return original
        }
        
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        
        for (face in faces) {
            val boundingBox = face.boundingBox
            val faceWidth = boundingBox.width().toFloat()
            val faceHeight = boundingBox.height().toFloat()
            
            // Calculate filter position and size based on filter type
            when (filterType) {
                "mustache" -> {
                    // Position mustache below nose
                    val noseLandmark = face.getLandmark(FaceLandmark.NOSE_BASE)
                    val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)
                    val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)
                    
                    if (noseLandmark != null) {
                        val filterWidth = faceWidth * 0.6f
                        val filterHeight = filterWidth * filterBitmap.height / filterBitmap.width
                        
                        val centerX = noseLandmark.position.x
                        val centerY = if (mouthLeft != null && mouthRight != null) {
                            (noseLandmark.position.y + (mouthLeft.position.y + mouthRight.position.y) / 2) / 2
                        } else {
                            noseLandmark.position.y + faceHeight * 0.1f
                        }
                        
                        val destRect = RectF(
                            centerX - filterWidth / 2,
                            centerY - filterHeight / 2,
                            centerX + filterWidth / 2,
                            centerY + filterHeight / 2
                        )
                        
                        // Apply rotation based on face angle
                        val rotationZ = face.headEulerAngleZ
                        canvas.save()
                        canvas.rotate(rotationZ, centerX, centerY)
                        canvas.drawBitmap(filterBitmap, null, destRect, paint)
                        canvas.restore()
                    }
                }
                
                "hat" -> {
                    // Position hat above head
                    val filterWidth = faceWidth * 1.3f
                    val filterHeight = filterWidth * filterBitmap.height / filterBitmap.width
                    
                    val centerX = boundingBox.centerX().toFloat()
                    val topY = boundingBox.top - filterHeight * 0.3f
                    
                    val destRect = RectF(
                        centerX - filterWidth / 2,
                        topY - filterHeight * 0.7f,
                        centerX + filterWidth / 2,
                        topY + filterHeight * 0.3f
                    )
                    
                    val rotationZ = face.headEulerAngleZ
                    canvas.save()
                    canvas.rotate(rotationZ, centerX, boundingBox.centerY().toFloat())
                    canvas.drawBitmap(filterBitmap, null, destRect, paint)
                    canvas.restore()
                }
                
                "glasses" -> {
                    // Position glasses over eyes
                    val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                    val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
                    
                    if (leftEye != null && rightEye != null) {
                        val eyeDistance = kotlin.math.abs(rightEye.position.x - leftEye.position.x)
                        val filterWidth = eyeDistance * 2.2f
                        val filterHeight = filterWidth * filterBitmap.height / filterBitmap.width
                        
                        val centerX = (leftEye.position.x + rightEye.position.x) / 2
                        val centerY = (leftEye.position.y + rightEye.position.y) / 2
                        
                        val destRect = RectF(
                            centerX - filterWidth / 2,
                            centerY - filterHeight / 2,
                            centerX + filterWidth / 2,
                            centerY + filterHeight / 2
                        )
                        
                        val rotationZ = face.headEulerAngleZ
                        canvas.save()
                        canvas.rotate(rotationZ, centerX, centerY)
                        canvas.drawBitmap(filterBitmap, null, destRect, paint)
                        canvas.restore()
                    }
                }
                
                "mask" -> {
                    // Position mask over entire face
                    val filterWidth = faceWidth * 1.2f
                    val filterHeight = faceHeight * 1.2f
                    
                    val centerX = boundingBox.centerX().toFloat()
                    val centerY = boundingBox.centerY().toFloat()
                    
                    val destRect = RectF(
                        centerX - filterWidth / 2,
                        centerY - filterHeight / 2,
                        centerX + filterWidth / 2,
                        centerY + filterHeight / 2
                    )
                    
                    val rotationZ = face.headEulerAngleZ
                    canvas.save()
                    canvas.rotate(rotationZ, centerX, centerY)
                    canvas.drawBitmap(filterBitmap, null, destRect, paint)
                    canvas.restore()
                }
                
                "ears" -> {
                    // Position ears on sides of head (like bunny/cat ears)
                    val filterWidth = faceWidth * 1.5f
                    val filterHeight = filterWidth * filterBitmap.height / filterBitmap.width
                    
                    val centerX = boundingBox.centerX().toFloat()
                    val topY = boundingBox.top.toFloat()
                    
                    val destRect = RectF(
                        centerX - filterWidth / 2,
                        topY - filterHeight * 0.8f,
                        centerX + filterWidth / 2,
                        topY + filterHeight * 0.2f
                    )
                    
                    val rotationZ = face.headEulerAngleZ
                    canvas.save()
                    canvas.rotate(rotationZ, centerX, boundingBox.centerY().toFloat())
                    canvas.drawBitmap(filterBitmap, null, destRect, paint)
                    canvas.restore()
                }
                
                "nose" -> {
                    // Position nose filter over nose
                    val noseLandmark = face.getLandmark(FaceLandmark.NOSE_BASE)
                    
                    if (noseLandmark != null) {
                        val filterWidth = faceWidth * 0.35f
                        val filterHeight = filterWidth * filterBitmap.height / filterBitmap.width
                        
                        val centerX = noseLandmark.position.x
                        val centerY = noseLandmark.position.y - faceHeight * 0.05f
                        
                        val destRect = RectF(
                            centerX - filterWidth / 2,
                            centerY - filterHeight / 2,
                            centerX + filterWidth / 2,
                            centerY + filterHeight / 2
                        )
                        
                        val rotationZ = face.headEulerAngleZ
                        canvas.save()
                        canvas.rotate(rotationZ, centerX, centerY)
                        canvas.drawBitmap(filterBitmap, null, destRect, paint)
                        canvas.restore()
                    }
                }
                
                "full" -> {
                    // Full face overlay - scale to fit entire face area
                    val filterWidth = faceWidth * 1.4f
                    val filterHeight = faceHeight * 1.6f
                    
                    val centerX = boundingBox.centerX().toFloat()
                    val centerY = boundingBox.centerY().toFloat() - faceHeight * 0.1f
                    
                    val destRect = RectF(
                        centerX - filterWidth / 2,
                        centerY - filterHeight / 2,
                        centerX + filterWidth / 2,
                        centerY + filterHeight / 2
                    )
                    
                    val rotationZ = face.headEulerAngleZ
                    canvas.save()
                    canvas.rotate(rotationZ, centerX, centerY)
                    canvas.drawBitmap(filterBitmap, null, destRect, paint)
                    canvas.restore()
                }
                
                else -> {
                    // Default: center on face
                    val filterWidth = faceWidth
                    val filterHeight = filterWidth * filterBitmap.height / filterBitmap.width
                    
                    val destRect = RectF(
                        boundingBox.left.toFloat(),
                        boundingBox.centerY() - filterHeight / 2,
                        boundingBox.right.toFloat(),
                        boundingBox.centerY() + filterHeight / 2
                    )
                    
                    canvas.drawBitmap(filterBitmap, null, destRect, paint)
                }
            }
        }
        
        return result
    }
    
    private fun getFFmpegFilter(filter: String): String {
        return when (filter) {
            "bw" -> "hue=s=0"
            "sepia" -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"
            "vintage" -> "hue=s=0.5,curves=vintage"
            "contrast" -> "eq=contrast=2:brightness=-0.1"
            "invert" -> "negate"
            "cold" -> "colorbalance=bs=-0.2:ms=-0.1:hs=0.1"
            else -> "null"
        }
    }
    
    private fun applyBackgroundToFrame(original: Bitmap, mask: SegmentationMask, bgBitmap: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        
        val scaledBg = Bitmap.createScaledBitmap(bgBitmap, width, height, true)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Get mask dimensions (may differ from image if enableRawSizeMask is used)
        val maskWidth = mask.width
        val maskHeight = mask.height
        
        val maskBuffer = mask.buffer
        maskBuffer.rewind()
        
        // Create float array for mask values
        val maskArray = FloatArray(maskWidth * maskHeight)
        maskBuffer.asFloatBuffer().get(maskArray)
        
        val originalPixels = IntArray(width * height)
        val bgPixels = IntArray(width * height)
        original.getPixels(originalPixels, 0, width, 0, 0, width, height)
        scaledBg.getPixels(bgPixels, 0, width, 0, 0, width, height)
        
        val resultPixels = IntArray(width * height)
        
        // Scale factors for mask to image coordinates
        val scaleX = maskWidth.toFloat() / width
        val scaleY = maskHeight.toFloat() / height
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                
                // Map image coordinates to mask coordinates
                val maskX = (x * scaleX).toInt().coerceIn(0, maskWidth - 1)
                val maskY = (y * scaleY).toInt().coerceIn(0, maskHeight - 1)
                val maskIndex = maskY * maskWidth + maskX
                
                val confidence = maskArray[maskIndex]
                
                // More aggressive threshold - higher threshold means more of the background is removed
                // confidence > 0.7 means we're more certain it's a person
                if (confidence > 0.7f) {
                    resultPixels[i] = originalPixels[i]
                } else if (confidence > 0.3f) {
                    // Blend zone for smoother edges
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
    
    private fun saveVideoToGallery(file: File): Uri? {
        return try {
            val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + "_processed"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoBooth/$eventName")
                }
            }
            
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }
            uri
        } catch (e: Exception) {
            null
        }
    }
    
    private fun cleanupAndFinish(framesDir: File, processedDir: File, outputFile: File?) {
        framesDir.deleteRecursively()
        processedDir.deleteRecursively()
        outputFile?.delete()
        finishWithError()
    }
    
    private fun finishWithSuccess(uri: Uri) {
        val resultIntent = Intent()
        resultIntent.putExtra("RESULT_URI", uri.toString())
        resultIntent.putExtra("SUCCESS", true)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
    
    private fun finishWithError() {
        runOnUiThread {
            Thread.sleep(2000) // Show error message for 2 seconds
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}
