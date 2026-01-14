package com.photobooth.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.photobooth.app.databinding.ActivityVideoConfigBinding

class VideoConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoConfigBinding
    private lateinit var eventName: String
    private var videoDuration = 10 // seconds
    private var slowMotionMode = "normal" // normal, 0.5x, boomerang
    private var boomerangMinSpeed = 0.5f // 0.3 - 0.8
    private var boomerangMaxSpeed = 1.0f // 0.5 - 1.5
    private var boomerangFrequency = 0.1f // 0.05 - 0.30
    private var filterMode = "normal"
    private var frameMode = "none"
    private var framePath = ""
    private var backgroundMode = "none"
    private var backgroundPath = ""
    private var removeBackground = false
    private var faceFilterType = "none"
    private var faceFilterPath = ""
    
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
                    binding.frameStatus.text = "✓ Marco seleccionado: $theme"
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
                    // Auto-enable background removal
                    removeBackground = true
                    binding.switchRemoveBackground.isChecked = true
                } else {
                    binding.backgroundStatus.text = ""
                    binding.backgroundStatus.visibility = View.GONE
                    removeBackground = false
                    binding.switchRemoveBackground.isChecked = false
                }
            }
        }
    }
    
    // Face filter launcher
    private val faceFilterLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                faceFilterType = data.getStringExtra("FILTER_TYPE") ?: "none"
                faceFilterPath = data.getStringExtra("FILTER_PATH") ?: ""
                val filterName = data.getStringExtra("FILTER_NAME") ?: ""
                
                if (faceFilterType != "none") {
                    binding.faceFilterStatus.text = "✓ Filtro facial: $filterName"
                    binding.faceFilterStatus.visibility = View.VISIBLE
                } else {
                    binding.faceFilterStatus.text = ""
                    binding.faceFilterStatus.visibility = View.GONE
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eventName = intent.getStringExtra("EVENT_NAME") ?: "Evento"

        // Filter spinner
        val filterOptions = arrayOf(
            "✨ Normal",
            "⬜ Blanco y Negro",
            "🟤 Sepia",
            "📷 Retro",
            "⚡ Alto Contraste",
            "🌈 Invertir",
            "❄️ Tonos Fríos"
        )
        
        val filterModes = arrayOf(
            "normal",
            "bw",
            "sepia",
            "vintage",
            "contrast",
            "invert",
            "cold"
        )
        
        val filterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilter.adapter = filterAdapter
        
        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterMode = filterModes[position]
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                filterMode = "normal"
            }
        }

        // Duration slider
        binding.seekBarDuration.max = 25 // 5-30 seconds
        binding.seekBarDuration.progress = 5 // default 10 seconds
        binding.durationValue.text = "10 segundos"

        binding.seekBarDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                videoDuration = progress + 5 // 5-30 range
                binding.durationValue.text = "$videoDuration segundos"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Slow motion mode selection
        binding.radioGroupSlowMotion.setOnCheckedChangeListener { _, checkedId ->
            slowMotionMode = when(checkedId) {
                R.id.radio_slow_05x -> "0.5x"
                R.id.radio_boomerang -> "boomerang"
                else -> "normal"
            }
            // Show/hide boomerang parameters
            binding.boomerangParams.visibility = if (slowMotionMode == "boomerang") {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        // Boomerang parameters
        binding.seekBarMinSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                boomerangMinSpeed = 0.3f + (progress / 100f) // 0.3 - 0.8
                binding.minSpeedValue.text = "%.2fx".format(boomerangMinSpeed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBarMaxSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                boomerangMaxSpeed = 0.5f + (progress / 50f) // 0.5 - 1.5
                binding.maxSpeedValue.text = "%.2fx".format(boomerangMaxSpeed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBarFrequency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                boomerangFrequency = 0.05f + (progress / 100f) // 0.05 - 0.30
                binding.frequencyValue.text = "%.2f".format(boomerangFrequency)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
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
        
        // Face filter selection button
        binding.buttonSelectFaceFilter.setOnClickListener {
            val intent = Intent(this, FaceFilterActivity::class.java)
            faceFilterLauncher.launch(intent)
        }
        
        // Remove background switch
        binding.switchRemoveBackground.setOnCheckedChangeListener { _, isChecked ->
            removeBackground = isChecked
        }

        binding.buttonStart.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra("EVENT_NAME", eventName)
            intent.putExtra("MODE", "VIDEO")
            intent.putExtra("FILTER", filterMode)
            intent.putExtra("VIDEO_DURATION", videoDuration)
            intent.putExtra("SLOW_MOTION_MODE", slowMotionMode)
            intent.putExtra("BOOMERANG_MIN_SPEED", boomerangMinSpeed)
            intent.putExtra("BOOMERANG_MAX_SPEED", boomerangMaxSpeed)
            intent.putExtra("BOOMERANG_FREQUENCY", boomerangFrequency)
            intent.putExtra("FRAME", frameMode)
            intent.putExtra("FRAME_PATH", framePath)
            intent.putExtra("BACKGROUND", backgroundMode)
            intent.putExtra("BACKGROUND_PATH", backgroundPath)
            intent.putExtra("REMOVE_BG", removeBackground)
            intent.putExtra("FACE_FILTER_TYPE", faceFilterType)
            intent.putExtra("FACE_FILTER_PATH", faceFilterPath)
            startActivity(intent)
        }
    }
}
