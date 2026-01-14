package com.photobooth.app

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photobooth.app.databinding.ActivityGalleryBinding

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val photoCount = intent.getIntExtra("PHOTO_COUNT", 0)
        val bitmaps = (0 until photoCount).mapNotNull { index ->
            intent.getByteArrayExtra("PHOTO_$index")?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }

        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = GalleryAdapter(bitmaps)

        binding.buttonClose.setOnClickListener {
            finish()
        }
    }
}
