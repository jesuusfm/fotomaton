package com.photobooth.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.photobooth.app.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMenuBinding
    private lateinit var eventName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eventName = intent.getStringExtra("EVENT_NAME") ?: "Evento"
        binding.eventNameTitle.text = eventName

        binding.buttonPhotoMode.setOnClickListener {
            val intent = Intent(this, PhotoConfigActivity::class.java)
            intent.putExtra("EVENT_NAME", eventName)
            startActivity(intent)
        }

        binding.buttonVideoMode.setOnClickListener {
            val intent = Intent(this, VideoConfigActivity::class.java)
            intent.putExtra("EVENT_NAME", eventName)
            startActivity(intent)
        }
    }
}
