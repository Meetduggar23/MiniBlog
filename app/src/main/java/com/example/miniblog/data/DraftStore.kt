package com.example.miniblog.data

import android.content.Context
import com.example.miniblog.model.Draft
import org.json.JSONArray
import org.json.JSONObject

/** Local persistence for post drafts (autosaved and manually saved). */
class DraftStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** All drafts, most recently updated first. */
    fun getDrafts(): List<Draft> {
        val json = prefs.getString(KEY_DRAFTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val drafts = mutableListOf<Draft>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tags = mutableListOf<String>()
                val tagArray = obj.optJSONArray("tags")
                if (tagArray != null) {
                    for (j in 0 until tagArray.length()) {
                        val tag = tagArray.optString(j).trim()
                        if (tag.isNotEmpty()) tags.add(tag)
                    }
                }
                drafts.add(
                    Draft(
                        id = obj.optInt("id"),
                        title = obj.optString("title"),
                        body = obj.optString("body"),
                        tags = tags,
                        updatedAt = if (obj.has("updatedAt")) obj.optLong("updatedAt")
                        else System.currentTimeMillis()
                    )
                )
            }
            drafts.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getDraft(id: Int): Draft? = getDrafts().find { it.id == id }

    /** Inserts or updates a draft (upsert by id). */
    fun saveDraft(draft: Draft) {
        val drafts = getDrafts().filter { it.id != draft.id } + draft
        save(drafts)
    }

    fun removeDraft(id: Int) {
        save(getDrafts().filterNot { it.id == id })
    }

    /** Next free id for a draft. */
    fun nextDraftId(): Int =
        maxOf(1, (getDrafts().maxOfOrNull { it.id } ?: 0) + 1)

    private fun save(drafts: List<Draft>) {
        val array = JSONArray()
        for (draft in drafts) {
            array.put(
                JSONObject().apply {
                    put("id", draft.id)
                    put("title", draft.title)
                    put("body", draft.body)
                    put("tags", JSONArray(draft.tags))
                    put("updatedAt", draft.updatedAt)
                }
            )
        }
        prefs.edit().putString(KEY_DRAFTS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "miniblog_drafts"
        private const val KEY_DRAFTS = "drafts"
    }
}
