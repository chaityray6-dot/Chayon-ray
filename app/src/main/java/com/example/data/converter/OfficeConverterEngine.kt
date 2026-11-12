package com.example.data.converter

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.data.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class OfficeFormat(val ext: String, val mimeType: String, val label: String) {
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word (.docx)"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel (.xlsx)"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint (.pptx)"),
    TXT("txt", "text/plain", "Plain Text (.txt)")
}

class OfficeConverterEngine(
    private val context: Context,
    private val pdfEngine: PdfEngine
) {

    suspend fun convertOfficeUriToPdf(uri: Uri, title: String, format: OfficeFormat, outputPdf: File): File =
        withContext(Dispatchers.IO) {
            val extractedText = when (format) {
                OfficeFormat.TXT -> context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                }.orEmpty()
                OfficeFormat.DOCX -> extractDocxText(uri)
                OfficeFormat.XLSX -> extractXlsxText(uri)
                OfficeFormat.PPTX -> extractPptxText(uri)
            }
            val body = extractedText.ifBlank { "No readable text found in ${format.label}." }
            pdfEngine.textToPdf(title = title, bodyText = body, outputFile = outputPdf)
        }

    suspend fun convertPdfToOffice(pdfFile: File, targetFormat: OfficeFormat, outputFile: File): File =
        withContext(Dispatchers.IO) {
            outputFile.parentFile?.mkdirs()
            val pages = pdfEngine.extractAllPagesText(pdfFile)
            val nonEmptyPages = if (pages.isEmpty()) listOf("(No extractable text found in PDF)") else pages

            when (targetFormat) {
                OfficeFormat.TXT -> {
                    val content = nonEmptyPages.mapIndexed { idx, txt ->
                        "--- Page ${idx + 1} ---\n$txt"
                    }.joinToString("\n\n")
                    outputFile.writeText(content)
                }
                OfficeFormat.DOCX -> writeValidDocx(nonEmptyPages, outputFile)
                OfficeFormat.XLSX -> writeValidXlsx(nonEmptyPages, outputFile)
                OfficeFormat.PPTX -> writeValidPptx(nonEmptyPages, outputFile)
            }
            outputFile
        }

    private fun extractDocxText(uri: Uri): String {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xml = zis.readBytes().toString(Charsets.UTF_8)
                        sb.append(parseXmlTextNodes(xml, paragraphTag = "p", textTag = "t"))
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        }
        return sb.toString().trim()
    }

    private fun extractXlsxText(uri: Uri): String {
        val sharedStrings = mutableListOf<String>()
        val sheetXmls = mutableListOf<Pair<String, String>>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "xl/sharedStrings.xml" -> {
                            val xml = zis.readBytes().toString(Charsets.UTF_8)
                            sharedStrings.addAll(parseSharedStrings(xml))
                        }
                        entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") -> {
                            sheetXmls.add(entry.name to zis.readBytes().toString(Charsets.UTF_8))
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        }
        val sb = StringBuilder()
        sheetXmls.sortedBy { it.first }.forEachIndexed { idx, (_, xml) ->
            sb.append("=== Sheet ${idx + 1} ===\n")
            sb.append(parseXlsxSheet(xml, sharedStrings))
            sb.append("\n")
        }
        return sb.toString().trim()
    }

    private fun extractPptxText(uri: Uri): String {
        val slides = mutableListOf<Pair<String, String>>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                        slides.add(entry.name to zis.readBytes().toString(Charsets.UTF_8))
                    }
                    entry = zis.nextEntry
                }
            }
        }
        return slides.sortedBy { it.first }.mapIndexed { idx, (_, xml) ->
            "Slide ${idx + 1}:\n${parseXmlTextNodes(xml, paragraphTag = "p", textTag = "t")}"
        }.joinToString("\n\n").trim()
    }

    private fun parseXmlTextNodes(xml: String, paragraphTag: String, textTag: String): String {
        val sb = StringBuilder()
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name?.substringAfter(':')
                    if (name == textTag) {
                        sb.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name?.substringAfter(':')
                    if (name == paragraphTag) sb.append('\n')
                }
            }
            event = parser.next()
        }
        return sb.toString()
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        val cur = StringBuilder()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name?.substringAfter(':')
                    if (name == "si") cur.clear()
                    else if (name == "t") cur.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name?.substringAfter(':')
                    if (name == "si") list.add(cur.toString())
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseXlsxSheet(xml: String, sharedStrings: List<String>): String {
        val sb = StringBuilder()
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        var cellType: String? = null
        val rowValues = mutableListOf<String>()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name?.substringAfter(':')
                    if (name == "row") rowValues.clear()
                    else if (name == "c") cellType = parser.getAttributeValue(null, "t")
                    else if (name == "v") {
                        val raw = parser.nextText()
                        val value = if (cellType == "s") {
                            sharedStrings.getOrNull(raw.toIntOrNull() ?: -1) ?: raw
                        } else raw
                        rowValues.add(value)
                    } else if (name == "t") {
                        rowValues.add(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name?.substringAfter(':')
                    if (name == "row" && rowValues.isNotEmpty()) {
                        sb.append(rowValues.joinToString(" | ")).append('\n')
                    }
                }
            }
            event = parser.next()
        }
        return sb.toString()
    }

    private fun writeValidDocx(pages: List<String>, outputFile: File) {
        val paragraphsXml = pages.flatMapIndexed { idx, page ->
            val header = "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Page ${idx + 1}</w:t></w:r></w:p>"
            val lines = page.lines().map { line ->
                "<w:p><w:r><w:t xml:space=\"preserve\">${escapeXml(line)}</w:t></w:r></w:p>"
            }
            listOf(header) + lines
        }.joinToString("")

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>$paragraphsXml</w:body>
</w:document>"""

        ZipOutputStream(outputFile.outputStream()).use { zos ->
            zos.writeEntry("[Content_Types].xml", contentTypes)
            zos.writeEntry("_rels/.rels", rels)
            zos.writeEntry("word/document.xml", docXml)
        }
    }

    private fun writeValidXlsx(pages: List<String>, outputFile: File) {
        val rows = mutableListOf<String>()
        var rowNum = 1
        pages.forEachIndexed { pageIdx, text ->
            rows.add(buildXlsxRow(rowNum++, listOf("Page ${pageIdx + 1}", "")))
            text.lines().filter { it.isNotBlank() }.forEach { line ->
                val cells = line.split("|", "\t", "  ").map { it.trim() }.filter { it.isNotEmpty() }
                rows.add(buildXlsxRow(rowNum++, cells.ifEmpty { listOf(line) }))
            }
        }

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="PDF Export" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""
        val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
        val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>${rows.joinToString("")}</sheetData>
</worksheet>"""

        ZipOutputStream(outputFile.outputStream()).use { zos ->
            zos.writeEntry("[Content_Types].xml", contentTypes)
            zos.writeEntry("_rels/.rels", rels)
            zos.writeEntry("xl/workbook.xml", workbook)
            zos.writeEntry("xl/_rels/workbook.xml.rels", wbRels)
            zos.writeEntry("xl/worksheets/sheet1.xml", sheetXml)
        }
    }

    private fun buildXlsxRow(rowIndex: Int, cells: List<String>): String {
        val cellsXml = cells.mapIndexed { colIdx, value ->
            val colName = ('A' + (colIdx % 26)).toString()
            "<c r=\"$colName$rowIndex\" t=\"inlineStr\"><is><t>${escapeXml(value)}</t></is></c>"
        }.joinToString("")
        return "<row r=\"$rowIndex\">$cellsXml</row>"
    }

    private fun writeValidPptx(pages: List<String>, outputFile: File) {
        val slideCount = pages.size.coerceAtLeast(1)
        val slideOverrides = (1..slideCount).joinToString("") {
            "<Override PartName=\"/ppt/slides/slide$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>"
        }
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  $slideOverrides
</Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
        val slideIds = (1..slideCount).joinToString("") { "<p:sldId id=\"${255 + it}\" r:id=\"rId$it\"/>" }
        val presentation = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <p:sldIdLst>$slideIds</p:sldIdLst>
</p:presentation>"""
        val presRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  ${(1..slideCount).joinToString("") { "<Relationship Id=\"rId$it\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide$it.xml\"/>" }}
</Relationships>"""

        ZipOutputStream(outputFile.outputStream()).use { zos ->
            zos.writeEntry("[Content_Types].xml", contentTypes)
            zos.writeEntry("_rels/.rels", rels)
            zos.writeEntry("ppt/presentation.xml", presentation)
            zos.writeEntry("ppt/_rels/presentation.xml.rels", presRels)
            pages.forEachIndexed { idx, text ->
                val paragraphs = text.lines().take(18).joinToString("") { line ->
                    "<a:p><a:r><a:t>${escapeXml(line)}</a:t></a:r></a:p>"
                }
                val slideXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:spTree><p:sp><p:txBody><a:bodyPr/>$paragraphs</p:txBody></p:sp></p:spTree></p:cSld>
</p:sld>"""
                zos.writeEntry("ppt/slides/slide${idx + 1}.xml", slideXml)
            }
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
