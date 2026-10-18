package com.educationvault.app

import com.educationvault.app.docs.Category
import com.educationvault.app.docs.DocParser
import com.educationvault.app.docs.FileSupport
import com.educationvault.app.docs.Formatting
import com.educationvault.app.docs.FormValidator
import com.educationvault.app.docs.FormValidator.Problem
import com.educationvault.app.docs.categoryLabel
import java.io.ByteArrayInputStream
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocsLogicTest {

    // ----- parsing -----
    private val docJson = """{"id":"d1","title":"Matric","category":"matric","institution":"BISE","passingYear":2015,
        "issueDate":"2016-01-02","notes":"n","mimeType":"application/pdf","size":1234,"originalName":"a.pdf",
        "createdAt":"2026-10-18T10:00:00.000Z","updatedAt":"2026-10-18T10:00:00.000Z"}"""

    @Test fun parsesDocument() {
        val d = DocParser.parseDoc(JSONObject(docJson))
        assertEquals("d1", d.id)
        assertEquals(2015, d.passingYear)
        assertEquals("2016-01-02", d.issueDate)
        assertEquals(1234L, d.size)
    }

    @Test fun nullYearAndDateStayNull() {
        val d = DocParser.parseDoc(JSONObject("""{"id":"d2","title":"x","passingYear":null,"issueDate":null}"""))
        assertNull(d.passingYear)
        assertNull(d.issueDate)
    }

    @Test fun documentWithoutIdIsRejected() {
        assertThrows(JSONException::class.java) { DocParser.parseDoc(JSONObject("""{"title":"x"}""")) }
    }

    @Test fun parsesPageWithCursor() {
        val page = DocParser.parsePage(JSONObject("""{"items":[$docJson],"nextCursor":"30","total":31}"""))
        assertEquals(1, page.items.size)
        assertEquals("30", page.nextCursor)
        assertEquals(31, page.total)
        val last = DocParser.parsePage(JSONObject("""{"items":[],"nextCursor":null,"total":0}"""))
        assertNull(last.nextCursor)
    }

    @Test fun parsesCategoriesAndLabels() {
        val cats = DocParser.parseCategories(
            JSONObject("""{"categories":[{"id":"cv","ur":"سی وی","en":"CV","custom":false},{"id":"custom_ab12cd34","ur":"X","en":"X","custom":true}]}""")
        )
        assertEquals(2, cats.size)
        assertTrue(cats[1].custom)
        assertEquals("سی وی", categoryLabel("cv", cats, true))
        assertEquals("CV", categoryLabel("cv", cats, false))
        assertEquals("unknown", categoryLabel("unknown", cats, true))
    }

    // ----- formatting -----
    @Test fun formatsSizes() {
        assertEquals("512 B", Formatting.formatSize(512))
        assertEquals("2 KB", Formatting.formatSize(2048))
        assertEquals("1.5 MB", Formatting.formatSize(1536L * 1024))
        assertEquals("5.0 MB", Formatting.formatSize(5L * 1024 * 1024))
    }

    @Test fun formatsLabelsAndDates() {
        assertEquals("2026-10-18", Formatting.dateOnly("2026-10-18T10:00:00.000Z"))
        assertEquals("PDF", Formatting.mimeLabel("application/pdf"))
        assertEquals("JPG", Formatting.mimeLabel("image/jpeg"))
        assertEquals("png", Formatting.extensionFor("image/png"))
        assertEquals("Matric", Formatting.titleFromFileName("Matric.pdf"))
        assertEquals("noext", Formatting.titleFromFileName("noext"))
    }

    @Test fun suggestedFileNameHasNoPathCharacters() {
        val n = Formatting.suggestedFileName("../../a/b:c*", "application/pdf")
        assertFalse(n.contains("/"))
        assertFalse(n.contains(":"))
        assertFalse(n.contains("*"))
        assertTrue(n.endsWith(".pdf"))
        assertEquals("document.jpg", Formatting.suggestedFileName("   ", "image/jpeg"))
    }

    // ----- files -----
    @Test fun normalizesMimeTypes() {
        assertEquals("application/pdf", FileSupport.normalizeMime("application/pdf", "x"))
        assertEquals("image/jpeg", FileSupport.normalizeMime("image/jpg", "x"))
        assertEquals("application/pdf", FileSupport.normalizeMime("application/octet-stream", "Scan.PDF"))
        assertEquals("image/png", FileSupport.normalizeMime(null, "a.png"))
        assertNull(FileSupport.normalizeMime("application/zip", "a.zip"))
        assertNull(FileSupport.normalizeMime(null, "noextension"))
    }

    @Test fun readLimitedNeverTruncates() {
        val data = ByteArray(1000) { 7 }
        assertArrayEquals(data, FileSupport.readLimited(ByteArrayInputStream(data), 1000))
        assertNull(FileSupport.readLimited(ByteArrayInputStream(data), 999))
        assertEquals(0, FileSupport.readLimited(ByteArrayInputStream(ByteArray(0)), 10)?.size)
    }

    // ----- form validation -----
    private fun v(
        hasFile: Boolean = true, title: String = "T", category: String? = "matric", year: String = "",
        date: String = "", notes: String = ""
    ) = FormValidator.validate(hasFile, title, category, year, date, notes, currentYear = 2026)

    @Test fun validFormPasses() {
        assertNull(v())
        assertNull(v(year = "2019", date = "2020-02-29", notes = "ok"))
    }

    @Test fun reportsFirstProblem() {
        assertEquals(Problem.FILE_MISSING, v(hasFile = false))
        assertEquals(Problem.TITLE_EMPTY, v(title = "   "))
        assertEquals(Problem.TITLE_TOO_LONG, v(title = "x".repeat(121)))
        assertEquals(Problem.CATEGORY_MISSING, v(category = null))
        assertEquals(Problem.YEAR_INVALID, v(year = "19"))
        assertEquals(Problem.YEAR_INVALID, v(year = "1900"))
        assertEquals(Problem.YEAR_INVALID, v(year = "2028"))
        assertEquals(Problem.YEAR_INVALID, v(year = "abcd"))
        assertEquals(Problem.DATE_INVALID, v(date = "2021-02-29"))
        assertEquals(Problem.DATE_INVALID, v(date = "15-01-2020"))
        assertEquals(Problem.NOTES_TOO_LONG, v(notes = "n".repeat(1001)))
    }

    @Test fun realDateChecks() {
        assertTrue(FormValidator.isRealDate("2024-02-29"))
        assertFalse(FormValidator.isRealDate("2023-02-29"))
        assertFalse(FormValidator.isRealDate("2023-13-01"))
        assertFalse(FormValidator.isRealDate("2023-04-31"))
        assertTrue(FormValidator.isRealDate("2023-12-31"))
    }
}
