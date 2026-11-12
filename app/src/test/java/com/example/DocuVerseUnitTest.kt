package com.example

import com.example.data.ai.PlaceholderAiRepository
import com.example.data.ai.SummaryMode
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocuVerseUnitTest {

    @Test
    fun placeholderAi_citesMatchingPagesAndReportsMissingInfo() = runTest {
        val repo = PlaceholderAiRepository()
        assertFalse(repo.isLiveGeminiEnabled)

        val pages = listOf(
            "DocuVerse supports offline PDF reading, bookmarks, and text reflow.",
            "AES-256 Keystore protects the Private Vault."
        )

        val summary = repo.generateSummary("Guide", pages, SummaryMode.SHORT, AppLanguage.ENGLISH)
        assertTrue(summary.isPlaceholder)
        assertTrue(summary.answer.contains("Sample AI"))
        assertTrue(summary.citedPages.contains(1))

        val foundAnswer = repo.askQuestion("Guide", pages, "What protects Private Vault?", AppLanguage.ENGLISH)
        assertTrue(foundAnswer.citedPages.contains(2))

        val missingAnswer = repo.askQuestion("Guide", pages, "quantum rocket propulsion", AppLanguage.ENGLISH)
        assertTrue(missingAnswer.citedPages.isEmpty())
        assertTrue(missingAnswer.answer.contains("not found", ignoreCase = true))
    }
}
