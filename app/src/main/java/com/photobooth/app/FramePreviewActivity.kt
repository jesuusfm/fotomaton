package com.photobooth.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photobooth.app.databinding.ActivityFramePreviewBinding
import java.io.InputStream

class FramePreviewActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityFramePreviewBinding
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    
    private val themes = mutableListOf<Theme>()
    private val currentFrames = mutableListOf<FrameInfo>()
    private var selectedTheme: String? = null
    private var selectedFrame: FrameInfo? = null
    
    private lateinit var themeAdapter: ThemeAdapter
    private lateinit var frameAdapter: FrameAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFramePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup camera
        startCamera()
        
        // Load themes from assets
        loadThemes()
        
        // Setup RecyclerViews
        setupThemeRecycler()
        
        // Button listeners
        binding.buttonBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        
        binding.buttonFlipCamera.setOnClickListener {
            // Toggle between front and back camera
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }
        
        binding.buttonNoFrame.setOnClickListener {
            // Return with no frame selected
            val intent = Intent()
            intent.putExtra("FRAME_MODE", "none")
            intent.putExtra("FRAME_PATH", "")
            intent.putExtra("FRAME_THEME", "")
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
        
        binding.buttonConfirm.setOnClickListener {
            selectedFrame?.let { frame ->
                val intent = Intent()
                intent.putExtra("FRAME_MODE", "asset")
                intent.putExtra("FRAME_PATH", frame.path)
                intent.putExtra("FRAME_THEME", selectedTheme)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Error al iniciar cámara", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun loadThemes() {
        try {
            val themeFolders = assets.list("marcos") ?: emptyArray()
            
            themes.clear()
            for (folder in themeFolders) {
                if (folder.isNotEmpty()) {
                    val icon = getThemeIcon(folder)
                    val displayName = getThemeDisplayName(folder)
                    themes.add(Theme(folder, displayName, icon))
                }
            }
            
            if (themes.isEmpty()) {
                Toast.makeText(this, "No hay marcos disponibles", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error cargando temáticas: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getThemeIcon(themeName: String): String {
        return when (themeName.lowercase()) {
            "verano" -> "☀️"
            "navidad" -> "🎄"
            "cumpleaños" -> "🎂"
            "frio" -> "❄️"
            "comunion" -> "✝️"
            "boda" -> "💍"
            "otros" -> "🎨"
            else -> "🖼️"
        }
    }
    
    private fun getThemeDisplayName(themeName: String): String {
        return themeName.replaceFirstChar { it.uppercase() }
    }
    
    private fun setupThemeRecycler() {
        themeAdapter = ThemeAdapter(themes) { theme ->
            onThemeSelected(theme)
        }
        
        binding.themeRecycler.apply {
            layoutManager = LinearLayoutManager(this@FramePreviewActivity, RecyclerView.HORIZONTAL, false)
            adapter = themeAdapter
        }
    }
    
    private fun setupFrameRecycler() {
        frameAdapter = FrameAdapter(currentFrames) { frame ->
            onFrameSelected(frame)
        }
        
        binding.framesRecycler.apply {
            layoutManager = LinearLayoutManager(this@FramePreviewActivity, RecyclerView.HORIZONTAL, false)
            adapter = frameAdapter
        }
    }
    
    private fun onThemeSelected(theme: Theme) {
        selectedTheme = theme.folder
        binding.themeLabel.text = "Temática: ${theme.displayName}"
        
        // Load frames for this theme
        loadFramesForTheme(theme.folder)
        
        // Show frames recycler
        binding.framesTitle.visibility = View.VISIBLE
        binding.framesRecycler.visibility = View.VISIBLE
    }
    
    private fun loadFramesForTheme(themeName: String) {
        try {
            val frameFiles = assets.list("marcos/$themeName") ?: emptyArray()
            
            currentFrames.clear()
            for (file in frameFiles) {
                if (file.endsWith(".png", ignoreCase = true) || 
                    file.endsWith(".jpg", ignoreCase = true)) {
                    val path = "marcos/$themeName/$file"
                    currentFrames.add(FrameInfo(path, file))
                }
            }
            
            if (currentFrames.isEmpty()) {
                Toast.makeText(this, "No hay marcos en esta temática", Toast.LENGTH_SHORT).show()
                binding.framesTitle.visibility = View.GONE
                binding.framesRecycler.visibility = View.GONE
            } else {
                setupFrameRecycler()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error cargando marcos: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun onFrameSelected(frame: FrameInfo) {
        selectedFrame = frame
        
        // Update selection in adapter
        frameAdapter.setSelected(frame)
        
        // Show frame preview on camera
        try {
            val inputStream: InputStream = assets.open(frame.path)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            binding.frameOverlay.setImageBitmap(bitmap)
            binding.frameOverlay.visibility = View.VISIBLE
            inputStream.close()
            
            // Enable confirm button
            binding.buttonConfirm.isEnabled = true
        } catch (e: Exception) {
            Toast.makeText(this, "Error cargando marco: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Data classes
    data class Theme(
        val folder: String,
        val displayName: String,
        val icon: String
    )
    
    data class FrameInfo(
        val path: String,
        val fileName: String
    )
}
