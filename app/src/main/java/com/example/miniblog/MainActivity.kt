package com.example.miniblog

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    private var nextLocalId = PostDetailActivity.LOCAL_POST_ID_BASE + 1
    private var isLoading = false

    private val createPostLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val title = result.data?.getStringExtra("POST_TITLE") ?: ""
            val body = result.data?.getStringExtra("POST_BODY") ?: ""
            if (title.isNotEmpty() && body.isNotEmpty()) {
                val newPost = Post(
                    userId = 1,
                    id = nextLocalId++,
                    title = title,
                    body = body,
                    createdAt = System.currentTimeMillis()
                )
                allPosts = listOf(newPost) + allPosts
                filterPosts()
            }
        }
    }

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val deletedId = result.data?.getIntExtra("DELETED_POST_ID", -1) ?: -1
            if (deletedId != -1) {
                allPosts = allPosts.filter { it.id != deletedId }
                filterPosts()
            }
        }
    }

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
            detailLauncher.launch(intent)
        }
        binding.recyclerViewPosts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPosts.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            if (!isLoading) {
                loadPosts(fromSwipe = true)
            }
        }

        binding.fabCreate.setOnClickListener {
            createPostLauncher.launch(Intent(this, CreatePostActivity::class.java))
        }

        binding.buttonCreatePost.setOnClickListener {
            createPostLauncher.launch(Intent(this, CreatePostActivity::class.java))
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
            val savedTimestamps = savedInstanceState.getLongArray("post_timestamps")
            nextLocalId = savedInstanceState.getInt("next_local_id", nextLocalId)
            if (savedIds != null && savedTitles != null && savedBodies != null &&
                savedIds.isNotEmpty()
            ) {
                allPosts = savedIds.indices.map { i ->
                    Post(
                        userId = 1,
                        id = savedIds[i],
                        title = savedTitles[i],
                        body = savedBodies[i],
                        createdAt = savedTimestamps?.getOrNull(i) ?: System.currentTimeMillis()
                    )
                }
                filterPosts()
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
                if (!isLoading) loadPosts()
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
        AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> if (!isLoading) loadPosts()
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
        outState.putLongArray("post_timestamps", allPosts.map { it.createdAt }.toLongArray())
        outState.putInt("next_local_id", nextLocalId)
    }

    private fun localPosts() = allPosts.filter { it.id >= PostDetailActivity.LOCAL_POST_ID_BASE }

    private fun loadPosts(fromSwipe: Boolean = false) {
        if (isLoading) return
        if (!NetworkUtils.isNetworkAvailable(this)) {
            binding.swipeRefresh.isRefreshing = false
            if (allPosts.isEmpty()) {
                showEmptyFeed()
            } else {
                Toast.makeText(this, "Offline — showing saved posts", Toast.LENGTH_SHORT).show()
            }
            return
        }
        isLoading = true
        if (fromSwipe) {
            binding.swipeRefresh.isRefreshing = true
        } else if (allPosts.isEmpty()) {
            binding.progressBar.visibility = View.VISIBLE
            hideEmpty()
        }
        lifecycleScope.launch {
            val result = repository.getPosts()
            isLoading = false
            binding.swipeRefresh.isRefreshing = false
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    // Keep locally-created posts pinned at the top of the feed.
                    allPosts = localPosts() + result.data
                    filterPosts()
                }
                is NetworkResult.Error -> {
                    if (allPosts.isEmpty()) {
                        showEmptyFeed()
                    }
                    Toast.makeText(
                        this@MainActivity,
                        "Couldn't load posts: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
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

        if (allPosts.isEmpty() && currentSearchQuery.isEmpty()) {
            showEmptyFeed()
        } else if (filtered.isEmpty() && allPosts.isNotEmpty()) {
            showSearchEmpty()
        } else {
            hideEmpty()
        }
    }

    private fun showEmptyFeed() {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.imageViewEmptyLogo.visibility = View.VISIBLE
        binding.textViewEmptyTitle.visibility = View.VISIBLE
        binding.textViewEmptySubtitle.visibility = View.VISIBLE
        binding.buttonCreatePost.visibility = View.VISIBLE
        binding.textViewMessage.visibility = View.GONE
        binding.buttonRetry.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.recyclerViewPosts.visibility = View.GONE
    }

    private fun showSearchEmpty() {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.imageViewEmptyLogo.visibility = View.GONE
        binding.textViewEmptyTitle.visibility = View.GONE
        binding.textViewEmptySubtitle.visibility = View.GONE
        binding.buttonCreatePost.visibility = View.GONE
        binding.textViewMessage.visibility = View.VISIBLE
        binding.textViewMessage.text = "No posts match \"$currentSearchQuery\""
        binding.buttonRetry.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.recyclerViewPosts.visibility = View.GONE
    }

    private fun hideEmpty() {
        binding.layoutEmpty.visibility = View.GONE
        binding.recyclerViewPosts.visibility = View.VISIBLE
    }
}
