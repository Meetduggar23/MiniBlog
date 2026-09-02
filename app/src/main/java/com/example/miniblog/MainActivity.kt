package com.example.miniblog

import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.miniblog.data.AppPreferences
import com.example.miniblog.data.DraftStore
import com.example.miniblog.data.JsonParser
import com.example.miniblog.data.LocalPostStore
import com.example.miniblog.data.PostRepository
import com.example.miniblog.data.PostStatsStore
import com.example.miniblog.databinding.ActivityMainBinding
import com.example.miniblog.model.Draft
import com.example.miniblog.model.Post
import com.example.miniblog.network.NetworkResult
import com.example.miniblog.network.NetworkUtils
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val postStore by lazy { LocalPostStore(this) }
    private val draftStore by lazy { DraftStore(this) }
    private val statsStore by lazy { PostStatsStore(this) }
    private val appPrefs by lazy { AppPreferences(this) }
    private val repository = PostRepository()

    private var allPosts = listOf<Post>()
    private var drafts = listOf<Draft>()
    private lateinit var adapter: PostAdapter
    private lateinit var draftAdapter: DraftAdapter
    private var currentSearchQuery = ""
    private var currentTab = TAB_ALL
    private var sortOrder = AppPreferences.SORT_NEWEST

    /** Id of a delete currently in flight — guards against double actions. */
    private var deletingPostId: Int? = null

    /** Bulk-select (ActionMode) state. */
    private var selectionActionMode: ActionMode? = null

    private val createPostLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val postJson =
                result.data?.getStringExtra(CreatePostActivity.EXTRA_CREATED_POST)
            val isEdit =
                result.data?.getBooleanExtra(CreatePostActivity.EXTRA_IS_EDIT, false) ?: false
            val draftToDelete =
                result.data?.getIntExtra(CreatePostActivity.EXTRA_DRAFT_TO_DELETE, -1) ?: -1
            if (draftToDelete != -1) {
                draftStore.removeDraft(draftToDelete)
            }
            if (isEdit) {
                handleEditedPost(postJson)
            } else {
                handleCreatedPost(postJson)
            }
            loadDrafts()
        }
    }

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Bookmark/pin toggles made inside Post Detail.
            result.data?.getStringExtra("UPDATED_POST_JSON")?.let { json ->
                parsePostOrNull(json)?.let { applyPostUpdate(it, showFeedback = false) }
                return@registerForActivityResult
            }
            // Edit performed from Post Detail — sync the feed as well.
            result.data?.getStringExtra(CreatePostActivity.EXTRA_CREATED_POST)?.let { json ->
                handleEditedPost(json)
                return@registerForActivityResult
            }
            val deletedId = result.data?.getIntExtra("DELETED_POST_ID", -1) ?: -1
            if (deletedId != -1) {
                allPosts.find { it.id == deletedId }?.let { softDeletePost(it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "MiniBlog"
        sortOrder = appPrefs.getSortOrder()

        adapter = PostAdapter(
            stats = statsStore,
            onItemClick = { post -> openPostDetail(post) },
            onDeleteClick = { post -> confirmDeletePost(post) },
            onLikeClick = { post, position -> toggleLike(post, position) },
            onBookmarkClick = { post, position -> toggleBookmark(post, position) },
            onLongPressToSelect = { post -> startSelectionMode(post) },
            onSelectionChanged = { count -> updateSelectionTitle(count) }
        )
        binding.recyclerViewPosts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPosts.adapter = adapter

        draftAdapter = DraftAdapter(
            onItemClick = { draft -> openDraft(draft) },
            onDeleteClick = { draft -> confirmDeleteDraft(draft) }
        )
        binding.recyclerViewDrafts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewDrafts.adapter = draftAdapter

        attachSwipeToDelete()

        setupTabs()
        setupSearch()

        binding.swipeRefresh.setOnRefreshListener {
            // Refresh from network + local
            loadPosts(onComplete = {
                binding.swipeRefresh.isRefreshing = false
            })
            loadDrafts()
        }

        binding.fabCreate.setOnClickListener {
            createPostLauncher.launch(Intent(this, CreatePostActivity::class.java))
        }

        binding.buttonCreatePost.setOnClickListener {
            createPostLauncher.launch(Intent(this, CreatePostActivity::class.java))
        }

        loadPosts()
        loadDrafts()
    }

    // ---------------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------------

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_all))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_saved))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_drafts))
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                refreshUi()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ---------------------------------------------------------------------
    // Search + recent searches
    // ---------------------------------------------------------------------

    private fun setupSearch() {
        binding.editTextSearch.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    currentSearchQuery = s?.toString()?.trim() ?: ""
                    // A changed filter invalidates the current selection, so
                    // leave selection mode for predictable behaviour.
                    exitSelectionMode()
                    refreshUi()
                    updateRecentSearchesVisibility()
                }
            }
        )

        binding.editTextSearch.setOnFocusChangeListener { _, hasFocus ->
            updateRecentSearchesVisibility()
        }

        binding.editTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                appPrefs.addRecentSearch(currentSearchQuery)
                refreshUi()
                binding.editTextSearch.clearFocus()
                true
            } else false
        }

        binding.textViewClearSearches.setOnClickListener {
            appPrefs.clearRecentSearches()
            updateRecentSearchesVisibility()
        }
    }

    private fun updateRecentSearchesVisibility() {
        val show = binding.editTextSearch.hasFocus() &&
                currentSearchQuery.isEmpty() &&
                appPrefs.getRecentSearches().isNotEmpty()
        if (show) {
            populateRecentSearches()
            binding.cardRecentSearches.visibility = View.VISIBLE
        } else {
            binding.cardRecentSearches.visibility = View.GONE
        }
    }

    private fun populateRecentSearches() {
        binding.containerRecentSearches.removeAllViews()
        appPrefs.getRecentSearches().forEach { query ->
            val item = TextView(this).apply {
                text = query
                textSize = 15f
                setPadding(8, 20, 8, 20)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setOnClickListener {
                    binding.editTextSearch.setText(query)
                    binding.editTextSearch.setSelection(query.length)
                }
            }
            binding.containerRecentSearches.addView(item)
        }
    }

    // ---------------------------------------------------------------------
    // Data loading
    // ---------------------------------------------------------------------

    /**
     * Loads posts from the remote API (network) and merges them with
     * locally-created posts. Shows a loading state while fetching.
     *
     * Flow: Connectivity check → HttpURLConnection GET /posts →
     *       JSONArray parsing → merge with local → display
     */
    private fun loadPosts(onComplete: (() -> Unit)? = null) {
        // Check connectivity before attempting network request
        if (!NetworkUtils.isNetworkAvailable(this)) {
            // Offline: show local posts only
            allPosts = postStore.getPosts()
            refreshUi()
            onComplete?.invoke()
            return
        }

        // Show loading state while network request is in progress
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.recyclerViewPosts.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = repository.getAllPosts()) {
                is NetworkResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    // Merge remote posts with locally-created posts.
                    // Remote posts get createdAt = current time so newest-first
                    // ordering is consistent. User-created posts keep their
                    // original timestamps.
                    val remotePosts = result.data
                    val localPosts = postStore.getPosts()
                        .filter { it.id >= Post.LOCAL_ID_BASE }
                    allPosts = (remotePosts + localPosts)
                        .distinctBy { it.id }
                        .sortedByDescending { it.createdAt }
                    refreshUi()
                    onComplete?.invoke()
                }
                is NetworkResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    // Network failed — fall back to local posts
                    allPosts = postStore.getPosts()
                    if (allPosts.isEmpty()) {
                        // Show error state with retry option
                        showEmptyState(
                            result.message,
                            getString(R.string.retry_hint),
                            showBrand = false, showCreateButton = false
                        )
                        binding.buttonCreatePost.visibility = View.GONE
                        binding.textViewMessage.visibility = View.VISIBLE
                        binding.textViewMessage.text = getString(R.string.retry)
                        binding.textViewMessage.setOnClickListener {
                            loadPosts()
                        }
                    } else {
                        refreshUi()
                    }
                    Snackbar.make(
                        binding.root, result.message, Snackbar.LENGTH_SHORT
                    ).show()
                    onComplete?.invoke()
                }
            }
        }
    }

    private fun loadDrafts() {
        drafts = draftStore.getDrafts()
        draftAdapter.submitList(drafts)
        refreshUi()
    }

    // ---------------------------------------------------------------------
    // UI state (tabs + search + sort + empty states)
    // ---------------------------------------------------------------------

    private fun refreshUi() {
        if (currentTab == TAB_DRAFTS) {
            binding.recyclerViewPosts.visibility = View.GONE
            binding.recyclerViewDrafts.visibility = View.VISIBLE
            if (drafts.isEmpty()) {
                showEmptyState(
                    getString(R.string.no_drafts),
                    getString(R.string.no_drafts_hint),
                    showBrand = false,
                    showCreateButton = false
                )
            } else {
                hideEmpty()
            }
            return
        }

        binding.recyclerViewDrafts.visibility = View.GONE

        val tabPosts = if (currentTab == TAB_SAVED) {
            allPosts.filter { it.isBookmarked }
        } else {
            allPosts
        }

        val filtered = if (currentSearchQuery.isEmpty()) {
            tabPosts
        } else {
            tabPosts.filter { post ->
                post.title.contains(currentSearchQuery, ignoreCase = true) ||
                        post.body.contains(currentSearchQuery, ignoreCase = true) ||
                        post.tags.any { it.contains(currentSearchQuery, ignoreCase = true) }
            }
        }

        adapter.submitList(sortPosts(filtered))

        when {
            currentSearchQuery.isNotEmpty() && filtered.isEmpty() -> {
                showEmptyState(
                    getString(R.string.no_posts_found), "",
                    showBrand = false, showCreateButton = false
                )
            }
            currentTab == TAB_SAVED && tabPosts.isEmpty() -> {
                showEmptyState(
                    getString(R.string.no_saved_posts),
                    getString(R.string.no_saved_posts_hint),
                    showBrand = false, showCreateButton = false
                )
            }
            allPosts.isEmpty() -> {
                showEmptyState(
                    getString(R.string.empty_feed_title),
                    getString(R.string.empty_feed_subtitle),
                    showBrand = true, showCreateButton = true
                )
            }
            else -> hideEmpty()
        }
    }

    /**
     * Pinned posts always come first; within each group the chosen order is
     * applied. Default (and persisted) order is Newest.
     */
    private fun sortPosts(list: List<Post>): List<Post> {
        val byOrder = when (sortOrder) {
            AppPreferences.SORT_OLDEST ->
                compareBy<Post> { it.createdAt }
            AppPreferences.SORT_MOST_LIKED ->
                compareByDescending<Post> { statsStore.likeCount(it.id) }
            AppPreferences.SORT_MOST_VIEWED ->
                compareByDescending<Post> { statsStore.viewCount(it.id) }
            else -> compareByDescending<Post> { it.createdAt }
        }
        return list.sortedWith(
            compareByDescending<Post> { it.isPinned }.then(byOrder)
                .then(compareByDescending<Post> { it.createdAt })
        )
    }

    private fun showEmptyState(title: String, subtitle: String, showBrand: Boolean, showCreateButton: Boolean) {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.imageViewEmptyLogo.visibility = if (showBrand) View.VISIBLE else View.GONE
        binding.textViewBrandName.visibility = if (showBrand) View.VISIBLE else View.GONE
        binding.textViewTagline.visibility = if (showBrand) View.VISIBLE else View.GONE
        binding.textViewEmptyTitle.text = title
        binding.textViewEmptyTitle.visibility = View.VISIBLE
        binding.textViewEmptySubtitle.text = subtitle
        binding.textViewEmptySubtitle.visibility =
            if (subtitle.isEmpty()) View.GONE else View.VISIBLE
        binding.buttonCreatePost.visibility =
            if (showCreateButton) View.VISIBLE else View.GONE
        binding.textViewMessage.visibility = View.GONE
        binding.recyclerViewPosts.visibility = View.GONE
    }

    private fun hideEmpty() {
        binding.layoutEmpty.visibility = View.GONE
        binding.recyclerViewPosts.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------

    private fun openPostDetail(post: Post) {
        val intent = Intent(this, PostDetailActivity::class.java)
        intent.putExtra("POST_JSON", JsonParser.postToJson(post))
        detailLauncher.launch(intent)
    }

    private fun openDraft(draft: Draft) {
        val intent = Intent(this, CreatePostActivity::class.java)
        intent.putExtra(CreatePostActivity.EXTRA_DRAFT_ID, draft.id)
        createPostLauncher.launch(intent)
    }

    // ---------------------------------------------------------------------
    // Create / edit results
    // ---------------------------------------------------------------------

    /**
     * Inserts a successfully published post at the TOP of the feed and
     * persists it, so it stays first across search, refresh and restarts.
     */
    private fun handleCreatedPost(postJson: String?) {
        if (postJson == null) return
        val newPost = parsePostOrNull(postJson) ?: return

        postStore.addPost(newPost)
        // Newest first: the freshly created post carries the latest timestamp.
        allPosts = (listOf(newPost) + allPosts)
        if (currentTab != TAB_DRAFTS) {
            binding.recyclerViewPosts.post {
                binding.recyclerViewPosts.scrollToPosition(0)
            }
        }
        Snackbar.make(binding.root, R.string.post_published, Snackbar.LENGTH_SHORT).show()
        refreshUi()
    }

    /**
     * Replaces an edited post in place — its original createdAt (and therefore
     * its position in the newest-first order) is preserved by the editor.
     * Bookmark/pin/trash flags are merged from the existing copy.
     */
    private fun handleEditedPost(postJson: String?) {
        if (postJson == null) return
        val edited = parsePostOrNull(postJson) ?: return
        val existing = allPosts.find { it.id == edited.id }
        val merged = existing?.copy(
            title = edited.title,
            body = edited.body,
            tags = edited.tags,
            createdAt = edited.createdAt
        ) ?: edited
        applyPostUpdate(merged, showFeedback = true)
    }

    private fun applyPostUpdate(post: Post, showFeedback: Boolean) {
        postStore.addPost(post)
        allPosts = allPosts.map { if (it.id == post.id) post else it }
            .let { list ->
                if (list.any { it.id == post.id }) list else list + post
            }
        refreshUi()
        if (showFeedback) {
            Snackbar.make(binding.root, R.string.post_updated, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun parsePostOrNull(postJson: String): Post? = try {
        JsonParser.parsePost(postJson)
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------------
    // Bulk select mode (ActionMode)
    // ---------------------------------------------------------------------

    private val selectionCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_selection, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Hide "Delete Selected" when nothing is selected.
            val deleteItem = menu.findItem(R.id.action_delete_selected)
            deleteItem?.isVisible = adapter.selectedCount() > 0
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_select_all -> {
                    val n = adapter.selectAll()
                    if (n == 0) {
                        Snackbar.make(
                            binding.root, R.string.no_posts, Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    mode.invalidate()
                    true
                }
                R.id.action_deselect_all -> {
                    adapter.deselectAll()
                    mode.invalidate()
                    true
                }
                R.id.action_delete_selected -> {
                    confirmDeleteSelected()
                    true
                }
                R.id.action_close_selection -> {
                    exitSelectionMode()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            adapter.exitSelectionMode()
            selectionActionMode = null
        }
    }

    /** Long-press on a card: begin bulk selection with that post selected. */
    private fun startSelectionMode(post: Post) {
        if (selectionActionMode != null) return
        selectionActionMode = startActionMode(selectionCallback)
        adapter.enterSelectionMode(post.id)
    }

    /** Refreshes the ActionMode title / menu when the selection changes. */
    private fun updateSelectionTitle(count: Int) {
        val mode = selectionActionMode ?: return
        mode.title = getString(R.string.selected_count, count)
        mode.invalidate()
    }

    private fun exitSelectionMode() {
        selectionActionMode?.finish()
        selectionActionMode = null
        adapter.exitSelectionMode()
    }

    private fun confirmDeleteSelected() {
        val ids = adapter.selectedPostIds()
        if (ids.isEmpty()) return
        val posts = allPosts.filter { ids.contains(it.id) }
        if (posts.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.delete_selected_confirm, posts.size))
            .setPositiveButton(R.string.delete) { _, _ -> softDeleteSelected(posts) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Soft-deletes the selected posts (reversible via Undo). */
    private fun softDeleteSelected(posts: List<Post>) {
        if (posts.isEmpty()) return
        posts.forEach { post ->
            postStore.addPost(post.copy(deletedAt = System.currentTimeMillis()))
        }
        val deletedIds = posts.map { it.id }.toSet()
        allPosts = allPosts.filter { !deletedIds.contains(it.id) }
        exitSelectionMode()
        refreshUi()
        Snackbar.make(
            binding.root, getString(R.string.posts_deleted, posts.size), Snackbar.LENGTH_LONG
        ).setAction(R.string.undo) { restoreSelectedPosts(posts) }.show()
    }

    private fun restoreSelectedPosts(posts: List<Post>) {
        posts.forEach { post ->
            postStore.addPost(post.copy(deletedAt = null))
        }
        loadPosts()
        Snackbar.make(binding.root, R.string.posts_restored, Snackbar.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------------
    // Like / bookmark
    // ---------------------------------------------------------------------

    private fun toggleLike(post: Post, position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        // Local like state; no network, no duplicate toggles possible.
        statsStore.toggleLike(post.id)
        adapter.notifyItemChanged(position)
    }

    private fun toggleBookmark(post: Post, position: Int) {
        val updated = post.copy(isBookmarked = !post.isBookmarked)
        postStore.addPost(updated)
        allPosts = allPosts.map { if (it.id == updated.id) updated else it }
        refreshUi()
        Snackbar.make(
            binding.root,
            if (updated.isBookmarked) R.string.post_saved else R.string.post_removed_from_saved,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    // ---------------------------------------------------------------------
    // Delete (soft → Trash) + restore
    // ---------------------------------------------------------------------

    /**
     * Swipe a card left/right to trigger the same delete flow as the trash
     * icon. The card snaps back immediately; deletion only happens after the
     * confirmation dialog is accepted.
     */
    private fun attachSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val post = adapter.currentList.getOrNull(position) ?: return
                // Restore the swiped card; it moves to Trash only after
                // confirmation.
                adapter.notifyItemChanged(position)
                confirmDeletePost(post)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerViewPosts)
    }

    private fun confirmDeletePost(post: Post) {
        if (deletingPostId != null) return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_dialog_title)
            .setMessage(R.string.delete_dialog_message)
            .setPositiveButton(R.string.delete) { _, _ -> softDeletePost(post) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Soft delete: the post moves to the Trash (kept locally with a
     * deletedAt timestamp) and can be restored. All feed posts are managed
     * locally, so this is fully reversible — no fake server restore.
     */
    private fun softDeletePost(post: Post) {
        if (deletingPostId != null) return
        deletingPostId = post.id
        postStore.addPost(post.copy(deletedAt = System.currentTimeMillis()))
        allPosts = allPosts.filter { it.id != post.id }
        deletingPostId = null
        refreshUi()
        Snackbar.make(binding.root, R.string.post_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { restorePost(post) }
            .show()
    }

    private fun restorePost(post: Post) {
        postStore.addPost(post.copy(deletedAt = null))
        loadPosts()
        Snackbar.make(binding.root, R.string.post_restored, Snackbar.LENGTH_SHORT).show()
    }

    /** Delete all currently shown feed posts (not the trash) with confirmation. */
    private fun confirmDeleteAll() {
        val toDelete = allPosts
        if (toDelete.isEmpty()) {
            Snackbar.make(binding.root, R.string.no_posts, Snackbar.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_all)
            .setMessage(getString(R.string.delete_all_confirm, toDelete.size))
            .setPositiveButton(R.string.delete) { _, _ -> softDeleteAll(toDelete) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Soft-deletes every shown feed post (reversible via Undo). */
    private fun softDeleteAll(posts: List<Post>) {
        if (posts.isEmpty()) return
        posts.forEach { post ->
            postStore.addPost(post.copy(deletedAt = System.currentTimeMillis()))
        }
        val deletedIds = posts.map { it.id }.toSet()
        allPosts = allPosts.filter { !deletedIds.contains(it.id) }
        refreshUi()
        Snackbar.make(
            binding.root, getString(R.string.posts_deleted, posts.size), Snackbar.LENGTH_LONG
        ).setAction(R.string.undo) { restoreSelectedPosts(posts) }.show()
    }

    private fun confirmDeleteDraft(draft: Draft) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_draft_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                draftStore.removeDraft(draft.id)
                loadDrafts()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------------
    // Toolbar menu: sort, trash, my blog, theme, about
    // ---------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadPosts()
                loadDrafts()
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
        val options = arrayOf(
            getString(R.string.sort),
            getString(R.string.my_blog),
            getString(R.string.trash),
            getString(R.string.theme),
            getString(R.string.delete_all),
            getString(R.string.about)
        )
        AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSortMenu()
                    1 -> startActivity(Intent(this, StatsActivity::class.java))
                    2 -> startActivity(Intent(this, TrashActivity::class.java))
                    3 -> showThemeMenu()
                    4 -> confirmDeleteAll()
                    5 -> Toast.makeText(
                        this,
                        "MiniBlog v1.0\nYour posts are saved on this device.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    private fun showSortMenu() {
        val options = arrayOf(
            getString(R.string.sort_newest),
            getString(R.string.sort_oldest),
            getString(R.string.sort_most_liked),
            getString(R.string.sort_most_viewed)
        )
        val checked = when (sortOrder) {
            AppPreferences.SORT_OLDEST -> 1
            AppPreferences.SORT_MOST_LIKED -> 2
            AppPreferences.SORT_MOST_VIEWED -> 3
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.sort)
            .setSingleChoiceItems(options, checked) { dialog, which ->
                sortOrder = when (which) {
                    1 -> AppPreferences.SORT_OLDEST
                    2 -> AppPreferences.SORT_MOST_LIKED
                    3 -> AppPreferences.SORT_MOST_VIEWED
                    else -> AppPreferences.SORT_NEWEST
                }
                appPrefs.setSortOrder(sortOrder)
                dialog.dismiss()
                refreshUi() // in-memory reorder; no reload
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThemeMenu() {
        val options = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val checked = when (appPrefs.getThemeMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(options, checked) { dialog, which ->
                val mode = when (which) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                appPrefs.setThemeMode(mode)
                AppCompatDelegate.setDefaultNightMode(mode)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val TAB_ALL = 0
        private const val TAB_SAVED = 1
        private const val TAB_DRAFTS = 2
    }
}
