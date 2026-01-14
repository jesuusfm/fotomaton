package com.photobooth.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PendingSharesActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var textCount: TextView
    private lateinit var textEmpty: TextView
    private lateinit var textSubtitle: TextView
    private lateinit var manager: PendingSharesManager
    private lateinit var eventName: String
    private var shares = listOf<PendingShare>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("PendingShares", "onCreate START")
        
        setContentView(R.layout.activity_pending_shares)
        android.util.Log.d("PendingShares", "Layout set")
        
        eventName = intent.getStringExtra("EVENT_NAME") ?: ""
        android.util.Log.d("PendingShares", "Event name: $eventName")
        
        manager = PendingSharesManager(this)
        
        recyclerView = findViewById(R.id.recyclerPendingShares)
        textCount = findViewById(R.id.textCount)
        textEmpty = findViewById(R.id.textEmpty)
        textSubtitle = findViewById(R.id.textSubtitle)
        android.util.Log.d("PendingShares", "Views found")
        
        textSubtitle.text = "Evento: $eventName"
        
        findViewById<Button>(R.id.buttonBack).setOnClickListener {
            android.util.Log.d("PendingShares", "Back button clicked")
            finish()
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        android.util.Log.d("PendingShares", "LayoutManager set")
        
        // Log RecyclerView dimensions after layout
        recyclerView.post {
            android.util.Log.d("PendingShares", "RecyclerView dimensions: width=${recyclerView.width}, height=${recyclerView.height}")
        }
        
        loadPendingShares()
        android.util.Log.d("PendingShares", "onCreate END")
    }
    
    override fun onResume() {
        super.onResume()
        loadPendingShares()
    }
    
    private fun loadPendingShares() {
        shares = if (eventName.isEmpty()) {
            manager.getAllPendingShares()
        } else {
            manager.getPendingSharesByEvent(eventName)
        }
        
        android.util.Log.d("PendingShares", "Event: $eventName, Total shares: ${shares.size}")
        shares.forEach {
            android.util.Log.d("PendingShares", "Share: ${it.contact}, type: ${it.fileType}, sent: ${it.sent}")
        }
        
        val pendingCount = shares.count { !it.sent }
        textCount.text = "$pendingCount pendiente${if (pendingCount != 1) "s" else ""}"
        
        if (shares.isEmpty()) {
            android.util.Log.d("PendingShares", "No shares, showing empty message")
            recyclerView.visibility = View.GONE
            textEmpty.visibility = View.VISIBLE
        } else {
            android.util.Log.d("PendingShares", "Showing ${shares.size} shares")
            recyclerView.visibility = View.VISIBLE
            textEmpty.visibility = View.GONE
            recyclerView.adapter = PendingSharesAdapter(shares)
        }
    }
    
    private inner class PendingSharesAdapter(
        private val shares: List<PendingShare>
    ) : RecyclerView.Adapter<PendingSharesAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageThumbnail: ImageView = view.findViewById(R.id.imageThumbnail)
            val textContact: TextView = view.findViewById(R.id.textContact)
            val textType: TextView = view.findViewById(R.id.textType)
            val textDate: TextView = view.findViewById(R.id.textDate)
            val textStatus: TextView = view.findViewById(R.id.textStatus)
            val buttonSend: Button = view.findViewById(R.id.buttonSend)
            val buttonDelete: Button = view.findViewById(R.id.buttonDelete)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            android.util.Log.d("PendingShares", "onCreateViewHolder called")
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pending_share, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val share = shares[position]
            
            android.util.Log.d("PendingShares", "Binding item $position: contact=${share.contact}, fileUri='${share.fileUri}', filePath='${share.filePath}'")
            
            // Load thumbnail
            if (share.fileType == "PHOTO") {
                if (share.fileUri.isNotEmpty()) {
                    // Use URI (preferred)
                    android.util.Log.d("PendingShares", "Loading photo from URI: ${share.fileUri}")
                    Glide.with(holder.imageThumbnail.context)
                        .load(Uri.parse(share.fileUri))
                        .centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_dialog_alert)
                        .into(holder.imageThumbnail)
                } else if (share.filePath.isNotEmpty()) {
                    // Fallback to path
                    android.util.Log.d("PendingShares", "Loading photo from path: ${share.filePath}")
                    Glide.with(holder.imageThumbnail.context)
                        .load(File(share.filePath))
                        .centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_dialog_alert)
                        .into(holder.imageThumbnail)
                } else {
                    android.util.Log.e("PendingShares", "No URI or path for photo!")
                }
            } else {
                if (share.fileUri.isNotEmpty()) {
                    android.util.Log.d("PendingShares", "Loading video from URI: ${share.fileUri}")
                    Glide.with(holder.imageThumbnail.context)
                        .load(Uri.parse(share.fileUri))
                        .centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_dialog_alert)
                        .into(holder.imageThumbnail)
                } else {
                    android.util.Log.e("PendingShares", "No URI for video!")
                }
            }
            
            holder.textContact.text = share.contact
            holder.textType.text = if (share.fileType == "PHOTO") "📷 Foto" else "🎥 Video"
            
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            holder.textDate.text = dateFormat.format(Date(share.timestamp))
            
            if (share.sent) {
                holder.textStatus.visibility = View.VISIBLE
                holder.buttonSend.isEnabled = false
                holder.buttonSend.alpha = 0.5f
            } else {
                holder.textStatus.visibility = View.GONE
                holder.buttonSend.isEnabled = true
                holder.buttonSend.alpha = 1f
            }
            
            holder.buttonSend.setOnClickListener {
                sendShare(share)
            }
            
            holder.buttonDelete.setOnClickListener {
                confirmDelete(share)
            }
        }
        
        override fun getItemCount(): Int {
            android.util.Log.d("PendingShares", "getItemCount: ${shares.size}")
            return shares.size
        }
    }
    
    private fun sendShare(share: PendingShare) {
        val options = arrayOf(
            "📧 Enviar por Email",
            "💬 Enviar por WhatsApp"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Seleccionar método de envío")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> sendViaEmail(share)
                    1 -> sendViaWhatsApp(share)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun sendViaEmail(share: PendingShare) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = if (share.fileType == "PHOTO") "image/jpeg" else "video/mp4"
        
        if (share.fileUri.isNotEmpty()) {
            // Use URI (preferred)
            val uri = Uri.parse(share.fileUri)
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else if (share.filePath.isNotEmpty()) {
            // Fallback to path
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(share.filePath)))
        }
        
        shareIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(share.contact))
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Foto/Video del evento: ${share.eventName}")
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Te envío este ${if (share.fileType == "PHOTO") "foto" else "video"} del evento.")
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Enviar por Email"))
            manager.markAsSent(share.id)
            Toast.makeText(this, "✓ Marcado como enviado", Toast.LENGTH_SHORT).show()
            loadPendingShares()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendViaWhatsApp(share: PendingShare) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = if (share.fileType == "PHOTO") "image/jpeg" else "video/mp4"
        
        if (share.fileUri.isNotEmpty()) {
            // Use URI (preferred)
            val uri = Uri.parse(share.fileUri)
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else if (share.filePath.isNotEmpty()) {
            // Fallback to path
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(share.filePath)))
        }
        
        var message = "${if (share.fileType == "PHOTO") "Foto" else "Video"} del evento ${share.eventName}"
        if (share.contactType == "PHONE") {
            message += "\nPara: ${share.contact}"
        }
        shareIntent.putExtra(Intent.EXTRA_TEXT, message)
        shareIntent.setPackage("com.whatsapp")
        
        try {
            startActivity(shareIntent)
            manager.markAsSent(share.id)
            Toast.makeText(this, "✓ Marcado como enviado", Toast.LENGTH_SHORT).show()
            loadPendingShares()
        } catch (e: Exception) {
            // Try WhatsApp Business
            shareIntent.setPackage("com.whatsapp.w4b")
            try {
                startActivity(shareIntent)
                manager.markAsSent(share.id)
                Toast.makeText(this, "✓ Marcado como enviado", Toast.LENGTH_SHORT).show()
                loadPendingShares()
            } catch (e2: Exception) {
                // If neither work, show generic share dialog
                shareIntent.setPackage(null)
                try {
                    startActivity(Intent.createChooser(shareIntent, "Compartir por WhatsApp"))
                    manager.markAsSent(share.id)
                    Toast.makeText(this, "✓ Marcado como enviado", Toast.LENGTH_SHORT).show()
                    loadPendingShares()
                } catch (e3: Exception) {
                    Toast.makeText(this, "Error al compartir", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun confirmDelete(share: PendingShare) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar")
            .setMessage("¿Seguro que quieres eliminar esta asociación?")
            .setPositiveButton("Eliminar") { _, _ ->
                manager.deletePendingShare(share.id)
                Toast.makeText(this, "Eliminado", Toast.LENGTH_SHORT).show()
                loadPendingShares()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
