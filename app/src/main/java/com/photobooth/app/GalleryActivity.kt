package com.photobooth.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.photobooth.app.databinding.ActivityGalleryBinding

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uris = intent.getStringArrayExtra("MEDIA_URIS") ?: emptyArray()
        val types = intent.getStringArrayExtra("MEDIA_TYPES") ?: emptyArray()
        val eventName = intent.getStringExtra("EVENT_NAME") ?: ""

        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = GalleryAdapter(uris.toList(), types.toList()) { uri, type ->
            val intent = Intent(this, PreviewActivity::class.java)
            if (type == "VIDEO") {
                intent.putExtra("VIDEO_URI", uri)
                intent.putExtra("TYPE", "VIDEO")
                intent.putExtra("SLOW_MOTION_MODE", "normal")
            } else {
                intent.putExtra("IMAGE_URI", uri)
                intent.putExtra("TYPE", "PHOTO")
            }
            intent.putExtra("EVENT_NAME", eventName)
            startActivity(intent)
        }

        binding.buttonClose.setOnClickListener { finish() }
    }
}
