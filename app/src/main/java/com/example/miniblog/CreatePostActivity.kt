package com.example.miniblog

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.miniblog.data.JsonParser
import com.example.miniblog.data.LocalPostStore
import com.example.miniblog.data.PostRepository
import com.example.miniblog.databinding.ActivityCreatePostBinding
import com.example.miniblog.network.NetworkResult
import com.example.miniblog.network.NetworkUtils
import kotlinx.coroutines.launch

class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private val repository = PostRepository()
    private val postStore by lazy { LocalPostStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Create Post"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.buttonSubmit.setOnClickListener { submitPost() }
    }

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

        if (!NetworkUtils.isNetworkAvailable(this)) {
            binding.textViewError.text = "No internet connection. Please try again."
            binding.cardError.visibility = View.VISIBLE
            return
        }

        binding.progressBarCreate.visibility = View.VISIBLE
        binding.buttonSubmit.isEnabled = false
        val submittedAt = System.currentTimeMillis()

        lifecycleScope.launch {
            when (val result = repository.createPost(title, body)) {
                is NetworkResult.Success -> {
                    binding.progressBarCreate.visibility = View.GONE
                    binding.buttonSubmit.isEnabled = true

                    // The backend confirmed the publish; give the post its
                    // device-local identity and actual creation timestamp.
                    val created = result.data.copy(
                        id = postStore.nextLocalId(),
                        createdAt = submittedAt
                    )
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_CREATED_POST, JsonParser.postToJson(created))
                    }
                    setResult(RESULT_OK, resultIntent)

                    Toast.makeText(
                        this@CreatePostActivity,
                        "Post published!",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                }
                is NetworkResult.Error -> {
                    binding.progressBarCreate.visibility = View.GONE
                    binding.buttonSubmit.isEnabled = true
                    binding.textViewError.text = result.message
                    binding.cardError.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object {
        const val EXTRA_CREATED_POST = "created_post"
    }
}
