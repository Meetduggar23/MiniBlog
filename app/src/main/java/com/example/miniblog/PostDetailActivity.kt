package com.example.miniblog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.miniblog.data.JsonParser
import com.example.miniblog.data.LocalPostStore
import com.example.miniblog.data.PostRepository
import com.example.miniblog.data.PostStatsStore
import com.example.miniblog.databinding.ActivityPostDetailBinding
import com.example.miniblog.model.Comment
import com.example.miniblog.model.Post
import com.example.miniblog.network.NetworkResult
import com.example.miniblog.network.NetworkUtils
import com.example.miniblog.util.PostDateFormatter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class PostDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostDetailBinding
    private val repository = PostRepository()
    private val postStore by lazy { LocalPostStore(this) }
    private val statsStore by lazy { PostStatsStore(this) }
    private val commentList = mutableListOf<Comment>()
    private lateinit var adapter: CommentAdapter

    private var currentPost: Post? = null
    private var currentPostId = -1
    private var postTitle = ""
    private var postBody = ""
    private var postCreatedAt = 0L

    /** Edits performed from the detail screen are forwarded to the feed. */
    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val postJson =
                result.data?.getStringExtra(CreatePostActivity.EXTRA_CREATED_POST)
            if (postJson != null) {
                val edited = try {
                    JsonParser.parsePost(postJson)
                } catch (e: Exception) {
                    null
                }
                if (edited != null) {
                    // Merge flags from existing post
                    val existing = currentPost
                    val merged = existing?.copy(
                        title = edited.title,
                        body = edited.body,
                        tags = edited.tags,
                        createdAt = edited.createdAt
                    ) ?: edited
                    currentPost = merged
                    postTitle = merged.title
                    postBody = merged.body
                    postCreatedAt = merged.createdAt
                    refreshDetail(merged)
                    setResult(
                        RESULT_OK,
                        Intent().putExtra("UPDATED_POST_JSON", JsonParser.postToJson(merged))
                    )
                }
            }
        }
    }

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
        supportActionBar?.title = getString(R.string.post)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Read post data from the JSON intent extra (sent by MainActivity)
        val postJson = intent.getStringExtra("POST_JSON")
        if (postJson != null) {
            try {
                val post = JsonParser.parsePost(postJson)
                currentPost = post
                currentPostId = post.id
                postTitle = post.title
                postBody = post.body
                postCreatedAt = post.createdAt
                refreshDetail(post)
                // Record view
                statsStore.recordView(post.id)
            } catch (e: Exception) {
                // fallback to individual extras
                readFallbackExtras()
            }
        } else {
            readFallbackExtras()
            currentPost?.let { statsStore.recordView(it.id) }
        }

        binding.buttonRetryComments.setOnClickListener {
            currentPost?.let { loadComments(it.id) }
        }

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
                            postId = currentPostId,
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
                    showEmptyMessage(getString(R.string.no_comments))
                }
            } else if (currentPostId != -1) {
                loadComments(currentPostId)
            }
        } else if (currentPostId != -1) {
            loadComments(currentPostId)
        }
    }

    private fun readFallbackExtras() {
        currentPostId = intent.getIntExtra("POST_ID", -1)
        postTitle = intent.getStringExtra("POST_TITLE") ?: ""
        postBody = intent.getStringExtra("POST_BODY") ?: ""
        postCreatedAt = intent.getLongExtra("POST_CREATED_AT", 0L)
        binding.textViewDetailTitle.text = postTitle.replaceFirstChar { it.uppercase() }
        binding.textViewDetailBody.text = postBody
        if (postCreatedAt > 0) {
            binding.textViewDetailDate.visibility = View.VISIBLE
            binding.textViewDetailDate.text = PostDateFormatter.format(postCreatedAt)
        }
    }

    private fun refreshDetail(post: Post) {
        binding.textViewDetailTitle.text = post.title.replaceFirstChar { it.uppercase() }
        binding.textViewDetailBody.text = post.body

        // Date
        if (post.createdAt > 0) {
            binding.textViewDetailDate.visibility = View.VISIBLE
            binding.textViewDetailDate.text = PostDateFormatter.format(post.createdAt)
        } else {
            binding.textViewDetailDate.visibility = View.GONE
        }

        // Tags
        if (post.tags.isNotEmpty()) {
            binding.textViewDetailTags.visibility = View.VISIBLE
            binding.textViewDetailTags.text = post.tags.joinToString("  ") { "#$it" }
        } else {
            binding.textViewDetailTags.visibility = View.GONE
        }

        // View count
        binding.textViewDetailViews.text = statsStore.viewCount(post.id).toString()

        // Pin indicator
        if (post.isPinned) {
            binding.textViewPinned.visibility = View.VISIBLE
        } else {
            binding.textViewPinned.visibility = View.GONE
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

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val post = currentPost
        if (post != null) {
            // Update pin menu item text
            menu.findItem(R.id.action_pin)?.title =
                getString(if (post.isPinned) R.string.unpin_post else R.string.pin_post)
            // Update bookmark menu item text
            menu.findItem(R.id.action_bookmark)?.title =
                getString(if (post.isBookmarked) R.string.remove_saved_post else R.string.save_post)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                openEditor()
                true
            }
            R.id.action_delete -> {
                showDeleteConfirmation()
                true
            }
            R.id.action_share -> {
                sharePost()
                true
            }
            R.id.action_copy -> {
                copyPost()
                true
            }
            R.id.action_pin -> {
                togglePin()
                true
            }
            R.id.action_bookmark -> {
                toggleBookmark()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openEditor() {
        val intent = Intent(this, CreatePostActivity::class.java)
        intent.putExtra(CreatePostActivity.EXTRA_EDIT_POST_ID, currentPostId)
        intent.putExtra(CreatePostActivity.EXTRA_EDIT_TITLE, postTitle)
        intent.putExtra(CreatePostActivity.EXTRA_EDIT_BODY, postBody)
        intent.putExtra(CreatePostActivity.EXTRA_EDIT_CREATED_AT, postCreatedAt)
        currentPost?.let {
            intent.putExtra(CreatePostActivity.EXTRA_EDIT_TAGS, it.tags.joinToString(","))
        }
        editLauncher.launch(intent)
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_dialog_title)
            .setMessage(R.string.delete_dialog_message)
            .setPositiveButton(R.string.delete) { _, _ -> deletePost() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deletePost() {
        if (currentPostId < Post.LOCAL_ID_BASE &&
            !NetworkUtils.isNetworkAvailable(this)
        ) {
            Snackbar.make(binding.root, R.string.offline_hint, Snackbar.LENGTH_SHORT).show()
            return
        }
        if (currentPostId >= Post.LOCAL_ID_BASE) {
            onPostDeleted()
            return
        }
        binding.progressBarComments.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val result = repository.deletePost(currentPostId)) {
                is NetworkResult.Success -> onPostDeleted()
                is NetworkResult.Error -> {
                    binding.progressBarComments.visibility = View.GONE
                    Snackbar.make(
                        this@PostDetailActivity.binding.root,
                        result.message,
                        Snackbar.LENGTH_SHORT
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
        Snackbar.make(binding.root, R.string.post_deleted, Snackbar.LENGTH_SHORT).show()
        finish()
    }

    private fun sharePost() {
        val shareText = "$postTitle\n\n$postBody"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, postTitle)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_post_title)))
    }

    private fun copyPost() {
        val clipText = "$postTitle\n\n$postBody"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("post", clipText)
        clipboard.setPrimaryClip(clip)
        Snackbar.make(binding.root, R.string.post_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun togglePin() {
        val post = currentPost ?: return
        val updated = post.copy(isPinned = !post.isPinned)
        currentPost = updated
        postStore.addPost(updated)
        refreshDetail(updated)
        invalidateOptionsMenu()
        val msg = if (updated.isPinned) R.string.pinned else R.string.unpin_post
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
        setResult(RESULT_OK, Intent().putExtra("UPDATED_POST_JSON", JsonParser.postToJson(updated)))
    }

    private fun toggleBookmark() {
        val post = currentPost ?: return
        val updated = post.copy(isBookmarked = !post.isBookmarked)
        currentPost = updated
        postStore.addPost(updated)
        refreshDetail(updated)
        invalidateOptionsMenu()
        val msg = if (updated.isBookmarked) R.string.post_saved else R.string.post_removed_from_saved
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
        setResult(RESULT_OK, Intent().putExtra("UPDATED_POST_JSON", JsonParser.postToJson(updated)))
    }

    private fun loadComments(postId: Int) {
        if (postId >= Post.LOCAL_ID_BASE) {
            showEmptyMessage(getString(R.string.no_comments))
            return
        }
        binding.progressBarComments.visibility = View.VISIBLE
        binding.textViewCommentsMessage.visibility = View.GONE
        binding.buttonRetryComments.visibility = View.GONE
        lifecycleScope.launch {
            when (val result = repository.getComments(postId)) {
                is NetworkResult.Success -> {
                    binding.progressBarComments.visibility = View.GONE
                    commentList.clear()
                    commentList.addAll(result.data)
                    adapter.notifyDataSetChanged()
                    if (commentList.isEmpty()) {
                        showEmptyMessage(getString(R.string.no_comments))
                    }
                }
                is NetworkResult.Error -> {
                    binding.progressBarComments.visibility = View.GONE
                    binding.textViewCommentsMessage.setTextColor(
                        ContextCompat.getColor(
                            this@PostDetailActivity, R.color.error
                        )
                    )
                    binding.textViewCommentsMessage.text =
                        getString(R.string.comments_load_failed)
                    binding.textViewCommentsMessage.visibility = View.VISIBLE
                    binding.buttonRetryComments.visibility = View.VISIBLE
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
