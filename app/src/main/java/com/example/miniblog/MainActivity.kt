package com.example.miniblog

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.miniblog.data.PostRepository
import com.example.miniblog.databinding.ActivityMainBinding
import com.example.miniblog.model.Post
import com.example.miniblog.network.NetworkResult
import com.example.miniblog.network.NetworkUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repository = PostRepository()
    private val postList = mutableListOf<Post>()
    private lateinit var adapter: PostAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Mini Blog Explorer"

        adapter = PostAdapter(postList) { post ->
            val intent = Intent(this, PostDetailActivity::class.java)
            intent.putExtra("POST_ID", post.id)
            intent.putExtra("POST_TITLE", post.title)
            intent.putExtra("POST_BODY", post.body)
            startActivity(intent)
        }
        binding.recyclerViewPosts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPosts.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            loadPosts()
        }

        binding.fabCreate.setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }

        loadPosts()
    }

    private fun loadPosts() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showMessage("No internet connection.")
            binding.swipeRefresh.isRefreshing = false
            return
        }
        showLoading(true)
        lifecycleScope.launch {
            when (val result = repository.getPosts()) {
                is NetworkResult.Success -> {
                    showLoading(false)
                    binding.swipeRefresh.isRefreshing = false
                    postList.clear()
                    postList.addAll(result.data)
                    adapter.notifyDataSetChanged()
                    if (postList.isEmpty()) showMessage("No posts found.")
                }
                is NetworkResult.Error -> {
                    showLoading(false)
                    binding.swipeRefresh.isRefreshing = false
                    showMessage(result.message)
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility =
            if (isLoading) View.VISIBLE else View.GONE
        binding.textViewMessage.visibility = View.GONE
    }

    private fun showMessage(message: String) {
        binding.textViewMessage.text = message
        binding.textViewMessage.visibility = View.VISIBLE
    }
}
