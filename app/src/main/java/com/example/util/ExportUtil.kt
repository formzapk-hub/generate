package com.example.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportUtil {

    /**
     * Extracts text from a PDF InputStream using PDFBox-Android.
     */
    fun extractTextFromPdf(inputStream: InputStream): String {
        var document: PDDocument? = null
        try {
            document = PDDocument.load(inputStream)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            return stripper.getText(document) ?: ""
        } finally {
            document?.close()
        }
    }

    /**
     * Saves text to a standard Word (.docx) file by building its zip OpenXML entries.
     */
    fun saveTextToDocx(outputStream: OutputStream, content: String) {
        ZipOutputStream(outputStream).use { zip ->
            // 1. [Content_Types].xml
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            val contentTypes = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
            """.trimIndent()
            zip.write(contentTypes.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 2. _rels/.rels
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            val rels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
            """.trimIndent()
            zip.write(rels.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 3. word/document.xml
            zip.putNextEntry(ZipEntry("word/document.xml"))
            
            val escapedParagraphs = content.split("\n").joinToString("") { line ->
                val escapedLine = line
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                "<w:p><w:r><w:t>$escapedLine</w:t></w:r></w:p>"
            }
            
            val docXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    $escapedParagraphs
                  </w:body>
                </w:document>
            """.trimIndent()
            zip.write(docXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    /**
     * Saves plain text to a PDF document using PDFBox-Android with auto-paging.
     */
    fun saveTextToPdf(outputStream: OutputStream, content: String) {
        val document = PDDocument()
        try {
            var page = PDPage()
            document.addPage(page)
            var contentStream = PDPageContentStream(document, page)
            contentStream.beginText()
            contentStream.setFont(PDType1Font.HELVETICA, 10f)
            contentStream.newLineAtOffset(50f, 750f)

            val lines = content.split("\n")
            var yOffset = 750f
            val margin = 50f
            val lineHeight = 15f

            for (line in lines) {
                if (yOffset < margin + lineHeight) {
                    contentStream.endText()
                    contentStream.close()

                    page = PDPage()
                    document.addPage(page)
                    contentStream = PDPageContentStream(document, page)
                    contentStream.beginText()
                    contentStream.setFont(PDType1Font.HELVETICA, 10f)
                    contentStream.newLineAtOffset(50f, 750f)
                    yOffset = 750f
                }

                val cleanLine = line.filter { it.code in 32..126 || it.code in 160..255 }.trim()
                if (cleanLine.isNotEmpty()) {
                    try {
                        contentStream.showText(cleanLine)
                    } catch (e: Exception) {
                        val fallbackLine = cleanLine.filter { it.code in 32..126 }
                        if (fallbackLine.isNotEmpty()) {
                            contentStream.showText(fallbackLine)
                        }
                    }
                }
                contentStream.newLineAtOffset(0f, -lineHeight)
                yOffset -= lineHeight
            }

            contentStream.endText()
            contentStream.close()
            document.save(outputStream)
        } finally {
            document.close()
        }
    }

    /**
     * Saves plain text to a TXT file.
     */
    fun saveTextToTxt(outputStream: OutputStream, content: String) {
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }
}
