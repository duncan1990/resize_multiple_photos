package com.ahmety.resizemultiplephotos

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class PhotosListAdapter(
    private val uriList: MutableList<Uri> = mutableListOf<Uri>(),
    private val context: Context
) : RecyclerView.Adapter<PhotosListAdapter.PhotoViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PhotosListAdapter.PhotoViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(
            R.layout.item_photo,
            parent, false
        )
        return PhotoViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: PhotosListAdapter.PhotoViewHolder, position: Int) {
        holder.ivPhoto.setImageURI(uriList[position])
    }

    override fun getItemCount(): Int {
        return uriList.size
    }

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPhoto: ImageView = itemView.findViewById(R.id.ivPhotos)
    }
}