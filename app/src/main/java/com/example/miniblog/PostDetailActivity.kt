package com.example.miniblog

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.miniblog.data.PostRepository
import com.example.miniblog.databinding.ActivityPostDetailBinding
import com.example.miniblog.model.Comment
import com.example.miniblog.network.NetworkResult
import com.example.miniblog.network.NetworkUtils
import kotlinx.coroutines.launch

class PostDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostDetailBinding
    private val repository = PostRepository()
    private val commentList = mutableListOf<Comment>()
    private lateinit var adapter: CommentAdapter

    private var currentPostId = -1
    private var postTitle = ""
    private var postBody = ""

    companion object {
        const val LOCAL_POST_ID_BASE = 10000
        private const val KEY_POST_ID = "saved_post_id"
        private const val KEY_COMMENT_NAMES = "saved_comment_names"
        private const val KEY_COMMENT_EMAILS = "saved_comment_emails"
        private const val KEY_COMMENT_BODIES = "saved_comment_bodies"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val postId = intent.getIntExtra("POST_ID", -1)
        val title = intent.getStringExtra("POST_TITLE") ?: ""
        val body = intent.getStringExtra("POST_BODY") ?: ""

        currentPostId = postId
        postTitle = title
        postBody = body
        supportActionBar?.title = title.replaceFirstChar { it.uppercase() }
        binding.textViewDetailBody.text = body

        adapter = CommentAdapter(commentList)
        binding.recyclerViewComments.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewComments.adapter = adapter

        // Restore comments on rotation
        if (savedInstanceState != null) {
            val names = savedInstanceState.getStringArrayList(KEY_COMMENT_NAMES)
            val emails = savedInstanceState.getStringArrayList(KEY_COMMENT_EMAILS)
            val bodies = savedInstanceState.getStringArrayList(KEY_COMMENT_BODIES)
            if (names != null && emails != null && bodies != null) {
                commentList.clear()
                for (i in names.indices) {
                    commentList.add(
                        Comment(
                            postId = postId,
                            id = i,
                            name = names[i],
                            email = emails[i],
                            body = bodies[i]
                        )
                    )
                }
                adapter.notifyDataSetChanged()
                binding.progressBarComments.visibility = View.GONE
                if (commentList.isEmpty()) {
                    showEmptyMessage("No comments yet")
                }
            } else if (postId != -1) {
                loadComments(postId)
            }
        } else if (postId != -1) {
            loadComments(postId)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_POST_ID, currentPostId)
        outState.putStringArrayList(
            KEY_COMMENT_NAMES,
            ArrayList(commentList.map { it.name })
        )
        outState.putStringArrayList(
            KEY_COMMENT_EMAILS,
            ArrayList(commentList.map { it.email })
        )
        outState.putStringArrayList(
            KEY_COMMENT_BODIES,
            ArrayList(commentList.map { it.body })
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_post_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                showDeleteConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete") { _, _ -> deletePost() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePost() {
        // Locally-created posts (id >= LOCAL_POST_ID_BASE) exist only on this
        // device — remove them locally instead of hitting the fake API.
        if (currentPostId < LOCAL_POST_ID_BASE &&
            !NetworkUtils.isNetworkAvailable(this)
        ) {
            Toast.makeText(this, "No internet connection.", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentPostId >= LOCAL_POST_ID_BASE) {
            onPostDeleted()
            return
        }
        binding.progressBarComments.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val result = repository.deletePost(currentPostId)) {
                is NetworkResult.Success -> onPostDeleted()
                is NetworkResult.Error -> {
                    binding.progressBarComments.visibility = View.GONE
                    Toast.makeText(
                        this@PostDetailActivity,
                        "Failed to delete: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun onPostDeleted() {
        val resultIntent = Intent().apply {
            putExtra("DELETED_POST_ID", currentPostId)
        }
        setResult(RESULT_OK, resultIntent)
        Toast.makeText(this, "Post deleted successfully", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun loadComments(postId: Int) {
        binding.progressBarComments.visibility = View.VISIBLE
        binding.textViewCommentsMessage.visibility = View.GONE
        lifecycleScope.launch {
            when (val result = repository.getComments(postId)) {
                is NetworkResult.Success -> {
                    binding.progressBarComments.visibility = View.GONE
                    commentList.clear()
                    commentList.addAll(result.data)
                    adapter.notifyDataSetChanged()
                    if (commentList.isEmpty()) {
                        showEmptyMessage("No comments yet")
                    }
                }
                is NetworkResult.Error -> {
                    binding.progressBarComments.visibility = View.GONE
                    binding.textViewCommentsMessage.setTextColor(
                        ContextCompat.getColor(
                            this@PostDetailActivity, R.color.error
                        )
                    )
                    binding.textViewCommentsMessage.text = result.message
                    binding.textViewCommentsMessage.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showEmptyMessage(message: String) {
        binding.textViewCommentsMessage.setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
        binding.textViewCommentsMessage.text = message
        binding.textViewCommentsMessage.visibility = View.VISIBLE
    }
}
