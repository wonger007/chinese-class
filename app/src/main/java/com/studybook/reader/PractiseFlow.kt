package com.studybook.reader

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Choosing characters on an open file to practise: a tap on a PDF page (its text, or for a scanned page the characters
 * recognised from its picture) or in a Word document opens [PracticePicker], then writing practice with a worksheet.
 * Background work runs in [activity]'s lifecycle, so it stops when the chapter screen goes.
 */
class PractiseFlow(
    private val activity: AppCompatActivity,
    private val pdf: PdfViewer,
    private val docxText: InkTextView,
    /** The worksheet's title: the open file's name, or the chapter's. */
    private val title: () -> String,
    /** Called when the language to practise is switched here (the title bar shows it). */
    private val onLanguageChanged: () -> Unit = {},
) {
    /** Characters and their positions on each page of the open PDF, read in the background. */
    private var pdfGlyphs: Deferred<List<List<PdfGlyph>>>? = null
    /** Characters recognised from the images of scanned pages (pages with no text), by page. */
    /** What each recogniser read on a scanned page (PdfOcr.readAll), by page. */
    private val recognisedPages = HashMap<Int, Map<String, List<PdfGlyph>>>()

    /** Forgets the last file's characters; called whenever another file is opened. */
    fun reset() {
        pdfGlyphs?.cancel()
        pdfGlyphs = null
        recognisedPages.clear()
    }

    /** Starts reading the characters of a newly opened PDF, in the background. */
    fun openPdf(uri: Uri) {
        pdfGlyphs = activity.lifecycleScope.async(Dispatchers.IO) {
            runCatching { PdfText.load(activity, uri) }.getOrDefault(emptyList())
        }
    }

    /** Shows the characters to practise on a PDF page; the one under a long-press (x, y in points) comes pre-selected. */
    fun pickFromPdf(page: Int, x: Float?, y: Float?) {
        val glyphsJob = pdfGlyphs ?: return
        activity.lifecycleScope.launch {
            var glyphs = glyphsJob.await().getOrNull(page).orEmpty()
            // A scanned page has no text layer: recognise the characters from the page image instead.
            val recognised = PdfText.practiceCharacters(glyphs).isEmpty()
            if (recognised) {
                val reads = recognisedPages[page] ?: recognisePage(page).also { recognisedPages[page] = it }
                // The page is read by the recogniser of the language being practised. When the page looks like
                // another language, the user decides: use it for this language anyway, or switch languages.
                var want = LanguageChars.script(Downloads.language(activity))
                val pageScript = LanguageChars.pageScript(reads.mapValues { PdfText.practiceCharacters(it.value) })
                if (pageScript != null && pageScript != want) {
                    want = LanguageChars.script(askPageLanguage(pageScript) ?: return@launch)
                }
                glyphs = reads[want].orEmpty()
            }
            val own = ownChars()
            val characters = PdfText.practiceCharacters(glyphs).filter(own::has)
            if (characters.isEmpty()) return@launch noneInLanguage(R.string.practise_none_lang)
            val pressed = if (x != null && y != null) PdfText.glyphAt(glyphs, x, y, slop = 6f) else null
            if (x != null && pressed == null) {
                Toast.makeText(activity, R.string.practise_miss, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val preselected = pressed?.text?.let { practiceCharsIn(it) }.orEmpty().toSet()
            val scope = activity.getString(R.string.practise_from_page, page + 1, pdf.pageCount)
            PracticePicker.show(activity, characters, preselected, scope, recognised) { practise(it) }
        }
    }

    /**
     * A scanned page that looks like another language ([pageScript]) than the one being practised: asks whether to
     * use it for the practised language anyway, or switch to the page's (when it's being learned). Returns the
     * language to practise, or null to cancel. A guess can be wrong (a page almost all kanji looks Chinese).
     */
    private suspend fun askPageLanguage(pageScript: String): String? = suspendCancellableCoroutine { cont ->
        val current = Downloads.language(activity)
        val currentName = LanguageChars.name(activity, current)
        val pageLangs = Downloads.languages(activity).filter { LanguageChars.script(it) == pageScript }
        val pageName = when {
            pageScript != "zh" -> LanguageChars.name(activity, pageScript)
            pageLangs.size == 1 -> LanguageChars.name(activity, pageLangs[0])
            else -> activity.getString(R.string.page_lang_or,
                LanguageChars.name(activity, "yue"), LanguageChars.name(activity, "cmn"))
        }
        var answered = false
        fun answer(lang: String?) { if (!answered) { answered = true; cont.resume(lang) } }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.page_lang_title, pageName))
            .setMessage(activity.getString(R.string.page_lang_message, currentName))
            .setPositiveButton(activity.getString(R.string.page_lang_use, currentName)) { _, _ -> answer(current) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> answer(null) }
            .setOnCancelListener { answer(null) }
        pageLangs.firstOrNull()?.let { lang ->
            dialog.setNeutralButton(activity.getString(R.string.page_lang_switch, LanguageChars.name(activity, lang))) { _, _ ->
                Downloads.setLanguage(activity, lang)
                onLanguageChanged()
                answer(lang)
            }
        }
        val shown = dialog.show()
        cont.invokeOnCancellation { shown.dismiss() }
    }

    /** Runs on-device text recognition on a rendered PDF page, with every recogniser (see [PdfOcr.readAll]). */
    private suspend fun recognisePage(page: Int): Map<String, List<PdfGlyph>> {
        val widthPoints = pdf.pageWidthPoints(page) ?: return emptyMap()
        Toast.makeText(activity, R.string.recognising, Toast.LENGTH_SHORT).show()
        val pixels = 2000
        val bitmap = suspendCancellableCoroutine<Bitmap?> { cont -> pdf.renderPage(page, pixels) { cont.resume(it) } }
            ?: return emptyMap()
        return try {
            PdfOcr.readAll(bitmap, widthPoints.toFloat() / pixels)
        } finally {
            bitmap.recycle()
        }
    }

    /** Practise mode tap in a Word document: offers the characters of the tapped paragraph, the tapped one chosen. */
    fun pickFromDocx(offset: Int) {
        val text = docxText.text
        if (offset !in text.indices) return
        val cp = Character.codePointAt(text, offset)
        val tapped = String(Character.toChars(cp))
        if (!isPracticeChar(tapped)) {
            Toast.makeText(activity, R.string.practise_miss, Toast.LENGTH_SHORT).show()
            return
        }
        if (!ownChars().has(tapped)) return noneInLanguage(R.string.practise_not_lang)
        val start = text.lastIndexOf('\n', offset - 1).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
        PracticePicker.show(activity, practiceCharsIn(text.subSequence(start, end)), setOf(tapped),
            activity.getString(R.string.practise_from_paragraph)) { practise(it) }
    }

    /** Offers all the characters of the Word document. */
    fun pickAllFromDocx() = PracticePicker.show(activity, practiceCharsIn(docxText.text), emptySet(),
        activity.getString(R.string.practise_from_document)) { practise(it) }

    /** The characters in [text] that belong to the language being practised (others are never offered). */
    private fun practiceCharsIn(text: CharSequence): List<String> {
        val own = ownChars()
        return text.codePoints().toArray().map { String(Character.toChars(it)) }.filter(own::has).distinct()
    }

    private fun ownChars() = LanguageChars.of(activity, Downloads.language(activity))

    /** Says, naming the language being practised, that there's nothing in it to practise. */
    private fun noneInLanguage(message: Int) {
        val name = LanguageChars.name(activity, Downloads.language(activity))
        Toast.makeText(activity, activity.getString(message, name), Toast.LENGTH_SHORT).show()
    }

    /** Opens writing practice with a worksheet for these characters. */
    private fun practise(characters: List<String>) {
        if (characters.isEmpty()) return
        activity.startActivity(
            Intent(activity, TrainingActivity::class.java)
                .putExtra(TrainingActivity.EXTRA_TEXT, characters.joinToString(""))
                .putExtra(TrainingActivity.EXTRA_TITLE, title())
                .putExtra(TrainingActivity.EXTRA_AUTO_START, true)
        )
    }

    /** Adds "Practise writing" to the text selection menu of Word documents. */
    inner class Selection : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(Menu.NONE, R.id.practise_selection, 0, R.string.practise_title)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != R.id.practise_selection) return false
            val start = minOf(docxText.selectionStart, docxText.selectionEnd).coerceAtLeast(0)
            val end = maxOf(docxText.selectionStart, docxText.selectionEnd).coerceAtLeast(0)
            val characters = practiceCharsIn(docxText.text.subSequence(start, end))
            if (characters.isEmpty()) {
                noneInLanguage(R.string.practise_no_chinese)
            } else {
                practise(characters)
            }
            mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }
}
