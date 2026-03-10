package com.photobooth.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.photobooth.app.databinding.ActivityPreviewBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class PreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPreviewBinding
    private var type: String = "PHOTO"
    private lateinit var eventName: String
    private var isSlowMotion = false
    private var currentFileUri: String? = null
    private var currentFilePath: String? = null
    private var originalVideoUri: String? = null   // always the version with audio
    private var mutedVideoUri: String? = null       // cached version without audio
    private enum class AudioState { ORIGINAL, MUTED, MUSIC }
    private var audioState = AudioState.ORIGINAL
    private val musicVideoUriCache = mutableMapOf<String, String>()
    private var currentMusicAsset: String? = null
    private var isShowingPhotoAsVideo = false
    private var oscillationHandler: android.os.Handler? = null
    private var oscillationRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        type = intent.getStringExtra("TYPE") ?: "PHOTO"
        eventName = intent.getStringExtra("EVENT_NAME") ?: "Evento"
        isSlowMotion = intent.getBooleanExtra("SLOW_MOTION", false)

        if (type == "PHOTO") {
            // Try IMAGE_URI first (new way), then IMAGE_PATH (old way)
            val imageUriString = intent.getStringExtra("IMAGE_URI")
            val imagePath = intent.getStringExtra("IMAGE_PATH")
            
            if (imageUriString != null) {
                currentFileUri = imageUriString
                val uri = Uri.parse(imageUriString)
                binding.previewImage.setImageURI(uri)
            } else if (imagePath != null) {
                currentFilePath = imagePath
                val bitmap = BitmapFactory.decodeFile(imagePath)
                binding.previewImage.setImageBitmap(bitmap)
            }
            
            binding.previewImage.visibility = android.view.View.VISIBLE
            binding.previewVideo.visibility = android.view.View.GONE
        } else {
            val videoUriString = intent.getStringExtra("VIDEO_URI")
            val playbackSpeed = intent.getFloatExtra("PLAYBACK_SPEED", 1.0f)
            val slowMotionMode = intent.getStringExtra("SLOW_MOTION_MODE") ?: "normal"
            val boomerangMinSpeed = intent.getFloatExtra("BOOMERANG_MIN_SPEED", 0.5f)
            val boomerangMaxSpeed = intent.getFloatExtra("BOOMERANG_MAX_SPEED", 1.0f)
            val boomerangSlowDuration = intent.getFloatExtra("BOOMERANG_SLOW_DURATION", 3.0f)
            val boomerangFastDuration = intent.getFloatExtra("BOOMERANG_FAST_DURATION", 3.0f)
            videoUriString?.let { uriString ->
                currentFileUri = uriString
                originalVideoUri = uriString

                binding.previewVideo.setVideoURI(Uri.parse(uriString))
                binding.previewVideo.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    if (slowMotionMode == "boomerang") {
                        startSpeedOscillation(mp, boomerangMinSpeed, boomerangMaxSpeed, boomerangSlowDuration, boomerangFastDuration)
                    } else if (playbackSpeed != 1.0f) {
                        try {
                            mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                        } catch (e: Exception) {
                            android.util.Log.e("PreviewActivity", "Error setting playback speed: ${e.message}")
                        }
                    }
                    mp.start()
                }
            }
            binding.previewImage.visibility = android.view.View.GONE
            binding.previewVideo.visibility = android.view.View.VISIBLE
        }

        // Show mute toggle only for videos
        if (type == "VIDEO") {
            binding.buttonMuteAudio.visibility = View.VISIBLE
            binding.buttonMuteAudio.setOnClickListener { onMuteClicked() }
        }
        // Music button: visible for both photo and video
        binding.buttonMusic.visibility = View.VISIBLE
        binding.buttonMusic.setOnClickListener {
            if (audioState == AudioState.MUSIC) {
                currentMusicAsset = null
                audioState = AudioState.ORIGINAL
                updateAudioButtons()
                if (type == "VIDEO") {
                    currentFileUri = originalVideoUri
                    reloadVideo(Uri.parse(originalVideoUri ?: return@setOnClickListener))
                } else {
                    isShowingPhotoAsVideo = false
                    binding.previewVideo.stopPlayback()
                    binding.previewVideo.visibility = View.GONE
                    binding.previewImage.visibility = View.VISIBLE
                }
            } else {
                showMusicPicker()
            }
        }

        binding.buttonAnother.setOnClickListener {
            showConfirmationDialog()
        }

        binding.buttonShare.setOnClickListener {
            shareContent()
        }
    }

    private fun onMuteClicked() {
        when (audioState) {
            AudioState.MUTED -> {
                val uri = Uri.parse(originalVideoUri ?: return)
                audioState = AudioState.ORIGINAL
                currentFileUri = originalVideoUri
                currentMusicAsset = null
                updateAudioButtons()
                reloadVideo(uri)
            }
            else -> {
                val cached = mutedVideoUri
                if (cached != null) {
                    audioState = AudioState.MUTED
                    currentFileUri = cached
                    currentMusicAsset = null
                    updateAudioButtons()
                    reloadVideo(Uri.parse(cached))
                } else {
                    buildMutedVideo()
                }
            }
        }
    }

    private fun reloadVideo(uri: Uri) {
        oscillationRunnable?.let { oscillationHandler?.removeCallbacks(it) }
        binding.previewVideo.setVideoURI(uri)
        binding.previewVideo.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.start()
        }
        binding.previewVideo.start()
    }

    private fun buildMutedVideo() {
        val uriString = originalVideoUri ?: return
        binding.buttonMuteAudio.isEnabled = false
        binding.buttonMusic.isEnabled = false
        binding.buttonMuteAudio.text = "⏳ Procesando..."

        Thread {
            var extractor: MediaExtractor? = null
            var muxer: MediaMuxer? = null
            try {
                val inputFile = File(cacheDir, "mute_in_${System.currentTimeMillis()}.mp4")
                contentResolver.openInputStream(Uri.parse(uriString))?.use { inp ->
                    inputFile.outputStream().use { out -> inp.copyTo(out) }
                }
                val outputFile = File(cacheDir, "mute_out_${System.currentTimeMillis()}.mp4")

                extractor = MediaExtractor()
                extractor.setDataSource(inputFile.absolutePath)

                var videoTrack = -1
                var videoFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/") && videoTrack < 0) {
                        videoTrack = i
                        videoFormat = fmt
                    }
                }

                if (videoTrack < 0 || videoFormat == null) {
                    inputFile.delete()
                    runOnUiThread {
                        updateAudioButtons()
                        Toast.makeText(this, "Error: pista de video no encontrada", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                extractor.selectTrack(videoTrack)
                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxTrack = muxer.addTrack(videoFormat)
                try { muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION)) } catch (_: Exception) {}
                muxer.start()

                val bufferInfo = android.media.MediaCodec.BufferInfo()
                val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxTrack, buffer, bufferInfo)
                    extractor.advance()
                }

                muxer.stop(); muxer.release(); muxer = null
                extractor.release(); extractor = null
                inputFile.delete()

                // Save to MediaStore
                val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + "_muted"
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoBooth/$eventName")
                }
                val newUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
                newUri?.let { uri ->
                    contentResolver.openOutputStream(uri)?.use { out ->
                        outputFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    outputFile.delete()
                    mutedVideoUri = uri.toString()
                    currentFileUri = uri.toString()
                    audioState = AudioState.MUTED
                    currentMusicAsset = null
                    runOnUiThread {
                        updateAudioButtons()
                        reloadVideo(uri)
                    }
                } ?: run {
                    outputFile.delete()
                    runOnUiThread {
                        updateAudioButtons()
                        Toast.makeText(this, "Error al guardar video", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PreviewActivity", "buildMutedVideo error: ${e.message}", e)
                try { muxer?.stop() } catch (_: Exception) {}
                try { muxer?.release() } catch (_: Exception) {}
                try { extractor?.release() } catch (_: Exception) {}
                runOnUiThread {
                    updateAudioButtons()
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_take_another, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_yes).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_no).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun shareContent() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_share_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_email).setOnClickListener {
            dialog.dismiss()
            shareViaEmail()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_whatsapp).setOnClickListener {
            dialog.dismiss()
            shareViaWhatsApp()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_whatsapp_direct).setOnClickListener {
            dialog.dismiss()
            showWhatsAppNumberDialog()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_save_contact).setOnClickListener {
            dialog.dismiss()
            associateWithContact()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun shareViaEmail() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = if (shareIsVideo) "video/mp4" else "image/jpeg"
        
        if (type == "PHOTO") {
            if (currentFileUri != null) {
                val uri = Uri.parse(currentFileUri)
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else if (currentFilePath != null) {
                shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(currentFilePath!!)))
            }
        } else {
            currentFileUri?.let {
                val uri = Uri.parse(it)
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Foto/Video del evento: $eventName")
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Te envío este ${if (type == "PHOTO") "foto" else "video"} del evento.")
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Enviar por Email"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showWhatsAppNumberDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Número con código de país (ej: 34612345678)"
        input.inputType = android.text.InputType.TYPE_CLASS_PHONE
        input.setPadding(50, 40, 50, 40)
        
        AlertDialog.Builder(this)
            .setTitle("Enviar a WhatsApp")
            .setMessage("Ingresa el número con el código de país sin el símbolo +")
            .setView(input)
            .setPositiveButton("Enviar") { _, _ ->
                val phoneNumber = input.text.toString().trim()
                if (phoneNumber.isNotEmpty()) {
                    shareViaWhatsAppDirect(phoneNumber)
                } else {
                    Toast.makeText(this, "Número inválido", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun shareViaWhatsAppDirect(phoneNumber: String) {
        val uri = if (type == "PHOTO") {
            if (currentFileUri != null) {
                Uri.parse(currentFileUri)
            } else if (currentFilePath != null) {
                Uri.fromFile(File(currentFilePath!!))
            } else {
                Toast.makeText(this, "Error: No se encontró el archivo", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            currentFileUri?.let { Uri.parse(it) } ?: run {
                Toast.makeText(this, "Error: No se encontró el archivo", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        try {
            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(Intent.EXTRA_TEXT, "${if (type == "PHOTO") "Foto" else "Video"} del evento $eventName")
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri)
            sendIntent.type = if (shareIsVideo) "video/mp4" else "image/jpeg"
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sendIntent.setPackage("com.whatsapp")
            
            // Add phone number to open specific chat
            val whatsappUri = "https://wa.me/$phoneNumber"
            val openChatIntent = Intent(Intent.ACTION_VIEW)
            openChatIntent.data = Uri.parse(whatsappUri)
            openChatIntent.setPackage("com.whatsapp")
            
            try {
                // First open the chat
                startActivity(openChatIntent)
                // Small delay to let WhatsApp open
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startActivity(sendIntent)
                }, 1000)
            } catch (e: Exception) {
                // Try WhatsApp Business
                openChatIntent.setPackage("com.whatsapp.w4b")
                sendIntent.setPackage("com.whatsapp.w4b")
                try {
                    startActivity(openChatIntent)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startActivity(sendIntent)
                    }, 1000)
                } catch (e2: Exception) {
                    Toast.makeText(this, "WhatsApp no está instalado", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al enviar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun shareViaWhatsApp() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = if (shareIsVideo) "video/mp4" else "image/jpeg"
        
        if (type == "PHOTO") {
            if (currentFileUri != null) {
                val uri = Uri.parse(currentFileUri)
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else if (currentFilePath != null) {
                shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(currentFilePath!!)))
            }
        } else {
            currentFileUri?.let {
                val uri = Uri.parse(it)
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        
        shareIntent.putExtra(Intent.EXTRA_TEXT, "${if (type == "PHOTO") "Foto" else "Video"} del evento $eventName")
        shareIntent.setPackage("com.whatsapp")
        
        try {
            startActivity(shareIntent)
        } catch (e: Exception) {
            // Try WhatsApp Business
            shareIntent.setPackage("com.whatsapp.w4b")
            try {
                startActivity(shareIntent)
            } catch (e2: Exception) {
                // If neither work, show generic share dialog
                shareIntent.setPackage(null)
                try {
                    startActivity(Intent.createChooser(shareIntent, "Compartir por WhatsApp"))
                } catch (e3: Exception) {
                    Toast.makeText(this, "Error al compartir", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun associateWithContact() {
        val input = EditText(this)
        input.hint = "ejemplo@email.com o +34612345678"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        input.setPadding(50, 30, 50, 30)
        
        AlertDialog.Builder(this)
            .setTitle("💾 Guardar contacto")
            .setMessage("Introduce el email o teléfono.\nPodrás enviar este archivo más tarde desde la galería del evento.")
            .setView(input)
            .setPositiveButton("✓ Guardar") { _, _ ->
                val contact = input.text.toString().trim()
                if (contact.isEmpty()) {
                    Toast.makeText(this, "❌ Debes introducir un contacto", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Detect if it's email or phone
                val contactType = if (Patterns.EMAIL_ADDRESS.matcher(contact).matches()) {
                    "EMAIL"
                } else if (contact.replace(Regex("[^0-9]"), "").length >= 9) {
                    "PHONE"
                } else {
                    Toast.makeText(this, "❌ Formato inválido. Usa un email o teléfono válido", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                
                // Save association
                val manager = PendingSharesManager(this)
                val filePath = if (type == "PHOTO" && currentFilePath != null) currentFilePath else ""
                val fileUri = if (type == "VIDEO" || (type == "PHOTO" && currentFileUri != null)) {
                    currentFileUri ?: ""
                } else {
                    ""
                }
                
                manager.addPendingShare(
                    eventName = eventName,
                    filePath = filePath ?: "",
                    fileUri = fileUri ?: "",
                    fileType = type,
                    contact = contact,
                    contactType = contactType
                )
                
                Toast.makeText(this, "✓ Guardado para enviar a:\n$contact", Toast.LENGTH_LONG).show()
                
                // Show info about accessing pending shares
                AlertDialog.Builder(this)
                    .setTitle("✓ Contacto guardado")
                    .setMessage("Para enviar este archivo más tarde:\n\n1️⃣ Ve a la galería del evento\n2️⃣ Pulsa el botón 'VER ENVÍOS PENDIENTES'\n3️⃣ Envía cuando tengas conexión")
                    .setPositiveButton("Entendido") { _, _ ->
                        finish()
                    }
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private val shareIsVideo: Boolean get() = type == "VIDEO" || isShowingPhotoAsVideo

    private fun updateAudioButtons() {
        binding.buttonMuteAudio.isEnabled = true
        binding.buttonMusic.isEnabled = true
        binding.buttonMuteAudio.text = when (audioState) {
            AudioState.MUTED -> "🔊 Restaurar audio"
            else -> "🔇 Silenciar"
        }
        if (audioState == AudioState.MUSIC && currentMusicAsset != null) {
            val display = currentMusicAsset!!
                .substringBeforeLast(".")
                .replace('_', ' ').replace('-', ' ')
                .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            binding.buttonMusic.text = "🎵 $display  ✕"
        } else {
            binding.buttonMusic.text = "🎵 Añadir música"
        }
    }

    private fun showMusicPicker() {
        val musicFiles = try {
            (assets.list("music") ?: emptyArray())
                .filter { it.endsWith(".m4a") || it.endsWith(".aac") || it.endsWith(".mp3") }
        } catch (e: Exception) { emptyList() }

        if (musicFiles.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("🎵 Sin música")
                .setMessage("No hay archivos de música disponibles.\nAñade archivos .m4a en la carpeta assets/music/ de la app.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val displayNames = musicFiles.map { filename ->
            filename.substringBeforeLast(".")
                .replace('_', ' ').replace('-', ' ')
                .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("🎵 Selecciona música")
            .setItems(displayNames) { _, idx -> applyMusic(musicFiles[idx]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun applyMusic(assetName: String) {
        val cached = musicVideoUriCache[assetName]
        if (cached != null) {
            currentMusicAsset = assetName
            audioState = AudioState.MUSIC
            currentFileUri = cached
            updateAudioButtons()
            if (type == "PHOTO") {
                isShowingPhotoAsVideo = true
                binding.previewImage.visibility = View.GONE
                binding.previewVideo.visibility = View.VISIBLE
            }
            reloadVideo(Uri.parse(cached))
            return
        }
        if (type == "VIDEO") buildVideoWithMusic(assetName) else buildPhotoVideo(assetName)
    }

    private fun buildVideoWithMusic(assetName: String) {
        val uriString = originalVideoUri ?: return
        binding.buttonMusic.isEnabled = false
        binding.buttonMuteAudio.isEnabled = false
        binding.buttonMusic.text = "⏳ Procesando..."
        Thread {
            try {
                // Copy video to cache so FFmpeg can access it
                val inputFile = File(cacheDir, "vmusic_in_${System.currentTimeMillis()}.mp4")
                contentResolver.openInputStream(Uri.parse(uriString))?.use { inp ->
                    inputFile.outputStream().use { out -> inp.copyTo(out) }
                }

                // Copy music asset to cache (works with any format: mp3, aac, ogg…)
                val musicFile = File(cacheDir, "vmusic_audio_${System.currentTimeMillis()}.tmp")
                assets.open("music/$assetName").use { inp ->
                    musicFile.outputStream().use { out -> inp.copyTo(out) }
                }

                val outputFile = File(cacheDir, "vmusic_out_${System.currentTimeMillis()}.mp4")

                // -c:v copy keeps original video quality; -c:a aac transcodes any audio format to AAC
                // -shortest stops at the shorter of the two inputs
                val cmd = "-y -i \"${inputFile.absolutePath}\" -i \"${musicFile.absolutePath}\" " +
                        "-map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -b:a 192k -shortest \"${outputFile.absolutePath}\""

                val session = FFmpegKit.execute(cmd)
                inputFile.delete()
                musicFile.delete()

                if (!ReturnCode.isSuccess(session.returnCode) || !outputFile.exists() || outputFile.length() == 0L) {
                    outputFile.delete()
                    throw Exception("FFmpeg error: ${session.output?.takeLast(200)}")
                }

                val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + "_${assetName.substringBeforeLast(".")}"
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoBooth/$eventName")
                }
                val newUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
                if (newUri != null) {
                    contentResolver.openOutputStream(newUri)?.use { out ->
                        outputFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    outputFile.delete()
                    musicVideoUriCache[assetName] = newUri.toString()
                    currentMusicAsset = assetName
                    audioState = AudioState.MUSIC
                    currentFileUri = newUri.toString()
                    runOnUiThread { updateAudioButtons(); reloadVideo(newUri) }
                } else {
                    outputFile.delete()
                    runOnUiThread { updateAudioButtons(); Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                android.util.Log.e("PreviewActivity", "buildVideoWithMusic error: ${e.message}", e)
                runOnUiThread { updateAudioButtons(); Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun buildPhotoVideo(assetName: String) {
        binding.buttonMusic.isEnabled = false
        binding.buttonMusic.text = "⏳ Generando vídeo..."
        Thread {
            var encoder: MediaCodec? = null
            var musicExtractor: MediaExtractor? = null
            var muxer: MediaMuxer? = null
            try {
                val sourceBitmap: Bitmap = when {
                    currentFileUri != null -> contentResolver.openInputStream(Uri.parse(currentFileUri))
                        ?.use { BitmapFactory.decodeStream(it) }
                    currentFilePath != null -> BitmapFactory.decodeFile(currentFilePath)
                    else -> null
                } ?: throw Exception("No se pudo cargar la foto")

                val maxDim = 1080
                val scaleFactor = maxDim.toFloat() / maxOf(sourceBitmap.width, sourceBitmap.height)
                val w = ((sourceBitmap.width * scaleFactor).toInt() / 16) * 16
                val h = ((sourceBitmap.height * scaleFactor).toInt() / 16) * 16
                val scaled = if (w != sourceBitmap.width || h != sourceBitmap.height)
                    Bitmap.createScaledBitmap(sourceBitmap, w, h, true) else sourceBitmap
                val nv12 = bitmapToNV12(scaled, w, h)

                val fps = 5; val durationSecs = 15
                val totalFrames = fps * durationSecs
                val frameDurationUs = 1_000_000L / fps

                val encFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                    setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder.start()

                data class Frame(val data: ByteArray, val ptsUs: Long, val flags: Int)
                val encodedFrames = mutableListOf<Frame>()
                var outputVideoFormat: MediaFormat? = null
                val bufInfo = MediaCodec.BufferInfo()
                var inputIdx = 0; var encodingDone = false

                while (!encodingDone) {
                    if (inputIdx <= totalFrames) {
                        val inBufIdx = encoder.dequeueInputBuffer(10_000L)
                        if (inBufIdx >= 0) {
                            val inBuf = encoder.getInputBuffer(inBufIdx)!!
                            inBuf.clear()
                            if (inputIdx < totalFrames) {
                                inBuf.put(nv12)
                                encoder.queueInputBuffer(inBufIdx, 0, nv12.size, inputIdx.toLong() * frameDurationUs, 0)
                            } else {
                                encoder.queueInputBuffer(inBufIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                            inputIdx++
                        }
                    }
                    val outBufIdx = encoder.dequeueOutputBuffer(bufInfo, 10_000L)
                    when {
                        outBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputVideoFormat = encoder.outputFormat
                        outBufIdx >= 0 -> {
                            val flags = bufInfo.flags
                            if (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && bufInfo.size > 0) {
                                val outBuf = encoder.getOutputBuffer(outBufIdx)!!
                                val d = ByteArray(bufInfo.size); outBuf.get(d)
                                encodedFrames.add(Frame(d, bufInfo.presentationTimeUs, flags))
                            }
                            encoder.releaseOutputBuffer(outBufIdx, false)
                            if (flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encodingDone = true
                        }
                    }
                }
                encoder.stop(); encoder.release(); encoder = null

                val vFmt = outputVideoFormat ?: throw Exception("Error de encoder de vídeo")
                val afd: AssetFileDescriptor = assets.openFd("music/$assetName")
                musicExtractor = MediaExtractor()
                musicExtractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                var audioTrackIdx = -1
                for (i in 0 until musicExtractor.trackCount) {
                    val fmt = musicExtractor.getTrackFormat(i)
                    if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { audioTrackIdx = i; break }
                }

                val outputFile = File(cacheDir, "photo_music_${System.currentTimeMillis()}.mp4")
                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxV = muxer.addTrack(vFmt)
                val muxA = if (audioTrackIdx >= 0) {
                    musicExtractor.selectTrack(audioTrackIdx)
                    muxer.addTrack(musicExtractor.getTrackFormat(audioTrackIdx))
                } else -1
                muxer.start()

                val writeBuf = java.nio.ByteBuffer.allocate(1024 * 1024)
                val wInfo = MediaCodec.BufferInfo()
                for (f in encodedFrames) {
                    writeBuf.clear(); writeBuf.put(f.data); writeBuf.flip()
                    wInfo.offset = 0; wInfo.size = f.data.size
                    wInfo.presentationTimeUs = f.ptsUs; wInfo.flags = f.flags
                    muxer.writeSampleData(muxV, writeBuf, wInfo)
                }
                if (muxA >= 0) {
                    val aBuf = java.nio.ByteBuffer.allocate(256 * 1024)
                    val aInfo = MediaCodec.BufferInfo()
                    val maxPts = durationSecs * 1_000_000L
                    while (true) {
                        aInfo.offset = 0; aInfo.size = musicExtractor.readSampleData(aBuf, 0)
                        if (aInfo.size < 0) break
                        aInfo.presentationTimeUs = musicExtractor.sampleTime
                        if (aInfo.presentationTimeUs > maxPts) break
                        aInfo.flags = musicExtractor.sampleFlags
                        muxer.writeSampleData(muxA, aBuf, aInfo)
                        musicExtractor.advance()
                    }
                }
                muxer.stop(); muxer.release(); muxer = null
                musicExtractor.release(); musicExtractor = null

                val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + "_photo_${assetName.substringBeforeLast(".")}"
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoBooth/$eventName")
                }
                val newUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
                if (newUri != null) {
                    contentResolver.openOutputStream(newUri)?.use { out ->
                        outputFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    outputFile.delete()
                    musicVideoUriCache[assetName] = newUri.toString()
                    currentMusicAsset = assetName
                    audioState = AudioState.MUSIC
                    currentFileUri = newUri.toString()
                    isShowingPhotoAsVideo = true
                    runOnUiThread {
                        updateAudioButtons()
                        binding.previewImage.visibility = View.GONE
                        binding.previewVideo.visibility = View.VISIBLE
                        reloadVideo(newUri)
                    }
                } else {
                    outputFile.delete()
                    runOnUiThread { updateAudioButtons(); Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                android.util.Log.e("PreviewActivity", "buildPhotoVideo error: ${e.message}", e)
                try { encoder?.stop() } catch (_: Exception) {}; try { encoder?.release() } catch (_: Exception) {}
                try { muxer?.stop() } catch (_: Exception) {}; try { muxer?.release() } catch (_: Exception) {}
                try { musicExtractor?.release() } catch (_: Exception) {}
                runOnUiThread { updateAudioButtons(); Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun bitmapToNV12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val yuv = ByteArray(width * height * 3 / 2)
        val frameSize = width * height
        var yIdx = 0; var uvIdx = frameSize
        for (j in 0 until height) {
            for (i in 0 until width) {
                val p = argb[j * width + i]
                val r = (p shr 16) and 0xff; val g = (p shr 8) and 0xff; val b = p and 0xff
                yuv[yIdx++] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uvIdx++] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    yuv[uvIdx++] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }

    private fun startSpeedOscillation(mp: MediaPlayer, minSpeed: Float, maxSpeed: Float, slowDurationSecs: Float, fastDurationSecs: Float) {
        val slowDelayMs = (slowDurationSecs * 1000).toLong().coerceAtLeast(300)
        val fastDelayMs = (fastDurationSecs * 1000).toLong().coerceAtLeast(300)
        // Start with slow speed
        var inSlowPhase = true
        try {
            mp.playbackParams = mp.playbackParams.setSpeed(minSpeed)
        } catch (e: Exception) {
            android.util.Log.e("PreviewActivity", "Oscillation init error: ${e.message}")
        }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                inSlowPhase = !inSlowPhase
                val speed = if (inSlowPhase) minSpeed else maxSpeed
                val nextDelay = if (inSlowPhase) slowDelayMs else fastDelayMs
                try {
                    if (mp.isPlaying) {
                        mp.playbackParams = mp.playbackParams.setSpeed(speed)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PreviewActivity", "Oscillation step error: ${e.message}")
                }
                handler.postDelayed(this, nextDelay)
            }
        }
        // First switch happens after slow phase ends
        handler.postDelayed(runnable, slowDelayMs)
        oscillationHandler = handler
        oscillationRunnable = runnable
    }

    override fun onDestroy() {
        oscillationRunnable?.let { oscillationHandler?.removeCallbacks(it) }
        super.onDestroy()
        if (type == "PHOTO") {
            currentFilePath?.let { path ->
                File(path).delete()
            }
        }
    }
}
