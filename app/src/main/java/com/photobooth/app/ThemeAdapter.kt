package com.photobooth.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.photobooth.app.databinding.ItemThemeBinding

class ThemeAdapter(
    private val themes: List<FramePreviewActivity.Theme>,
    private val onThemeClick: (FramePreviewActivity.Theme) -> Unit
) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {
    
    private var selectedPosition = -1
    
    inner class ThemeViewHolder(private val binding: ItemThemeBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(theme: FramePreviewActivity.Theme, isSelected: Boolean) {
            binding.themeIcon.text = theme.icon
            binding.themeName.text = theme.displayName
            
            // Highlight selected theme
            binding.cardTheme.setCardBackgroundColor(
                if (isSelected) {
                    binding.root.context.getColor(R.color.purple_200)
                } else {
                    binding.root.context.getColor(android.R.color.white)
                }
            )
            
            binding.root.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = bindingAdapterPosition
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)
                onThemeClick(theme)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val binding = ItemThemeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ThemeViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        holder.bind(themes[position], position == selectedPosition)
    }
    
    override fun getItemCount() = themes.size
}
