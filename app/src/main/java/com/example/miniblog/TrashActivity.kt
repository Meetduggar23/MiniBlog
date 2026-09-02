package com.example.miniblog

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.miniblog.data.LocalPostStore
import com.example.miniblog.databinding.ActivityTrashBinding
import com.example.miniblog.model.Post
import com.google.android.material.snackbar.Snackbar

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding
    private val postStore by lazy { LocalPostStore(this) }
    private lateinit var adapter: TrashAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.trash)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = TrashAdapter(
            onRestoreClick = { post -> restorePost(post) },
            onDeleteForeverClick = { post -> confirmDeletePermanently(post) }
        )
        binding.recyclerViewTrash.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.recyclerViewTrash.adapter = adapter

        loadTrash()
    }

    private fun loadTrash() {
        val trashedPosts = postStore.getTrash()
        adapter.submitList(trashedPosts)
        if (trashedPosts.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.recyclerViewTrash.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.recyclerViewTrash.visibility = View.VISIBLE
        }
    }

    private fun restorePost(post: Post) {
        postStore.addPost(post.copy(deletedAt = null))
        loadTrash()
        Snackbar.make(binding.root, R.string.post_restored, Snackbar.LENGTH_SHORT).show()
    }

    private fun confirmDeletePermanently(post: Post) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_permanently_confirm)
            .setPositiveButton(R.string.delete_permanently) { _, _ ->
                postStore.removePost(post.id)
                loadTrash()
                Snackbar.make(
                    binding.root, R.string.post_deleted, Snackbar.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
