package com.example.miniblog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.miniblog.databinding.ItemDraftBinding
import com.example.miniblog.model.Draft
import com.example.miniblog.util.PostDateFormatter

class DraftAdapter(
    private val onItemClick: (Draft) -> Unit,
    private val onDeleteClick: (Draft) -> Unit
) : ListAdapter<Draft, DraftAdapter.DraftViewHolder>(DraftDiffCallback()) {

    inner class DraftViewHolder(val binding: ItemDraftBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): DraftViewHolder {
        val binding = ItemDraftBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DraftViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DraftViewHolder, position: Int) {
        val draft = getItem(position)
        holder.binding.textViewDraftTitle.text = draft.title.ifBlank {
            holder.binding.root.context.getString(R.string.hint_title)
        }
        holder.binding.textViewDraftBody.text = draft.body
        holder.binding.textViewDraftDate.text = holder.binding.root.context.getString(
            R.string.draft_last_saved,
            PostDateFormatter.format(draft.updatedAt)
        )
        holder.binding.root.setOnClickListener { onItemClick(draft) }
        holder.binding.buttonDeleteDraft.setOnClickListener { onDeleteClick(draft) }
    }

    class DraftDiffCallback : DiffUtil.ItemCallback<Draft>() {
        override fun areItemsTheSame(oldItem: Draft, newItem: Draft): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Draft, newItem: Draft): Boolean =
            oldItem == newItem
    }
}
