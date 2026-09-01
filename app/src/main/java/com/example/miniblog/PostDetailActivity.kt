package com.example.miniblog

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.miniblog.data.PostRepository
import com.example.miniblog.databinding.ActivityPostDetailBinding
import com.example.miniblog.model.Comment
import com.example.miniblog.network.NetworkResult
import kotlinx.coroutines.launch

class PostDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostDetailBinding
    private val repository = PostRepository()
    private val commentList = mutableListOf<Comment>()
    private lateinit var adapter: CommentAdapter

    private var currentPostId = -1

    companion object {
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
                    binding.textViewCommentsMessage.text = "No comments yet"
                    binding.textViewCommentsMessage.visibility = View.VISIBLE
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

    private fun loadComments(postId: Int) {
        binding.progressBarComments.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val result = repository.getComments(postId)) {
                is NetworkResult.Success -> {
                    binding.progressBarComments.visibility = View.GONE
                    commentList.clear()
                    commentList.addAll(result.data)
                    adapter.notifyDataSetChanged()
                    if (commentList.isEmpty()) {
                        binding.textViewCommentsMessage.text = "No comments yet"
                        binding.textViewCommentsMessage.visibility = View.VISIBLE
                    }
                }
                is NetworkResult.Error -> {
                    binding.progressBarComments.visibility = View.GONE
                    binding.textViewCommentsMessage.text = result.message
                    binding.textViewCommentsMessage.visibility = View.VISIBLE
                }
            }
        }
    }
}
