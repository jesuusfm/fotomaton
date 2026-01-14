package com.photobooth.app

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.photobooth.app.databinding.ItemBackgroundBinding

class BackgroundAdapter(
    private val backgrounds: List<BackgroundPreviewActivity.BackgroundInfo>,
    private val assets: AssetManager,
    private val onBackgroundClick: (BackgroundPreviewActivity.BackgroundInfo) -> Unit
) : RecyclerView.Adapter<BackgroundAdapter.BackgroundViewHolder>() {
    
    private var selectedBackground: BackgroundPreviewActivity.BackgroundInfo? = null
    
    inner class BackgroundViewHolder(private val binding: ItemBackgroundBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bg: BackgroundPreviewActivity.BackgroundInfo) {
            // Load thumbnail from assets
            try {
                val inputStream = assets.open(bg.path)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                binding.backgroundImage.setImageBitmap(bitmap)
                inputStream.close()
            } catch (e: Exception) {
                // Show placeholder
                binding.backgroundImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            
            // Show selection indicator
            val isSelected = selectedBackground?.path == bg.path
            binding.selectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.checkmark.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            binding.root.setOnClickListener {
                onBackgroundClick(bg)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackgroundViewHolder {
        val binding = ItemBackgroundBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BackgroundViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: BackgroundViewHolder, position: Int) {
        holder.bind(backgrounds[position])
    }
    
    override fun getItemCount(): Int = backgrounds.size
    
    fun setSelected(bg: BackgroundPreviewActivity.BackgroundInfo) {
        selectedBackground = bg
        notifyDataSetChanged()
    }
}
