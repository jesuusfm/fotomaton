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
    private var slowMotionMode = "normal" // normal, 0.5x, boomerang, boomerang_reverse
    private var boomerangMinSpeed = 0.5f
    private var boomerangMaxSpeed = 1.0f
    private var boomerangSlowDuration = 3.0f  // seconds at slow speed
    private var boomerangFastDuration = 3.0f  // seconds at fast speed
    private var filterMode = "normal"
    private var videoQuality = "UHD" // SD, HD, FHD, UHD (4K)
    private var frameMode = "none"
    private var framePath = ""
    private var backgroundMode = "none"
    private var backgroundPath = ""
    private var removeBackground = false
    private var cameraSource = "phone" // "phone" or "usb"
    private var usbVerticalMode = false    
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

        // Camera source selector
        binding.chipGroupCameraSource.setOnCheckedStateChangeListener { _, checkedIds ->
            cameraSource = if (checkedIds.contains(R.id.chip_usb_camera)) "usb" else "phone"
            binding.switchUsbVertical.visibility = if (cameraSource == "usb") View.VISIBLE else View.GONE
        }

        binding.switchUsbVertical.setOnCheckedChangeListener { _, isChecked ->
            usbVerticalMode = isChecked
        }

        // Slow motion mode selection
        binding.radioGroupSlowMotion.setOnCheckedChangeListener { _, checkedId ->
            slowMotionMode = when(checkedId) {
                R.id.radio_slow_05x -> "0.5x"
                R.id.radio_boomerang -> "boomerang"
                R.id.radio_boomerang_reverse -> "boomerang_reverse"
                else -> "normal"
            }
            // Show/hide boomerang parameters and update title
            if (slowMotionMode == "boomerang" || slowMotionMode == "boomerang_reverse") {
                binding.boomerangParams.visibility = android.view.View.VISIBLE
                binding.boomerangParamsTitle.text = if (slowMotionMode == "boomerang") {
                    "Parámetros Oscilante"
                } else {
                    "Parámetros Boomerang"
                }
            } else {
                binding.boomerangParams.visibility = android.view.View.GONE
            }
        }

        // Boomerang parameters
        // Min speed: 0.1x to 3.0x, step 0.1x, seekbar max=29
        binding.seekBarMinSpeed.max = 29
        binding.seekBarMinSpeed.progress = 4 // default 0.5x
        binding.minSpeedValue.text = "0.50x"
        binding.seekBarMinSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                boomerangMinSpeed = 0.1f + progress * 0.1f // 0.1 - 3.0
                binding.minSpeedValue.text = "%.2fx".format(boomerangMinSpeed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Max speed: 0.1x to 3.0x, step 0.1x, seekbar max=29
        binding.seekBarMaxSpeed.max = 29
        binding.seekBarMaxSpeed.progress = 9 // default 1.0x
        binding.maxSpeedValue.text = "1.00x"
        binding.seekBarMaxSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                boomerangMaxSpeed = 0.1f + progress * 0.1f // 0.1 - 3.0
                binding.maxSpeedValue.text = "%.2fx".format(boomerangMaxSpeed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Slow duration: 0.5s to 10s, step 0.5s, seekbar max=19
        binding.seekBarFrequency.max = 19
        binding.seekBarFrequency.progress = 5 // default 3.0s
        binding.frequencyValue.text = "3.0s"
        boomerangSlowDuration = 3.0f
        binding.seekBarFrequency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                boomerangSlowDuration = 0.5f + progress * 0.5f // 0.5s - 10s
                binding.frequencyValue.text = "%.1fs".format(boomerangSlowDuration)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Fast duration: 0.5s to 10s, step 0.5s, seekbar max=19
        binding.seekBarFastDuration.max = 19
        binding.seekBarFastDuration.progress = 5 // default 3.0s
        binding.fastDurationValue.text = "3.0s"
        boomerangFastDuration = 3.0f
        binding.seekBarFastDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                boomerangFastDuration = 0.5f + progress * 0.5f // 0.5s - 10s
                binding.fastDurationValue.text = "%.1fs".format(boomerangFastDuration)
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
        
        binding.buttonStart.setOnClickListener {
            android.util.Log.d("VideoConfigActivity", "=== INICIANDO CÁMARA ===")
            android.util.Log.d("VideoConfigActivity", "videoQuality=$videoQuality")
            
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra("EVENT_NAME", eventName)
            intent.putExtra("MODE", "VIDEO")
            intent.putExtra("FILTER", filterMode)
            intent.putExtra("VIDEO_QUALITY", videoQuality)
            intent.putExtra("VIDEO_DURATION", videoDuration)
            intent.putExtra("SLOW_MOTION_MODE", slowMotionMode)
            intent.putExtra("BOOMERANG_MIN_SPEED", boomerangMinSpeed)
            intent.putExtra("BOOMERANG_MAX_SPEED", boomerangMaxSpeed)
            intent.putExtra("BOOMERANG_SLOW_DURATION", boomerangSlowDuration)
            intent.putExtra("BOOMERANG_FAST_DURATION", boomerangFastDuration)
            intent.putExtra("FRAME", frameMode)
            intent.putExtra("FRAME_PATH", framePath)
            intent.putExtra("BACKGROUND", backgroundMode)
            intent.putExtra("BACKGROUND_PATH", backgroundPath)
            intent.putExtra("REMOVE_BG", removeBackground)
            intent.putExtra("CAMERA_SOURCE", cameraSource)
            intent.putExtra("USB_VERTICAL_MODE", usbVerticalMode)
            startActivity(intent)
        }
    }
}
