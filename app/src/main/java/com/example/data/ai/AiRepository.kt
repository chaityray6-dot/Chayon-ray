package com.example.data.ai

import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.delay

enum class SummaryMode(val label: String, val bnLabel: String) {
    SHORT("Short Summary", "সংক্ষিপ্ত সারাংশ"),
    DETAILED("Detailed Summary", "বিস্তারিত সারাংশ"),
    CHAPTER_WISE("Chapter-wise", "অধ্যায়ভিত্তিক"),
    IMPORTANT_POINTS("Important Points", "গুরুত্বপূর্ণ পয়েন্ট"),
    KEY_DEFINITIONS("Key Definitions", "মূল সংজ্ঞা"),
    DATES_AND_FACTS("Dates & Facts", "তারিখ ও তথ্য")
}

data class AiChatReply(
    val answer: String,
    val citedPages: List<Int>,
    val isPlaceholder: Boolean = true
)

interface AiRepository {
    val isLiveGeminiEnabled: Boolean
    suspend fun generateSummary(
        documentTitle: String,
        pagesText: List<String>,
        mode: SummaryMode,
        language: AppLanguage
    ): AiChatReply

    suspend fun askQuestion(
        documentTitle: String,
        pagesText: List<String>,
        question: String,
        language: AppLanguage
    ): AiChatReply
}

/**
 * Sample placeholder implementation used when Firebase setup is declined.
 * Grounded in the PDF's real extracted page text with clear placeholder labeling.
 */
class PlaceholderAiRepository : AiRepository {
    override val isLiveGeminiEnabled: Boolean = false

    override suspend fun generateSummary(
        documentTitle: String,
        pagesText: List<String>,
        mode: SummaryMode,
        language: AppLanguage
    ): AiChatReply {
        delay(350)
        val nonEmpty = pagesText.mapIndexedNotNull { idx, t ->
            if (t.isBlank()) null else idx + 1 to t.trim()
        }
        if (nonEmpty.isEmpty()) {
            val msg = if (language == AppLanguage.BENGALI) {
                "[নমুনা এআই উত্তর — ফায়ারবেস প্রয়োজন]\n\n\"$documentTitle\" ডকুমেন্টে কোনো পাঠযোগ্য টেক্সট পাওয়া যায়নি (সম্ভবত স্ক্যান করা ছবি)। প্রথমে OCR টুল দিয়ে টেক্সট এক্সট্র্যাক্ট করুন।"
            } else {
                "[Sample AI Response — Firebase Required]\n\nNo selectable text was found in \"$documentTitle\" (it may be a scanned PDF). Run the OCR tool first to extract text."
            }
            return AiChatReply(msg, emptyList(), isPlaceholder = true)
        }

        val cited = nonEmpty.take(4).map { it.first }
        val answer = buildString {
            if (language == AppLanguage.BENGALI) {
                appendLine("[নমুনা এআই সারাংশ (${mode.bnLabel}) — লাইভ জেমিনি ব্যবহারের জন্য ফায়ারবেস চালু করুন]")
                appendLine()
                appendLine("ডকুমেন্ট: $documentTitle (${pagesText.size} পৃষ্ঠা)")
            } else {
                appendLine("[Sample AI ${mode.label} — Enable Firebase for Live Gemini AI]")
                appendLine()
                appendLine("Document: $documentTitle (${pagesText.size} pages)")
            }
            appendLine()
            when (mode) {
                SummaryMode.SHORT -> {
                    val snippet = nonEmpty.first().second.take(280).replace('\n', ' ')
                    appendLine("• Overview (Page ${nonEmpty.first().first}): $snippet...")
                    appendLine("• Total readable sections: ${nonEmpty.size} pages analyzed locally.")
                }
                SummaryMode.DETAILED -> {
                    nonEmpty.take(3).forEach { (p, text) ->
                        appendLine("Page $p Highlights:")
                        appendLine(text.take(320).replace('\n', ' '))
                        appendLine()
                    }
                }
                SummaryMode.CHAPTER_WISE -> {
                    nonEmpty.forEach { (p, text) ->
                        val heading = text.lines().firstOrNull { it.isNotBlank() }?.take(64) ?: "Section"
                        appendLine("• Page $p — $heading")
                        appendLine("  ${text.take(150).replace('\n', ' ')}...")
                    }
                }
                SummaryMode.IMPORTANT_POINTS -> {
                    val bullets = nonEmpty.flatMap { (p, t) ->
                        t.split('.').map { it.trim() }.filter { it.length > 30 }.take(2).map { "Page $p: $it." }
                    }.take(6)
                    bullets.forEach { appendLine("• $it") }
                }
                SummaryMode.KEY_DEFINITIONS -> {
                    val defs = nonEmpty.flatMap { (p, t) ->
                        t.lines().filter { it.contains(":") || it.contains(" is ") }.take(2).map { "Page $p: ${it.trim()}" }
                    }.take(5)
                    if (defs.isEmpty()) {
                        appendLine("• Key terms extracted from Page ${nonEmpty.first().first}: ${nonEmpty.first().second.take(220)}")
                    } else {
                        defs.forEach { appendLine("• $it") }
                    }
                }
                SummaryMode.DATES_AND_FACTS -> {
                    val regex = Regex("""\b(\d{4}|\d{1,2}/\d{1,2}/\d{2,4}|\d+%)\b""")
                    val hits = nonEmpty.flatMap { (p, t) ->
                        t.lines().filter { regex.containsMatchIn(it) }.take(2).map { "Page $p: ${it.trim()}" }
                    }.take(6)
                    if (hits.isEmpty()) {
                        appendLine("• No explicit numeric dates or percentages detected in the extracted text.")
                    } else {
                        hits.forEach { appendLine("• $it") }
                    }
                }
            }
        }
        return AiChatReply(answer.trim(), cited, isPlaceholder = true)
    }

