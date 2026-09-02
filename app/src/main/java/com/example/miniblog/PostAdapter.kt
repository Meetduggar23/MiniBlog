package com.example.miniblog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.miniblog.data.PostStatsStore
import com.example.miniblog.databinding.ItemPostBinding
import com.example.miniblog.model.Post
import com.example.miniblog.util.PostDateFormatter

class PostAdapter(
    private val stats: PostStatsStore,
    private val onItemClick: (Post) -> Unit,
    private val onDeleteClick: (Post) -> Unit,
    private val onLikeClick: (Post, Int) -> Unit,
    private val onBookmarkClick: (Post, Int) -> Unit,
    private val onLongPressClick: (Post) -> Unit
) : ListAdapter<Post, PostAdapter.PostViewHolder>(PostDiffCallback()) {

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
        val post = getItem(position)
        val context = holder.binding.root.context

        holder.binding.textViewTitle.text = post.title.replaceFirstChar {
            it.uppercase()
        }
        holder.binding.textViewBody.text = post.body

        // Tags: subtle "#tag #tag" line, hidden when the post has none.
        if (post.tags.isNotEmpty()) {
            holder.binding.textViewTags.visibility = android.view.View.VISIBLE
            holder.binding.textViewTags.text = post.tags.joinToString("  ") { "#$it" }
        } else {
            holder.binding.textViewTags.visibility = android.view.View.GONE
        }

        holder.binding.textViewDate.text = PostDateFormatter.format(post.createdAt)

        // Like state
        val liked = stats.isLiked(post.id)
        holder.binding.buttonLike.setImageResource(
            if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        )
        holder.binding.buttonLike.setColorFilter(
            androidx.core.content.ContextCompat.getColor(
                context, if (liked) R.color.error else R.color.text_secondary
            )
        )
        holder.binding.buttonLike.contentDescription =
            context.getString(if (liked) R.string.unlike_post else R.string.like_post)
        holder.binding.textViewLikes.text = stats.likeCount(post.id).toString()

        // Bookmark state
        val bookmarked = post.isBookmarked
        holder.binding.buttonBookmark.setImageResource(
            if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline
        )
        holder.binding.buttonBookmark.setColorFilter(
            androidx.core.content.ContextCompat.getColor(
                context, if (bookmarked) R.color.primary else R.color.text_secondary
            )
        )
        holder.binding.buttonBookmark.contentDescription = context.getString(
            if (bookmarked) R.string.remove_saved_post else R.string.save_post
        )

        holder.binding.textViewViews.text = stats.viewCount(post.id).toString()

        holder.binding.root.setOnClickListener { onItemClick(post) }
        holder.binding.root.setOnLongClickListener {
            onLongPressClick(post)
            true
        }
        holder.binding.buttonDelete.setOnClickListener { onDeleteClick(post) }
        holder.binding.buttonLike.setOnClickListener { view ->
            // Subtle press-pop animation; the state itself flips on rebind.
            view.animate().scaleX(1.25f).scaleY(1.25f).setDuration(110)
                .withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                }.start()
            onLikeClick(post, holder.bindingAdapterPosition)
        }
        holder.binding.buttonBookmark.setOnClickListener {
            onBookmarkClick(post, holder.bindingAdapterPosition)
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem == newItem
        }
    }
}
