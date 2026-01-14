package com.photobooth.app

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.photobooth.app.databinding.ItemFrameBinding

class FrameAdapter(
    private val frames: List<FramePreviewActivity.FrameInfo>,
    private val onFrameClick: (FramePreviewActivity.FrameInfo) -> Unit
) : RecyclerView.Adapter<FrameAdapter.FrameViewHolder>() {
    
    private var selectedFrame: FramePreviewActivity.FrameInfo? = null
    
    inner class FrameViewHolder(private val binding: ItemFrameBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(frame: FramePreviewActivity.FrameInfo, isSelected: Boolean) {
            // Load thumbnail from assets
            try {
                val inputStream = binding.root.context.assets.open(frame.path)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                binding.frameThumbnail.setImageBitmap(bitmap)
                inputStream.close()
            } catch (e: Exception) {
                // Show placeholder on error
                binding.frameThumbnail.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            
            // Show selection indicator
            binding.selectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.checkmark.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            binding.root.setOnClickListener {
                onFrameClick(frame)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FrameViewHolder {
        val binding = ItemFrameBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FrameViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: FrameViewHolder, position: Int) {
        holder.bind(frames[position], frames[position] == selectedFrame)
    }
    
    override fun getItemCount() = frames.size
    
    fun setSelected(frame: FramePreviewActivity.FrameInfo) {
        val oldSelection = selectedFrame
        selectedFrame = frame
        
        // Notify changes
        oldSelection?.let { old ->
            val oldIndex = frames.indexOf(old)
            if (oldIndex != -1) notifyItemChanged(oldIndex)
        }
        
        val newIndex = frames.indexOf(frame)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }
}