    override suspend fun askQuestion(
        documentTitle: String,
        pagesText: List<String>,
        question: String,
        language: AppLanguage
    ): AiChatReply {
        delay(300)
        val tokens = question.lowercase()
            .split(Regex("""[\s,?.!।]+"""))
            .filter { it.length >= 3 }
            .distinct()

        if (pagesText.all { it.isBlank() }) {
            val msg = if (language == AppLanguage.BENGALI) {
                "[নমুনা উত্তর — ফায়ারবেস প্রয়োজন]\nএই ডকুমেন্টে টেক্সট নেই। আগে OCR চালান।"
            } else {
                "[Sample Response — Firebase Required]\nThis PDF has no extractable text layer yet. Please run OCR first."
            }
            return AiChatReply(msg, emptyList(), isPlaceholder = true)
        }

        val scored = pagesText.mapIndexed { idx, text ->
            val lower = text.lowercase()
            val score = tokens.count { lower.contains(it) }
            Triple(idx + 1, score, text)
        }.filter { it.second > 0 }.sortedByDescending { it.second }

        if (scored.isEmpty()) {
            val msg = if (language == AppLanguage.BENGALI) {
                "[নমুনা উত্তর — ফায়ারবেস প্রয়োজন]\n\n\"$documentTitle\" ডকুমেন্টের প্রদত্ত টেক্সটে \"$question\" সম্পর্কিত কোনো তথ্য পাওয়া যায়নি।"
            } else {
                "[Sample Response — Firebase Required]\n\nThe requested information about \"$question\" was not found in \"$documentTitle\"."
            }
            return AiChatReply(msg, emptyList(), isPlaceholder = true)
        }

        val topPages = scored.take(2)
        val cited = topPages.map { it.first }
        val answer = buildString {
            if (language == AppLanguage.BENGALI) {
                appendLine("[নমুনা এআই উত্তর — লাইভ জেমিনি ব্যবহারের জন্য ফায়ারবেস চালু করুন]")
                appendLine()
                appendLine("ডকুমেন্ট \"$documentTitle\" থেকে প্রাসঙ্গিক অংশ:")
            } else {
                appendLine("[Sample AI Response — Enable Firebase for Live Gemini AI]")
                appendLine()
                appendLine("Relevant excerpts from \"$documentTitle\":")
            }
            appendLine()
            topPages.forEach { (pageNum, _, text) ->
                val excerpt = text.replace('\n', ' ').trim().take(260)
                appendLine("• Page $pageNum: \"$excerpt...\"")
            }
        }
        return AiChatReply(answer.trim(), cited, isPlaceholder = true)
    }
}
