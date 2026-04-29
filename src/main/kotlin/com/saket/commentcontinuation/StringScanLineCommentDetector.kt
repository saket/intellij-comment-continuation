package com.saket.commentcontinuation

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil

class StringScanLineCommentDetector : LineCommentDetector {

  override fun findLineComment(
    editor: Editor,
    lineStart: Int,
    lineEnd: Int
  ): LineCommentMatch? {
    val lineCommentMatch = fastRejectThenParseLineComment(editor, lineStart, lineEnd) ?: return null

    val project = editor.project ?: return null
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    psiDocumentManager.commitDocument(editor.document)
    val psiFile = psiDocumentManager.getPsiFile(editor.document) ?: return null

    // The raw precheck keeps PSI off the hot path for normal Enter presses. Once we are here,
    // use PSI only as a semantic confirmation that this is a supported Java/Kotlin line comment.
    val fileExtension = psiFile.virtualFile?.extension ?: psiFile.fileType.defaultExtension
    if (fileExtension !in supportedFileExtensions) return null

    val element = psiFile.findElementAt(lineCommentMatch.markerRange.start) ?: return null
    val comment = (element as? PsiComment)
      ?: PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false)
      ?: return null

    return if (
      comment.textOffset == lineCommentMatch.markerRange.start &&
      comment.text.startsWith("//")
    ) {
      lineCommentMatch
    } else {
      null
    }
  }

  private fun fastRejectThenParseLineComment(
    editor: Editor,
    lineStart: Int,
    lineEnd: Int
  ): LineCommentMatch? {
    val chars = editor.document.charsSequence
    var offset = lineStart

    // Only spaces and tabs count as source indentation here. Using explicit char checks also keeps
    // the hot-path precheck cheaper than a broader Char#isWhitespace-style classification.
    while (offset < lineEnd && (chars[offset] == ' ' || chars[offset] == '\t')) {
      offset++
    }

    // Only comments that begin the line after indentation are candidates for continuation.
    // Trailing comments and ordinary code lines should fall back to the IDE's normal Enter flow.
    if (offset + 1 >= lineEnd || chars[offset] != '/' || chars[offset + 1] != '/') {
      return null
    }

    // Keep `///` prefixes intact so doc-style line comments continue with the same marker.
    var prefixEnd = offset + MinimumCommentPrefixLength
    while (prefixEnd < lineEnd && chars[prefixEnd] == '/') {
      prefixEnd++
    }

    var indentEnd = prefixEnd
    // todo: extract a Char#isWhitespaceFoo somewhere because this check is being done in two other places.
    while (indentEnd < lineEnd && (chars[indentEnd] == ' ' || chars[indentEnd] == '\t')) {
      indentEnd++
    }

    val indent = if (indentEnd == prefixEnd) " " else chars.substring(prefixEnd, indentEnd)
    val isEmptyContinuationLine = (indentEnd until lineEnd).all { chars[it].isWhitespace() }
    return LineCommentMatch(
      markerRange = TextRange(offset, prefixEnd),
      indent = indent,
      isEmptyContinuationLine = isEmptyContinuationLine,
    )
  }

  @Suppress("ConstPropertyName")
  private companion object {
    private const val MinimumCommentPrefixLength = 2
    private val supportedFileExtensions = setOf("java", "kt")
  }
}
