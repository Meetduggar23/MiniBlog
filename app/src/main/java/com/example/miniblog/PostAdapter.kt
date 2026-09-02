package com.example.miniblog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.miniblog.data.PostStatsStore
import com.example.miniblog.databinding.ItemPostBinding
import com.example.miniblog.model.Post
import com.example.miniblog.util.PostDateFormatter

/**
 * Card adapter for the blog feed. Supports like/bookmark/delete alongside a
 * multi-select (bulk) mode entered via long-press.
 */
class PostAdapter(
    private val stats: PostStatsStore,
    private val onItemClick: (Post) -> Unit,
    private val onDeleteClick: (Post) -> Unit,
    private val onLikeClick: (Post, Int) -> Unit,
    private val onBookmarkClick: (Post, Int) -> Unit,
    private val onLongPressToSelect: (Post) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {}
) : ListAdapter<Post, PostAdapter.PostViewHolder>(PostDiffCallback()) {

    private val selectedIds = LinkedHashSet<Int>()
    private var selectionMode = false

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

        // Selection indicator (visible only while in selection mode).
        val selectView = holder.binding.imageViewSelect
        if (selectionMode) {
            selectView.visibility = android.view.View.VISIBLE
            selectView.setImageResource(
                if (selectedIds.contains(post.id)) R.drawable.ic_check_circle
                else R.drawable.ic_radio_unchecked
            )
            selectView.setColorFilter(
                ContextCompat.getColor(
                    context,
                    if (selectedIds.contains(post.id)) R.color.primary else R.color.text_secondary
                )
            )
            selectView.contentDescription = context.getString(
                if (selectedIds.contains(post.id)) R.string.deselect_post
                else R.string.select_post
            )
            holder.binding.root.isActivated = selectedIds.contains(post.id)
            holder.binding.buttonDelete.visibility = android.view.View.GONE
        } else {
            selectView.visibility = android.view.View.GONE
            holder.binding.root.isActivated = false
            holder.binding.buttonDelete.visibility = android.view.View.VISIBLE
        }

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

        // Small pinned indicator for feed cards.
        holder.binding.imageViewPinned.visibility =
            if (post.isPinned) android.view.View.VISIBLE else android.view.View.GONE

        // Like state
        val liked = stats.isLiked(post.id)
        holder.binding.buttonLike.setImageResource(
            if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        )
        holder.binding.buttonLike.setColorFilter(
            ContextCompat.getColor(
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
            ContextCompat.getColor(
                context, if (bookmarked) R.color.primary else R.color.text_secondary
            )
        )
        holder.binding.buttonBookmark.contentDescription = context.getString(
            if (bookmarked) R.string.remove_saved_post else R.string.save_post
        )

        holder.binding.textViewViews.text = stats.viewCount(post.id).toString()

        holder.binding.root.setOnClickListener {
            if (selectionMode) {
                // Tapping a card toggles its selection.
                holder.bindingAdapterPosition.let { pos ->
                    if (pos != RecyclerView.NO_POSITION) togglePost(post.id)
                }
            } else {
                onItemClick(post)
            }
        }
        holder.binding.root.setOnLongClickListener {
            if (!selectionMode) {
                onLongPressToSelect(post)
            }
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

    // ------------------------------------------------------------------
    // Selection (bulk) mode API
    // ------------------------------------------------------------------

    fun isSelectionMode(): Boolean = selectionMode

    /** Enters selection mode, selecting the given post (or none if null). */
    fun enterSelectionMode(postId: Int) {
        selectionMode = true
        selectedIds.add(postId)
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun exitSelectionMode() {
        if (!selectionMode && selectedIds.isEmpty()) return
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun isSelected(postId: Int): Boolean = selectedIds.contains(postId)

    private fun togglePost(postId: Int) {
        if (selectedIds.contains(postId)) selectedIds.remove(postId) else selectedIds.add(postId)
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    /** Selects all posts currently shown in the list (currentList). */
    fun selectAll(): Int {
        currentList.forEach { selectedIds.add(it.id) }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
        return selectedIds.size
    }

    /** Clears the current selection but stays in selection mode. */
    fun deselectAll(): Int {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
        return 0
    }

    fun selectedCount(): Int = selectedIds.size

    fun selectedPostIds(): Set<Int> = selectedIds.toSet()

    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem == newItem
        }
    }
}
