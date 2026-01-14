package com.photobooth.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.photobooth.app.databinding.ActivityPreviewBinding
import java.io.File

class PreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPreviewBinding
    private var type: String = "PHOTO"
    private lateinit var eventName: String
    private var isSlowMotion = false
    private var currentFileUri: String? = null
    private var currentFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        type = intent.getStringExtra("TYPE") ?: "PHOTO"
        eventName = intent.getStringExtra("EVENT_NAME") ?: "Evento"
        isSlowMotion = intent.getBooleanExtra("SLOW_MOTION", false)

        if (type == "PHOTO") {
            // Try IMAGE_URI first (new way), then IMAGE_PATH (old way)
            val imageUriString = intent.getStringExtra("IMAGE_URI")
            val imagePath = intent.getStringExtra("IMAGE_PATH")
            
            if (imageUriString != null) {
                currentFileUri = imageUriString
                val uri = Uri.parse(imageUriString)
                binding.previewImage.setImageURI(uri)
            } else if (imagePath != null) {
                currentFilePath = imagePath
                val bitmap = BitmapFactory.decodeFile(imagePath)
                binding.previewImage.setImageBitmap(bitmap)
            }
            
            binding.previewImage.visibility = android.view.View.VISIBLE
            binding.previewVideo.visibility = android.view.View.GONE
        } else {
            val videoUriString = intent.getStringExtra("VIDEO_URI")
            videoUriString?.let { uriString ->
                currentFileUri = uriString
                val uri = Uri.parse(uriString)
                binding.previewVideo.setVideoURI(uri)
                
                // Video is already processed in slow motion, just play normally
                binding.previewVideo.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.start()
                }
                
                binding.previewVideo.start()
            }
            binding.previewImage.visibility = android.view.View.GONE
            binding.previewVideo.visibility = android.view.View.VISIBLE
        }

        binding.buttonAnother.setOnClickListener {
            showConfirmationDialog()
        }

        binding.buttonShare.setOnClickListener {
            shareContent()
        }
    }

    private fun showConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_take_another, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_yes).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_no).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun shareContent() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_share_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_email).setOnClickListener {
            dialog.dismiss()
            shareViaEmail()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_whatsapp).setOnClickListener {
            dialog.dismiss()
            shareViaWhatsApp()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_whatsapp_direct).setOnClickListener {
            dialog.dismiss()
            showWhatsAppNumberDialog()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_save_contact).setOnClickListener {
            dialog.dismiss()
            associateWithContact()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun shareViaEmail() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = if (type == "PHOTO") "image/jpeg" else "video/mp4"
        
        if (type == "PHOTO") {
            if (currentFileUri != null) {
                val uri = Uri.parse(currentFileUri)
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else if (currentFilePath != null) {
                shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(currentFilePath!!)))
            }
        } else {
            currentFileUri?.let {
                val uri = Uri.parse(it)
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Foto/Video del evento: $eventName")
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Te envío este ${if (type == "PHOTO") "foto" else "video"} del evento.")
        
        try {
            startActivity(Intent.createChooser(shareIntent, "Enviar por Email"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showWhatsAppNumberDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Número con código de país (ej: 34612345678)"
        input.inputType = android.text.InputType.TYPE_CLASS_PHONE
        input.setPadding(50, 40, 50, 40)
        
        AlertDialog.Builder(this)
            .setTitle("Enviar a WhatsApp")
            .setMessage("Ingresa el número con el código de país sin el símbolo +")
            .setView(input)
            .setPositiveButton("Enviar") { _, _ ->
                val phoneNumber = input.text.toString().trim()
                if (phoneNumber.isNotEmpty()) {
                    shareViaWhatsAppDirect(phoneNumber)
                } else {
                    Toast.makeText(this, "Número inválido", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun shareViaWhatsAppDirect(phoneNumber: String) {
        val uri = if (type == "PHOTO") {
            if (currentFileUri != null) {
                Uri.parse(currentFileUri)
            } else if (currentFilePath != null) {
                Uri.fromFile(File(currentFilePath!!))
            } else {
                Toast.makeText(this, "Error: No se encontró el archivo", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            currentFileUri?.let { Uri.parse(it) } ?: run {
                Toast.makeText(this, "Error: No se encontró el archivo", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        try {
            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(Intent.EXTRA_TEXT, "${if (type == "PHOTO") "Foto" else "Video"} del evento $eventName")
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri)
            sendIntent.type = if (type == "PHOTO") "image/jpeg" else "video/mp4"
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sendIntent.setPackage("com.whatsapp")
            
            // Add phone number to open specific chat
            val whatsappUri = "https://wa.me/$phoneNumber"
            val openChatIntent = Intent(Intent.ACTION_VIEW)
            openChatIntent.data = Uri.parse(whatsappUri)
            openChatIntent.setPackage("com.whatsapp")
            
            try {
                // First open the chat
                startActivity(openChatIntent)
                // Small delay to let WhatsApp open
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startActivity(sendIntent)
                }, 1000)
            } catch (e: Exception) {
                // Try WhatsApp Business
                openChatIntent.setPackage("com.whatsapp.w4b")
                sendIntent.setPackage("com.whatsapp.w4b")
                try {
                    startActivity(openChatIntent)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startActivity(sendIntent)
                    }, 1000)
                } catch (e2: Exception) {
                    Toast.makeText(this, "WhatsApp no está instalado", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al enviar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun shareViaWhatsApp() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = if (type == "PHOTO") "image/jpeg" else "video/mp4"
        
        if (type == "PHOTO") {
            if (currentFileUri != null) {
                val uri = Uri.parse(currentFileUri)
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else if (currentFilePath != null) {
                shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(currentFilePath!!)))
            }
        } else {
            currentFileUri?.let {
                val uri = Uri.parse(it)
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        
        shareIntent.putExtra(Intent.EXTRA_TEXT, "${if (type == "PHOTO") "Foto" else "Video"} del evento $eventName")
        shareIntent.setPackage("com.whatsapp")
        
        try {
            startActivity(shareIntent)
        } catch (e: Exception) {
            // Try WhatsApp Business
            shareIntent.setPackage("com.whatsapp.w4b")
            try {
                startActivity(shareIntent)
            } catch (e2: Exception) {
                // If neither work, show generic share dialog
                shareIntent.setPackage(null)
                try {
                    startActivity(Intent.createChooser(shareIntent, "Compartir por WhatsApp"))
                } catch (e3: Exception) {
                    Toast.makeText(this, "Error al compartir", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun associateWithContact() {
        val input = EditText(this)
        input.hint = "ejemplo@email.com o +34612345678"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        input.setPadding(50, 30, 50, 30)
        
        AlertDialog.Builder(this)
            .setTitle("💾 Guardar contacto")
            .setMessage("Introduce el email o teléfono.\nPodrás enviar este archivo más tarde desde la galería del evento.")
            .setView(input)
            .setPositiveButton("✓ Guardar") { _, _ ->
                val contact = input.text.toString().trim()
                if (contact.isEmpty()) {
                    Toast.makeText(this, "❌ Debes introducir un contacto", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Detect if it's email or phone
                val contactType = if (Patterns.EMAIL_ADDRESS.matcher(contact).matches()) {
                    "EMAIL"
                } else if (contact.replace(Regex("[^0-9]"), "").length >= 9) {
                    "PHONE"
                } else {
                    Toast.makeText(this, "❌ Formato inválido. Usa un email o teléfono válido", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                
                // Save association
                val manager = PendingSharesManager(this)
                val filePath = if (type == "PHOTO" && currentFilePath != null) currentFilePath else ""
                val fileUri = if (type == "VIDEO" || (type == "PHOTO" && currentFileUri != null)) {
                    currentFileUri ?: ""
                } else {
                    ""
                }
                
                manager.addPendingShare(
                    eventName = eventName,
                    filePath = filePath ?: "",
                    fileUri = fileUri ?: "",
                    fileType = type,
                    contact = contact,
                    contactType = contactType
                )
                
                Toast.makeText(this, "✓ Guardado para enviar a:\n$contact", Toast.LENGTH_LONG).show()
                
                // Show info about accessing pending shares
                AlertDialog.Builder(this)
                    .setTitle("✓ Contacto guardado")
                    .setMessage("Para enviar este archivo más tarde:\n\n1️⃣ Ve a la galería del evento\n2️⃣ Pulsa el botón 'VER ENVÍOS PENDIENTES'\n3️⃣ Envía cuando tengas conexión")
                    .setPositiveButton("Entendido") { _, _ ->
                        finish()
                    }
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up temp photo file if it exists
        if (type == "PHOTO") {
            currentFilePath?.let { path ->
                File(path).delete()
            }
        }
    }
}
