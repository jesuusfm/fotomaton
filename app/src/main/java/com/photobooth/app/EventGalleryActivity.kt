package com.photobooth.app

import android.content.ContentUris
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.photobooth.app.databinding.ActivityEventGalleryBinding
import java.io.File

class EventGalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEventGalleryBinding
    private lateinit var eventName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eventName = intent.getStringExtra("EVENT_NAME") ?: "Evento"
        
        binding.titleText.text = eventName
        binding.buttonBack.setOnClickListener {
            finish()
        }

        binding.buttonPendingShares.setOnClickListener {
            val intent = Intent(this, PendingSharesActivity::class.java)
            intent.putExtra("EVENT_NAME", eventName)
            startActivity(intent)
        }

        loadEventMedia()
    }

    override fun onResume() {
        super.onResume()
        updatePendingCount()
    }

    private fun updatePendingCount() {
        val manager = PendingSharesManager(this)
        val count = manager.getPendingCount(eventName)
        binding.buttonPendingShares.text = "✉️ VER ENVÍOS PENDIENTES ($count)"
        
        // Highlight button if there are pending shares
        if (count > 0) {
            binding.buttonPendingShares.setBackgroundColor(getColor(android.R.color.holo_orange_light))
        }
    }

    private fun loadEventMedia() {
        val mediaItems = mutableListOf<MediaItem>()
        
        // Load photos
        val photoProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        
        val photoSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Images.Media.DATA} LIKE ?"
        }
        
        val photoSelectionArgs = arrayOf("%PhotoBooth/$eventName%")
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            photoProjection,
            photoSelection,
            photoSelectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                mediaItems.add(MediaItem(uri.toString(), name, "photo"))
            }
        }
        
        // Load videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        )
        
        val videoSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Video.Media.DATA} LIKE ?"
        }
        
        val videoSelectionArgs = arrayOf("%PhotoBooth/$eventName%")
        
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            videoSelection,
            videoSelectionArgs,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                mediaItems.add(MediaItem(uri.toString(), name, "video"))
            }
        }
        
        binding.mediaCount.text = "${mediaItems.size} archivos"
        binding.recyclerViewMedia.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerViewMedia.adapter = MediaAdapter(mediaItems)
    }
    
    data class MediaItem(val uri: String, val name: String, val type: String)
    
    inner class MediaAdapter(private val items: List<MediaItem>) : 
        RecyclerView.Adapter<MediaAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.image_thumbnail)
            val typeIndicator: TextView = view.findViewById(R.id.type_indicator)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            Glide.with(holder.itemView.context)
                .load(item.uri)
                .centerCrop()
                .into(holder.imageView)
            
            holder.typeIndicator.text = if (item.type == "video") "🎥" else ""
            
            holder.itemView.setOnClickListener {
                // TODO: Open full screen preview
            }
        }
        
        override fun getItemCount() = items.size
    }
}
