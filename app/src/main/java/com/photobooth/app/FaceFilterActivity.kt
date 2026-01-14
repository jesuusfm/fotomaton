package com.photobooth.app

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photobooth.app.databinding.ActivityFaceFilterBinding

/**
 * Activity for selecting face filters (mustaches, hats, masks, etc.)
 */
class FaceFilterActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityFaceFilterBinding
    private val filters = mutableListOf<FaceFilter>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaceFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        loadFilters()
        setupRecyclerView()
        
        binding.buttonBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        
        binding.buttonNone.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra("FILTER_TYPE", "none")
            resultIntent.putExtra("FILTER_PATH", "")
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
    
    private fun loadFilters() {
        filters.clear()
        
        // Add "None" option first
        // Then load filters from assets/face_filters/
        try {
            val filterFolders = assets.list("face_filters") ?: arrayOf()
            
            for (folder in filterFolders) {
                val files = assets.list("face_filters/$folder") ?: continue
                
                for (file in files) {
                    if (file.endsWith(".png") || file.endsWith(".webp")) {
                        val filterPath = "face_filters/$folder/$file"
                        val filterType = folder // mustache, hat, mask, glasses, etc.
                        val filterName = file.removeSuffix(".png").removeSuffix(".webp")
                            .replace("_", " ")
                            .replaceFirstChar { it.uppercase() }
                        
                        filters.add(FaceFilter(filterName, filterType, filterPath))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FaceFilterActivity", "Error loading filters: ${e.message}")
        }
        
        // Add example filters info if no filters found
        if (filters.isEmpty()) {
            binding.emptyMessage.visibility = View.VISIBLE
            binding.emptyMessage.text = """
                No hay filtros faciales disponibles.
                
                Para añadir filtros, crea carpetas en:
                assets/face_filters/
                
                Estructura:
                • face_filters/mustache/bigote1.png
                • face_filters/hat/gorro1.png
                • face_filters/mask/mascara1.png
                • face_filters/glasses/gafas1.png
                
                Las imágenes deben ser PNG con fondo transparente.
            """.trimIndent()
        } else {
            binding.emptyMessage.visibility = View.GONE
        }
    }
    
    private fun setupRecyclerView() {
        binding.recyclerFilters.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerFilters.adapter = FaceFilterAdapter(filters) { filter ->
            val resultIntent = Intent()
            resultIntent.putExtra("FILTER_TYPE", filter.type)
            resultIntent.putExtra("FILTER_PATH", filter.path)
            resultIntent.putExtra("FILTER_NAME", filter.name)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
    
    data class FaceFilter(
        val name: String,
        val type: String, // mustache, hat, mask, glasses, ears, nose
        val path: String
    )
    
    inner class FaceFilterAdapter(
        private val filters: List<FaceFilter>,
        private val onClick: (FaceFilter) -> Unit
    ) : RecyclerView.Adapter<FaceFilterAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.filter_image)
            val name: TextView = view.findViewById(R.id.filter_name)
            val type: TextView = view.findViewById(R.id.filter_type)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_face_filter, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val filter = filters[position]
            
            holder.name.text = filter.name
            holder.type.text = getTypeLabel(filter.type)
            
            // Load preview image from assets
            try {
                assets.open(filter.path).use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    holder.image.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                holder.image.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            
            holder.itemView.setOnClickListener {
                onClick(filter)
            }
        }
        
        override fun getItemCount() = filters.size
        
        private fun getTypeLabel(type: String): String {
            return when (type) {
                "mustache" -> "👨 Bigote"
                "hat" -> "🎩 Gorro"
                "mask" -> "🎭 Máscara"
                "glasses" -> "👓 Gafas"
                "ears" -> "🐰 Orejas"
                "nose" -> "🔴 Nariz"
                "full" -> "😎 Completo"
                else -> "🎨 Filtro"
            }
        }
    }
}
