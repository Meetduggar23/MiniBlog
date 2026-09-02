package com.example.miniblog.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Formats post timestamps as subtle, readable labels. */
object PostDateFormatter {

    private val dayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    /** "Today · 10:42 AM" for today, otherwise "Sep 2, 2026". */
    fun format(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) {
            "Today · ${timeFormat.format(Date(timestamp))}"
        } else {
            dayFormat.format(Date(timestamp))
        }
    }
}
