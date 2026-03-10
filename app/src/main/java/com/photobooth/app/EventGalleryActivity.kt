package com.photobooth.app

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.photobooth.app.databinding.ActivityEventGalleryBinding
import java.io.File

class EventGalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEventGalleryBinding
    private lateinit var eventName: String
    private val mediaItems = mutableListOf<MediaItem>()
    private var currentFilter = "all"
    private var pendingDeleteItem: MediaItem? = null

    private val deleteItemLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val item = pendingDeleteItem ?: return@registerForActivityResult
        pendingDeleteItem = null
        if (result.resultCode == RESULT_OK) {
            mediaItems.remove(item)
            filterAndDisplay()
            Toast.makeText(this, "Archivo eliminado", Toast.LENGTH_SHORT).show()
        }
    }

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

        binding.chipAll.setOnCheckedChangeListener { _, checked ->
            if (checked) { currentFilter = "all"; filterAndDisplay() }
        }
        binding.chipPhotos.setOnCheckedChangeListener { _, checked ->
            if (checked) { currentFilter = "photo"; filterAndDisplay() }
        }
        binding.chipVideos.setOnCheckedChangeListener { _, checked ->
            if (checked) { currentFilter = "video"; filterAndDisplay() }
        }
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

    private fun shareMediaItem(item: MediaItem) {
        val uri = Uri.parse(item.uri)
        val mimeType = if (item.type == "video") "video/*" else "image/*"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir"))
    }

    private fun confirmDeleteItem(item: MediaItem) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar archivo")
            .setMessage("¿Eliminar '${item.name}'?")
            .setPositiveButton("Eliminar") { _, _ -> deleteMediaItem(item) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteMediaItem(item: MediaItem) {
        val uri = Uri.parse(item.uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pendingDeleteItem = item
            val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
            deleteItemLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
        } else {
            contentResolver.delete(uri, null, null)
            mediaItems.remove(item)
            filterAndDisplay()
            Toast.makeText(this, "Archivo eliminado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun filterAndDisplay() {
        val filtered = if (currentFilter == "all") mediaItems
                       else mediaItems.filter { it.type == currentFilter }
        val label = when (currentFilter) {
            "photo" -> "fotos"
            "video" -> "vídeos"
            else -> "archivos"
        }
        binding.mediaCount.text = "${filtered.size} $label"
        binding.recyclerViewMedia.adapter = MediaAdapter(filtered)
    }

    private fun loadEventMedia() {
        mediaItems.clear()
        
        // Load photos
        val photoProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
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
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                mediaItems.add(MediaItem(uri.toString(), name, "photo", dateAdded))
            }
        }
        
        // Load videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED
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
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                mediaItems.add(MediaItem(uri.toString(), name, "video", dateAdded))
            }
        }
        
        mediaItems.sortByDescending { it.dateAdded }

        binding.recyclerViewMedia.layoutManager = GridLayoutManager(this, 3)
        filterAndDisplay()
    }
    
    data class MediaItem(val uri: String, val name: String, val type: String, val dateAdded: Long)
    
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

            holder.itemView.setOnLongClickListener {
                val options = arrayOf("📤 Compartir", "🗑️ Eliminar")
                AlertDialog.Builder(this@EventGalleryActivity)
                    .setTitle(item.name)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> shareMediaItem(item)
                            1 -> confirmDeleteItem(item)
                        }
                    }
                    .show()
                true
            }
        }
        
        override fun getItemCount() = items.size
    }
}
