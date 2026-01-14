package com.photobooth.app

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photobooth.app.databinding.ActivityEventNameBinding

class EventNameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEventNameBinding
    private val events = mutableListOf<EventInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventNameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request permissions at start
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, getRequiredPermissions(), REQUEST_CODE_PERMISSIONS
            )
        } else {
            loadExistingEvents()
        }

        binding.recyclerViewEvents.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewEvents.adapter = EventsAdapter(events)

        binding.buttonContinue.setOnClickListener {
            val eventName = binding.editTextEventName.text.toString().trim()
            
            if (eventName.isEmpty()) {
                Toast.makeText(this, "Por favor, introduce el nombre del evento", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!allPermissionsGranted()) {
                Toast.makeText(this, "Necesitas conceder los permisos para continuar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save event name to SharedPreferences
            val sharedPrefs = getSharedPreferences("PhotoBoothPrefs", MODE_PRIVATE)
            sharedPrefs.edit().putString("LAST_EVENT_NAME", eventName).apply()

            // Save event name and go to menu
            val intent = Intent(this, MenuActivity::class.java)
            intent.putExtra("EVENT_NAME", eventName)
            startActivity(intent)
        }
    }

    private fun loadExistingEvents() {
        val eventMap = mutableMapOf<String, EventInfo>()
        
        // Scan photos
        val photoProjection = arrayOf(
            MediaStore.Images.Media._ID,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 
                MediaStore.Images.Media.RELATIVE_PATH 
            else 
                MediaStore.Images.Media.DATA
        )
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            photoProjection,
            null,
            null,
            null
        )?.use { cursor ->
            val pathColumn = cursor.getColumnIndex(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 
                    MediaStore.Images.Media.RELATIVE_PATH 
                else 
                    MediaStore.Images.Media.DATA
            )
            
            while (cursor.moveToNext()) {
                val path = cursor.getString(pathColumn)
                val eventName = extractEventName(path)
                if (eventName != null) {
                    val info = eventMap.getOrPut(eventName) { EventInfo(eventName, 0, 0) }
                    info.photoCount++
                }
            }
        }
        
        // Scan videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 
                MediaStore.Video.Media.RELATIVE_PATH 
            else 
                MediaStore.Video.Media.DATA
        )
        
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            null
        )?.use { cursor ->
            val pathColumn = cursor.getColumnIndex(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 
                    MediaStore.Video.Media.RELATIVE_PATH 
                else 
                    MediaStore.Video.Media.DATA
            )
            
            while (cursor.moveToNext()) {
                val path = cursor.getString(pathColumn)
                val eventName = extractEventName(path)
                if (eventName != null) {
                    val info = eventMap.getOrPut(eventName) { EventInfo(eventName, 0, 0) }
                    info.videoCount++
                }
            }
        }
        
        events.clear()
        events.addAll(eventMap.values.sortedByDescending { it.photoCount + it.videoCount })
        binding.recyclerViewEvents.adapter?.notifyDataSetChanged()
    }
    
    private fun extractEventName(path: String): String? {
        // Extract event name from path like "Pictures/PhotoBooth/EventName/" or full path
        val photoBooth = path.indexOf("PhotoBooth")
        if (photoBooth == -1) return null
        
        val afterPhotoBoooth = path.substring(photoBooth + "PhotoBooth/".length)
        val endIndex = afterPhotoBoooth.indexOf('/')
        
        return if (endIndex > 0) {
            afterPhotoBoooth.substring(0, endIndex)
        } else null
    }

    data class EventInfo(val name: String, var photoCount: Int, var videoCount: Int)
    
    inner class EventsAdapter(private val events: List<EventInfo>) : 
        RecyclerView.Adapter<EventsAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textEventName: TextView = view.findViewById(R.id.text_event_name)
            val textFileCount: TextView = view.findViewById(R.id.text_file_count)
            val buttonViewGallery: Button = view.findViewById(R.id.button_view_gallery)
            val buttonUseEvent: Button = view.findViewById(R.id.button_use_event)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_event, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val event = events[position]
            holder.textEventName.text = event.name
            holder.textFileCount.text = "${event.photoCount} fotos, ${event.videoCount} videos"
            
            holder.buttonViewGallery.setOnClickListener {
                val intent = Intent(this@EventNameActivity, EventGalleryActivity::class.java)
                intent.putExtra("EVENT_NAME", event.name)
                startActivity(intent)
            }
            
            holder.buttonUseEvent.setOnClickListener {
                // Save event name to SharedPreferences
                val sharedPrefs = getSharedPreferences("PhotoBoothPrefs", MODE_PRIVATE)
                sharedPrefs.edit().putString("LAST_EVENT_NAME", event.name).apply()
                
                // Go to menu with this event
                val intent = Intent(this@EventNameActivity, MenuActivity::class.java)
                intent.putExtra("EVENT_NAME", event.name)
                startActivity(intent)
            }
        }
        
        override fun getItemCount() = events.size
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        }
        
        return permissions.toTypedArray()
    }

    private fun allPermissionsGranted() = getRequiredPermissions().all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                loadExistingEvents()
            } else {
                Toast.makeText(
                    this,
                    "Necesitas conceder todos los permisos (Cámara, Micrófono, Archivos) para usar la app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            loadExistingEvents()
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}
