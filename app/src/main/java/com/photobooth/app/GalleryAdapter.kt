package com.photobooth.app

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.photobooth.app.databinding.ItemGalleryPhotoBinding

class GalleryAdapter(private val bitmaps: List<Bitmap>) : RecyclerView.Adapter<GalleryAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(val binding: ItemGalleryPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemGalleryPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.binding.photoImage.setImageBitmap(bitmaps[position])
    }

    override fun getItemCount() = bitmaps.size
}
