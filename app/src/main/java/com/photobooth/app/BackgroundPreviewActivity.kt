package com.photobooth.app

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photobooth.app.databinding.ActivityBackgroundPreviewBinding
import java.io.InputStream

class BackgroundPreviewActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityBackgroundPreviewBinding
    
    private val themes = mutableListOf<FramePreviewActivity.Theme>()
    private val currentBackgrounds = mutableListOf<BackgroundInfo>()
    private var selectedTheme: String? = null
    private var selectedBackground: BackgroundInfo? = null
    
    private lateinit var themeAdapter: ThemeAdapter
    private lateinit var backgroundAdapter: BackgroundAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackgroundPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Load themes from assets
        loadThemes()
        
        // Setup RecyclerViews
        setupThemeRecycler()
        
        // Button listeners
        binding.buttonBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        
        binding.buttonNoBackground.setOnClickListener {
            // Return with no background selected
            val intent = Intent()
            intent.putExtra("BACKGROUND_MODE", "none")
            intent.putExtra("BACKGROUND_PATH", "")
            intent.putExtra("BACKGROUND_THEME", "")
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
        
        binding.buttonConfirm.setOnClickListener {
            selectedBackground?.let { bg ->
                val intent = Intent()
                intent.putExtra("BACKGROUND_MODE", "asset")
                intent.putExtra("BACKGROUND_PATH", bg.path)
                intent.putExtra("BACKGROUND_THEME", selectedTheme)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }
    }
    
    private fun loadThemes() {
        try {
            val themeFolders = assets.list("fondos") ?: emptyArray()
            
            themes.clear()
            for (folder in themeFolders) {
                if (folder.isNotEmpty()) {
                    val icon = getThemeIcon(folder)
                    val displayName = getThemeDisplayName(folder)
                    themes.add(FramePreviewActivity.Theme(folder, displayName, icon))
                }
            }
            
            if (themes.isEmpty()) {
                Toast.makeText(this, "No hay fondos disponibles", Toast.LENGTH_LONG).show()
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
            layoutManager = LinearLayoutManager(this@BackgroundPreviewActivity, RecyclerView.HORIZONTAL, false)
            adapter = themeAdapter
        }
    }
    
    private fun setupBackgroundRecycler() {
        backgroundAdapter = BackgroundAdapter(currentBackgrounds, assets) { bg ->
            onBackgroundSelected(bg)
        }
        
        binding.backgroundsRecycler.apply {
            layoutManager = LinearLayoutManager(this@BackgroundPreviewActivity, RecyclerView.HORIZONTAL, false)
            adapter = backgroundAdapter
        }
    }
    
    private fun onThemeSelected(theme: FramePreviewActivity.Theme) {
        selectedTheme = theme.folder
        binding.themeLabel.text = "Temática: ${theme.displayName}"
        
        // Load backgrounds for this theme
        loadBackgroundsForTheme(theme.folder)
        
        // Show backgrounds recycler
        binding.backgroundsTitle.visibility = View.VISIBLE
        binding.backgroundsRecycler.visibility = View.VISIBLE
    }
    
    private fun loadBackgroundsForTheme(themeName: String) {
        try {
            val bgFiles = assets.list("fondos/$themeName") ?: emptyArray()
            
            currentBackgrounds.clear()
            for (file in bgFiles) {
                if (file.endsWith(".png", ignoreCase = true) || 
                    file.endsWith(".jpg", ignoreCase = true) ||
                    file.endsWith(".jpeg", ignoreCase = true)) {
                    val path = "fondos/$themeName/$file"
                    currentBackgrounds.add(BackgroundInfo(path, file))
                }
            }
            
            if (currentBackgrounds.isEmpty()) {
                Toast.makeText(this, "No hay fondos en esta temática", Toast.LENGTH_SHORT).show()
                binding.backgroundsTitle.visibility = View.GONE
                binding.backgroundsRecycler.visibility = View.GONE
            } else {
                setupBackgroundRecycler()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error cargando fondos: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun onBackgroundSelected(bg: BackgroundInfo) {
        selectedBackground = bg
        
        // Update selection in adapter
        backgroundAdapter.setSelected(bg)
        
        // Show background preview
        try {
            val inputStream: InputStream = assets.open(bg.path)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            binding.backgroundPreview.setImageBitmap(bitmap)
            binding.backgroundPreview.visibility = View.VISIBLE
            binding.previewHint.visibility = View.GONE
            inputStream.close()
            
            // Enable confirm button
            binding.buttonConfirm.isEnabled = true
        } catch (e: Exception) {
            Toast.makeText(this, "Error cargando fondo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Data class for background info
    data class BackgroundInfo(
        val path: String,
        val fileName: String
    )
}
