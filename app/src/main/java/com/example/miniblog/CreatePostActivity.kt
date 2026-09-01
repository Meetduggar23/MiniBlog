package com.example.miniblog

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.miniblog.data.PostRepository
import com.example.miniblog.databinding.ActivityCreatePostBinding
import com.example.miniblog.network.NetworkResult
import kotlinx.coroutines.launch

class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private val repository = PostRepository()

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

        if (title.isEmpty() || body.isEmpty()) {
            Toast.makeText(
                this, "Please enter both title and body", Toast.LENGTH_SHORT
            ).show()
            return
        }

        binding.progressBarCreate.visibility = View.VISIBLE
        binding.cardResult.visibility = View.GONE
        binding.buttonSubmit.isEnabled = false

        lifecycleScope.launch {
            when (val result = repository.createPost(title, body)) {
                is NetworkResult.Success -> {
                    binding.progressBarCreate.visibility = View.GONE
                    binding.buttonSubmit.isEnabled = true
                    binding.textViewResult.text =
                        "Post created!\n\nServer response:\n${result.data}"
                    binding.cardResult.visibility = View.VISIBLE
                    binding.editTextTitle.text?.clear()
                    binding.editTextBody.text?.clear()
                }
                is NetworkResult.Error -> {
                    binding.progressBarCreate.visibility = View.GONE
                    binding.buttonSubmit.isEnabled = true
                    binding.textViewResult.text = result.message
                    binding.cardResult.visibility = View.VISIBLE
                }
            }
        }
    }
}
