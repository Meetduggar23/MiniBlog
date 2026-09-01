package com.example.miniblog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.miniblog.databinding.ItemPostBinding
import com.example.miniblog.model.Post

class PostAdapter(
    private val posts: List<Post>,
    private val onItemClick: (Post) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    inner class PostViewHolder(val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): PostViewHolder {
        val binding = ItemPostBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        holder.binding.textViewTitle.text = post.title.replaceFirstChar {
            it.uppercase()
        }
        holder.binding.textViewBody.text = post.body
        holder.binding.root.setOnClickListener { onItemClick(post) }
    }

    override fun getItemCount(): Int = posts.size
}
