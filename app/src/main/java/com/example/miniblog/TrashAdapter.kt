package com.example.miniblog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.miniblog.databinding.ItemTrashBinding
import com.example.miniblog.model.Post
import com.example.miniblog.util.PostDateFormatter

class TrashAdapter(
    private val onRestoreClick: (Post) -> Unit,
    private val onDeleteForeverClick: (Post) -> Unit
) : ListAdapter<Post, TrashAdapter.TrashViewHolder>(TrashDiffCallback()) {

    inner class TrashViewHolder(val binding: ItemTrashBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): TrashViewHolder {
        val binding = ItemTrashBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrashViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        val post = getItem(position)
        holder.binding.textViewTrashTitle.text = post.title.replaceFirstChar {
            it.uppercase()
        }
        holder.binding.textViewTrashBody.text = post.body
        holder.binding.buttonRestore.setOnClickListener { onRestoreClick(post) }
        holder.binding.buttonDeleteForever.setOnClickListener {
            onDeleteForeverClick(post)
        }
    }

    class TrashDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean =
            oldItem == newItem
    }
}
