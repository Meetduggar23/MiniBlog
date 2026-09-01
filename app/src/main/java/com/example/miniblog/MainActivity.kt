package com.example.miniblog

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
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
    private var allPosts = listOf<Post>()
    private lateinit var adapter: PostAdapter
    private var currentSearchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Mini Blog Explorer"

        adapter = PostAdapter { post ->
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

        binding.buttonRetry.setOnClickListener {
            loadPosts()
        }

        // Search filtering
        binding.editTextSearch.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    currentSearchQuery = s?.toString()?.trim() ?: ""
                    filterPosts()
                }
            }
        )

        binding.editTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterPosts()
                true
            } else false
        }

        if (savedInstanceState != null) {
            val savedTitles = savedInstanceState.getStringArrayList("post_titles")
            val savedBodies = savedInstanceState.getStringArrayList("post_bodies")
            val savedIds = savedInstanceState.getIntegerArrayList("post_ids")
            if (savedIds != null && savedTitles != null && savedBodies != null &&
                savedIds.isNotEmpty()) {
                allPosts = savedIds.indices.map { i ->
                    Post(userId = 1, id = savedIds[i], title = savedTitles[i], body = savedBodies[i])
                }
                filterPosts()
                binding.progressBar.visibility = View.GONE
                if (allPosts.isEmpty()) showEmpty("No posts found.")
            } else {
                loadPosts()
            }
        } else {
            loadPosts()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadPosts()
                true
            }
            R.id.action_overflow -> {
                showOverflowMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showOverflowMenu() {
        val options = arrayOf("Refresh", "About", "Cancel")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> loadPosts()
                    1 -> Toast.makeText(
                        this,
                        "Mini Blog Explorer v1.0\nPowered by jsonplaceholder.typicode.com",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("post_titles", ArrayList(allPosts.map { it.title }))
        outState.putStringArrayList("post_bodies", ArrayList(allPosts.map { it.body }))
        outState.putIntegerArrayList("post_ids", ArrayList(allPosts.map { it.id }))
    }

    private fun filterPosts() {
        val filtered = if (currentSearchQuery.isEmpty()) {
            allPosts
        } else {
            allPosts.filter {
                it.title.contains(currentSearchQuery, ignoreCase = true) ||
                        it.body.contains(currentSearchQuery, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)

        if (filtered.isEmpty() && allPosts.isNotEmpty()) {
            showEmpty("No posts match \"$currentSearchQuery\"")
        } else if (filtered.isEmpty()) {
            // keep empty state
        } else {
            hideEmpty()
        }
    }

    private fun loadPosts() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showEmpty("No internet connection.")
            binding.buttonRetry.visibility = View.VISIBLE
            binding.swipeRefresh.isRefreshing = false
            return
        }
        showLoading(true)
        lifecycleScope.launch {
            when (val result = repository.getPosts()) {
                is NetworkResult.Success -> {
                    showLoading(false)
                    binding.swipeRefresh.isRefreshing = false
                    allPosts = result.data
                    filterPosts()
                    if (allPosts.isEmpty()) showEmpty("No posts found.")
                    else hideEmpty()
                }
                is NetworkResult.Error -> {
                    showLoading(false)
                    binding.swipeRefresh.isRefreshing = false
                    showEmpty(result.message)
                    binding.buttonRetry.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility =
            if (isLoading) View.VISIBLE else View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.recyclerViewPosts.visibility =
            if (isLoading) View.GONE else View.VISIBLE
    }

    private fun showEmpty(message: String) {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.textViewMessage.text = message
        binding.progressBar.visibility = View.GONE
        binding.recyclerViewPosts.visibility = View.GONE
    }

    private fun hideEmpty() {
        binding.layoutEmpty.visibility = View.GONE
        binding.buttonRetry.visibility = View.GONE
        binding.recyclerViewPosts.visibility = View.VISIBLE
    }
}
