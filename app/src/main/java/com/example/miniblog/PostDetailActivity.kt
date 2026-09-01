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

        supportActionBar?.title = title.replaceFirstChar { it.uppercase() }
        binding.textViewDetailBody.text = body

        adapter = CommentAdapter(commentList)
        binding.recyclerViewComments.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewComments.adapter = adapter

        if (postId != -1) loadComments(postId)
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
