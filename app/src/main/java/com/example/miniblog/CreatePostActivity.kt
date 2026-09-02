package com.example.miniblog

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.miniblog.data.DraftStore
import com.example.miniblog.data.JsonParser
import com.example.miniblog.data.LocalPostStore
import com.example.miniblog.data.PostRepository
import com.example.miniblog.databinding.ActivityCreatePostBinding
import com.example.miniblog.model.Draft
import com.example.miniblog.model.Post
import com.example.miniblog.network.NetworkResult
import com.example.miniblog.network.NetworkUtils
import kotlinx.coroutines.launch

class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private val repository = PostRepository()
    private val postStore by lazy { LocalPostStore(this) }
    private val draftStore by lazy { DraftStore(this) }

    private val autosaveHandler = Handler(Looper.getMainLooper())

    private var editPostId = -1
    private var editCreatedAt = 0L
    private var editTags = ""
    private var draftId = -1
    private var autosaveDraftId = -1

    private var originalTitle = ""
    private var originalBody = ""
    private var originalTags = ""

    private val isEditMode: Boolean get() = editPostId != -1
    private val isDraftMode: Boolean get() = draftId != -1

    private val autosaveRunnable = Runnable { autosaveDraft() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { handleBack() }

        editPostId = intent.getIntExtra(EXTRA_EDIT_POST_ID, -1)
        draftId = intent.getIntExtra(EXTRA_DRAFT_ID, -1)

        when {
            isEditMode -> {
                supportActionBar?.title = getString(R.string.edit_post)
                originalTitle = intent.getStringExtra(EXTRA_EDIT_TITLE) ?: ""
                originalBody = intent.getStringExtra(EXTRA_EDIT_BODY) ?: ""
                editTags = intent.getStringExtra(EXTRA_EDIT_TAGS) ?: ""
                originalTags = editTags
                editCreatedAt = intent.getLongExtra(
                    EXTRA_EDIT_CREATED_AT, System.currentTimeMillis()
                )
                binding.editTextTitle.setText(originalTitle)
                binding.editTextBody.setText(originalBody)
                binding.editTextTags.setText(originalTags)
                binding.buttonSubmit.text = getString(R.string.save_changes)
            }
            isDraftMode -> {
                supportActionBar?.title = getString(R.string.create_post)
                draftStore.getDraft(draftId)?.let { draft ->
                    originalTitle = draft.title
                    originalBody = draft.body
                    originalTags = draft.tags.joinToString(", ")
                    autosaveDraftId = draft.id
                    binding.editTextTitle.setText(draft.title)
                    binding.editTextBody.setText(draft.body)
                    binding.editTextTags.setText(originalTags)
                }
            }
            else -> {
                supportActionBar?.title = getString(R.string.create_post)
            }
        }

        setupWatchers()

        // Back (gesture/button) goes through the same guard as the toolbar.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })

        binding.buttonSubmit.setOnClickListener { submitPost() }
        updateCounter()
    }

    // ---------------------------------------------------------------------
    // Typing: counters + debounced autosave (create/draft modes only)
    // ---------------------------------------------------------------------

    private fun setupWatchers() {
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateCounter()
                scheduleAutosave()
            }
        }
        binding.editTextTitle.addTextChangedListener(watcher)
        binding.editTextBody.addTextChangedListener(watcher)
    }

    private fun updateCounter() {
        val text = "${binding.editTextTitle.text} ${binding.editTextBody.text}".trim()
        val words = if (text.isEmpty()) 0 else text.split(Regex("\\s+")).size
        val chars = "${binding.editTextTitle.text}${binding.editTextBody.text}".length
        binding.textCounter.text = getString(R.string.words_chars, words, chars)
    }

    private fun scheduleAutosave() {
        if (isEditMode) return // edit mode uses the unsaved-changes dialog
        autosaveHandler.removeCallbacks(autosaveRunnable)
        val title = binding.editTextTitle.text.toString().trim()
        val body = binding.editTextBody.text.toString().trim()
        if (title.isEmpty() && body.isEmpty()) {
            binding.textAutosaveStatus.visibility = View.GONE
            return
        }
        autosaveHandler.postDelayed(autosaveRunnable, AUTOSAVE_DELAY_MS)
    }

    /** Debounced draft write (~800ms after the last keystroke). */
    private fun autosaveDraft() {
        val title = binding.editTextTitle.text.toString().trim()
        val body = binding.editTextBody.text.toString().trim()
        if (title.isEmpty() && body.isEmpty()) return

        binding.textAutosaveStatus.text = getString(R.string.autosave_saving)
        binding.textAutosaveStatus.visibility = View.VISIBLE

        val id = when {
            isDraftMode -> draftId
            autosaveDraftId != -1 -> autosaveDraftId
            else -> {
                val newId = draftStore.nextDraftId()
                autosaveDraftId = newId
                newId
            }
        }
        draftStore.saveDraft(
            Draft(
                id = id,
                title = title,
                body = body,
                tags = parseTagsInput(),
                updatedAt = System.currentTimeMillis()
            )
        )
        binding.textAutosaveStatus.text = getString(R.string.autosave_saved)
    }

    private fun parseTagsInput(): List<String> =
        binding.editTextTags.text.toString()
            .split(",")
            .map { it.trim().removePrefix("#").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)

    private fun hasUnsavedChanges(): Boolean {
        val title = binding.editTextTitle.text.toString().trim()
        val body = binding.editTextBody.text.toString().trim()
        val tags = binding.editTextTags.text.toString().trim()
        return title != originalTitle || body != originalBody || tags != originalTags
    }

    // ---------------------------------------------------------------------
    // Back navigation: draft dialog (create/draft) or discard warning (edit)
    // ---------------------------------------------------------------------

    private fun handleBack() {
        if (!hasUnsavedChanges()) {
            finish()
            return
        }
        if (isEditMode) {
            AlertDialog.Builder(this)
                .setTitle(R.string.discard_changes_title)
                .setMessage(R.string.discard_changes_message)
                .setPositiveButton(R.string.keep_editing, null)
                .setNegativeButton(R.string.discard) { _, _ -> finish() }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.save_as_draft)
                .setPositiveButton(R.string.save_draft) { _, _ ->
                    autosaveDraft() // already autosaved; this captures the latest state
                    finish()
                }
                .setNegativeButton(R.string.discard) { _, _ ->
                    // Abandon: remove the session draft entirely.
                    val idToRemove = if (isDraftMode) draftId else autosaveDraftId
                    if (idToRemove != -1) draftStore.removeDraft(idToRemove)
                    finish()
                }
                .setNeutralButton(R.string.cancel, null)
                .show()
        }
    }

    // ---------------------------------------------------------------------
    // Publish / save changes
    // ---------------------------------------------------------------------

    private fun submitPost() {
        val title = binding.editTextTitle.text.toString().trim()
        val body = binding.editTextBody.text.toString().trim()

        // Clear previous validation states
        binding.tilTitle.error = null
        binding.tilTitle.isErrorEnabled = false
        binding.tilBody.error = null
        binding.tilBody.isErrorEnabled = false
        binding.cardSuccess.visibility = View.GONE
        binding.cardError.visibility = View.GONE

        var hasError = false
        if (title.isEmpty()) {
            binding.tilTitle.error = "Title is required"
            binding.tilTitle.isErrorEnabled = true
            hasError = true
        }
        if (body.isEmpty()) {
            binding.tilBody.error = "Body is required"
            binding.tilBody.isErrorEnabled = true
            hasError = true
        }
        if (hasError) return

        // Editing a locally-managed post is a purely local operation — no
        // network required. Creating always goes through the API.
        if (!isEditMode && !NetworkUtils.isNetworkAvailable(this)) {
            binding.textViewError.text = getString(R.string.offline_hint)
            binding.cardError.visibility = View.VISIBLE
            return
        }

        if (isEditMode && editPostId >= Post.LOCAL_ID_BASE) {
            saveLocalEdit(title, body)
            return
        }

        setSubmitting(true)
        val submittedAt = System.currentTimeMillis()
        val tags = parseTagsInput()

        lifecycleScope.launch {
            val result = if (isEditMode) {
                repository.updatePost(editPostId, title, body)
            } else {
                repository.createPost(title, body)
            }
            when (result) {
                is NetworkResult.Success -> {
                    setSubmitting(false)

                    val created = if (isEditMode) {
                        // Keep the original id and timestamp so the post
                        // stays in its existing newest-first position.
                        result.data.copy(
                            id = editPostId,
                            createdAt = editCreatedAt,
                            tags = tags
                        )
                    } else {
                        // The backend confirmed the publish; give the post its
                        // device-local identity and actual creation timestamp.
                        result.data.copy(
                            id = postStore.nextLocalId(),
                            createdAt = submittedAt,
                            tags = tags
                        )
                    }
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_CREATED_POST, JsonParser.postToJson(created))
                        putExtra(EXTRA_IS_EDIT, isEditMode)
                        val consumedDraft = when {
                            isEditMode -> -1
                            isDraftMode -> draftId
                            else -> autosaveDraftId
                        }
                        if (consumedDraft != -1) {
                            putExtra(EXTRA_DRAFT_TO_DELETE, consumedDraft)
                        }
                    }
                    setResult(RESULT_OK, resultIntent)

                    finish()
                }
                is NetworkResult.Error -> {
                    setSubmitting(false)
                    binding.textViewError.text = result.message
                    binding.cardError.visibility = View.VISIBLE
                }
            }
        }
    }

    /** Persists an edit to a locally-created post and returns to the feed. */
    private fun saveLocalEdit(title: String, body: String) {
        setSubmitting(true)
        lifecycleScope.launch {
            val updated = Post(
                userId = 1,
                id = editPostId,
                title = title,
                body = body,
                createdAt = editCreatedAt,
                tags = parseTagsInput()
            )
            val resultIntent = Intent().apply {
                putExtra(EXTRA_CREATED_POST, JsonParser.postToJson(updated))
                putExtra(EXTRA_IS_EDIT, true)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun setSubmitting(submitting: Boolean) {
        binding.progressBarCreate.visibility =
            if (submitting) View.VISIBLE else View.GONE
        binding.buttonSubmit.isEnabled = !submitting
        binding.buttonSubmit.text = getString(
            when {
                submitting && isEditMode -> R.string.saving
                submitting -> R.string.publishing
                isEditMode -> R.string.save_changes
                else -> R.string.publish_post
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        autosaveHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        const val EXTRA_CREATED_POST = "created_post"
        const val EXTRA_IS_EDIT = "is_edit"
        const val EXTRA_EDIT_POST_ID = "edit_post_id"
        const val EXTRA_EDIT_TITLE = "edit_post_title"
        const val EXTRA_EDIT_BODY = "edit_post_body"
        const val EXTRA_EDIT_TAGS = "edit_post_tags"
        const val EXTRA_EDIT_CREATED_AT = "edit_post_created_at"
        const val EXTRA_DRAFT_ID = "draft_id"
        const val EXTRA_DRAFT_TO_DELETE = "draft_to_delete"
        private const val AUTOSAVE_DELAY_MS = 800L
    }
}
