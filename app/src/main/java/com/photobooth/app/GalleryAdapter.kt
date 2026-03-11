package com.photobooth.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.photobooth.app.databinding.ItemGalleryPhotoBinding

class GalleryAdapter(
    private val uris: List<String>,
    private val types: List<String>,
    private val onClick: (uri: String, type: String) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.MediaViewHolder>() {

    class MediaViewHolder(val binding: ItemGalleryPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemGalleryPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val uri = uris[position]
        val type = types[position]
        holder.binding.photoImage.setImageBitmap(null)
        holder.binding.videoBadge.visibility = if (type == "VIDEO") View.VISIBLE else View.GONE
        // Load thumbnail on background thread
        Thread {
            val thumb = loadThumbnail(holder.itemView.context, uri, type)
            holder.itemView.post { holder.binding.photoImage.setImageBitmap(thumb) }
        }.start()
        holder.itemView.setOnClickListener { onClick(uri, type) }
    }

    private fun loadThumbnail(context: Context, uriString: String, type: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            if (type == "VIDEO") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(400, 400), null)
                } else {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(context, uri)
                    val bmp = r.getFrameAtTime(0)
                    r.release()
                    bmp
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) { null }
    }

    override fun getItemCount() = uris.size
}
