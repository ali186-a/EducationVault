package com.educationvault.app.docs

import java.util.Calendar

/** Client-side checks for the upload / edit forms. The backend validates everything again. */
object FormValidator {
    enum class Problem {
        FILE_MISSING, TITLE_EMPTY, TITLE_TOO_LONG, CATEGORY_MISSING, YEAR_INVALID, DATE_INVALID, NOTES_TOO_LONG
    }

    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val YEAR_RE = Regex("^\\d{4}$")

    fun validate(
        hasFile: Boolean,
        title: String,
        category: String?,
        yearText: String,
        issueDate: String,
        notes: String,
        currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): Problem? {
        if (!hasFile) return Problem.FILE_MISSING
        val t = title.trim()
        if (t.isEmpty()) return Problem.TITLE_EMPTY
        if (t.length > 120) return Problem.TITLE_TOO_LONG
        if (category.isNullOrEmpty()) return Problem.CATEGORY_MISSING
        val y = yearText.trim()
        if (y.isNotEmpty()) {
            val n = y.toIntOrNull()
            if (!YEAR_RE.matches(y) || n == null || n < 1950 || n > currentYear + 1) return Problem.YEAR_INVALID
        }
        val d = issueDate.trim()
        if (d.isNotEmpty() && !isRealDate(d)) return Problem.DATE_INVALID
        if (notes.trim().length > 1000) return Problem.NOTES_TOO_LONG
        return null
    }

    fun isRealDate(s: String): Boolean {
        if (!DATE_RE.matches(s)) return false
        val y = s.substring(0, 4).toInt()
        val m = s.substring(5, 7).toInt()
        val d = s.substring(8, 10).toInt()
        if (y < 1900 || y > 2100 || m < 1 || m > 12) return false
        val leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
        val days = when (m) {
            2 -> if (leap) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return d in 1..days
    }
}
