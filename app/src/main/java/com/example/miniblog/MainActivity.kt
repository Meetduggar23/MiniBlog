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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.miniblog.data.JsonParser
import com.example.miniblog.data.LocalPostStore
import com.example.miniblog.databinding.ActivityMainBinding
import com.example.miniblog.model.Post

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val postStore by lazy { LocalPostStore(this) }
    private var allPosts = listOf<Post>()
    private lateinit var adapter: PostAdapter
    private var currentSearchQuery = ""

    private val createPostLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleCreatedPost(
                result.data?.getStringExtra(CreatePostActivity.EXTRA_CREATED_POST)
            )
        }
    }

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val deletedId = result.data?.getIntExtra("DELETED_POST_ID", -1) ?: -1
            if (deletedId != -1) {
                postStore.removePost(deletedId)
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
        supportActionBar?.title = "MiniBlog"

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
            loadPosts()
            binding.swipeRefresh.isRefreshing = false
        }

        binding.fabCreate.setOnClickListener {
            createPostLauncher.launch(Intent(this, CreatePostActivity::class.java))
        }

        binding.buttonCreatePost.setOnClickListener {
            createPostLauncher.launch(Intent(this, CreatePostActivity::class.java))
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

        loadPosts()
    }

    /**
     * Inserts a successfully published post at the TOP of the feed and
     * persists it, so it stays first across search, refresh and restarts.
     */
    private fun handleCreatedPost(postJson: String?) {
        if (postJson == null) return
        val newPost = try {
            JsonParser.parsePost(postJson)
        } catch (e: Exception) {
            null
        } ?: return

        postStore.addPost(newPost)
        // Newest first: the freshly created post carries the latest timestamp.
        allPosts = (listOf(newPost) + allPosts).sortedByDescending { it.createdAt }
        filterPosts()
        binding.recyclerViewPosts.post {
            binding.recyclerViewPosts.scrollToPosition(0)
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
        AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> loadPosts()
                    1 -> Toast.makeText(
                        this,
                        "MiniBlog v1.0\nYour posts are saved on this device.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    /** Loads only user-created posts, newest first. */
    private fun loadPosts() {
        allPosts = postStore.getPosts()
        filterPosts()
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
        binding.textViewBrandName.visibility = View.VISIBLE
        binding.textViewTagline.visibility = View.VISIBLE
        binding.textViewEmptyTitle.visibility = View.VISIBLE
        binding.textViewEmptySubtitle.visibility = View.VISIBLE
        binding.buttonCreatePost.visibility = View.VISIBLE
        binding.textViewMessage.visibility = View.GONE
        binding.recyclerViewPosts.visibility = View.GONE
    }

    private fun showSearchEmpty() {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.imageViewEmptyLogo.visibility = View.GONE
        binding.textViewBrandName.visibility = View.GONE
        binding.textViewTagline.visibility = View.GONE
        binding.textViewEmptyTitle.visibility = View.GONE
        binding.textViewEmptySubtitle.visibility = View.GONE
        binding.buttonCreatePost.visibility = View.GONE
        binding.textViewMessage.visibility = View.VISIBLE
        binding.textViewMessage.text = "No posts match \"$currentSearchQuery\""
        binding.recyclerViewPosts.visibility = View.GONE
    }

    private fun hideEmpty() {
        binding.layoutEmpty.visibility = View.GONE
        binding.recyclerViewPosts.visibility = View.VISIBLE
    }
}
