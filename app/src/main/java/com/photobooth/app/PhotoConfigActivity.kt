package com.photobooth.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.photobooth.app.databinding.ActivityPhotoConfigBinding

class PhotoConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoConfigBinding
    private lateinit var eventName: String
    private var filterMode = "normal"
    private var isPhotoBoothMode = false
    private var frameMode = "none"
    private var framePath = ""
    private var backgroundMode = "none"
    private var backgroundPath = ""
    private var removeBackground = false

    private val filterOptions = arrayOf(
        "✨ Normal",
        "⬜ Blanco y Negro",
        "🟤 Sepia (Vintage)",
        "📷 Retro",
        "⚡ Alto Contraste",
        "🌈 Invertir Colores",
        "❄️ Tonos Fríos"
    )
    
    private val filterModes = arrayOf(
        "normal",
        "bw",
        "sepia",
        "vintage",
        "contrast",
        "invert",
        "cold"
    )
    
    // Frame preview launcher
    private val framePreviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                frameMode = data.getStringExtra("FRAME_MODE") ?: "none"
                framePath = data.getStringExtra("FRAME_PATH") ?: ""
                val theme = data.getStringExtra("FRAME_THEME") ?: ""
                
                if (frameMode != "none") {
                    binding.frameStatus.text = "✓ Marco: $theme"
                    binding.frameStatus.visibility = View.VISIBLE
                } else {
                    binding.frameStatus.text = ""
                    binding.frameStatus.visibility = View.GONE
                }
            }
        }
    }
    
    // Background preview launcher
    private val backgroundPreviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                backgroundMode = data.getStringExtra("BACKGROUND_MODE") ?: "none"
                backgroundPath = data.getStringExtra("BACKGROUND_PATH") ?: ""
                val theme = data.getStringExtra("BACKGROUND_THEME") ?: ""
                
                if (backgroundMode != "none") {
                    binding.backgroundStatus.text = "✓ Fondo: $theme"
                    binding.backgroundStatus.visibility = View.VISIBLE
                    removeBackground = true
                } else {
                    binding.backgroundStatus.text = ""
                    binding.backgroundStatus.visibility = View.GONE
                    removeBackground = false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eventName = intent.getStringExtra("EVENT_NAME") ?: "Evento"

        // Setup filter spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilter.adapter = adapter
        
        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterMode = filterModes[position]
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                filterMode = "normal"
            }
        }
        
        // Frame selection button
        binding.buttonSelectFrame.setOnClickListener {
            val intent = Intent(this, FramePreviewActivity::class.java)
            framePreviewLauncher.launch(intent)
        }
        
        // Background selection button
        binding.buttonSelectBackground.setOnClickListener {
            val intent = Intent(this, BackgroundPreviewActivity::class.java)
            backgroundPreviewLauncher.launch(intent)
        }

        // Photo booth mode
        binding.switchPhotoBooth.setOnCheckedChangeListener { _, isChecked ->
            isPhotoBoothMode = isChecked
        }
        
        binding.buttonStart.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra("EVENT_NAME", eventName)
            intent.putExtra("MODE", "PHOTO")
            intent.putExtra("FILTER", filterMode)
            intent.putExtra("PHOTO_BOOTH", isPhotoBoothMode)
            intent.putExtra("FRAME", frameMode)
            intent.putExtra("FRAME_PATH", framePath)
            intent.putExtra("BACKGROUND", backgroundMode)
            intent.putExtra("BACKGROUND_PATH", backgroundPath)
            intent.putExtra("REMOVE_BG", removeBackground)
            startActivity(intent)
        }
    }
}
