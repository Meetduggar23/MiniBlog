package com.example.miniblog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.miniblog.data.AppPreferences
import com.example.miniblog.data.DraftStore
import com.example.miniblog.data.LocalPostStore
import com.example.miniblog.data.PostStatsStore
import com.example.miniblog.databinding.ActivityStatsBinding
import java.util.Calendar

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private val postStore by lazy { LocalPostStore(this) }
    private val draftStore by lazy { DraftStore(this) }
    private val statsStore by lazy { PostStatsStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.my_blog)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }

    private fun loadStats() {
        val allPosts = postStore.getPosts()
        val drafts = draftStore.getDrafts()

        val totalPosts = allPosts.size
        val published = allPosts.size
        val draftsCount = drafts.size
        val savedPosts = allPosts.count { it.isBookmarked }
        val likedPosts = statsStore.likedPostCount()

        val totalLikes = statsStore.totalLikes()
        val totalViews = statsStore.totalViews()

        val calendar = Calendar.getInstance()
        val thisMonth = calendar.get(Calendar.MONTH)
        val thisYear = calendar.get(Calendar.YEAR)
        val postsThisMonth = allPosts.count { post ->
            val cal = Calendar.getInstance().apply { timeInMillis = post.createdAt }
            cal.get(Calendar.MONTH) == thisMonth && cal.get(Calendar.YEAR) == thisYear
        }

        binding.textViewTotalPosts.text = totalPosts.toString()
        binding.textViewPublished.text = published.toString()
        binding.textViewDraftsCount.text = draftsCount.toString()
        binding.textViewSavedCount.text = savedPosts.toString()
        binding.textViewLikedCount.text = likedPosts.toString()

        binding.textViewTotalLikes.text = totalLikes.toString()
        binding.textViewTotalViews.text = totalViews.toString()
        binding.textViewPostsThisMonth.text = postsThisMonth.toString()
    }
}
