package com.saket.commentcontinuation

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil

class StringScanLineCommentDetector : LineCommentDetector {

  override fun findLikelyLineComment(
    editor: Editor,
    lineStart: Int,
    lineEnd: Int
  ): LineCommentMatch? {
    val chars = editor.document.charsSequence
    var offset = lineStart

    // Skip whitespaces.
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

    val isEmptyContinuationLine = (prefixEnd until lineEnd).all { chars[it].isWhitespace() }
    return LineCommentMatch(
      start = offset,
      prefixEnd = prefixEnd,
      isEmptyContinuationLine = isEmptyContinuationLine,
    )
  }

  override fun isConfirmedLineComment(editor: Editor, match: LineCommentMatch): Boolean {
    val project = editor.project ?: return false
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    psiDocumentManager.commitDocument(editor.document)
    val psiFile = psiDocumentManager.getPsiFile(editor.document) ?: return false

    // The raw precheck keeps PSI off the hot path for normal Enter presses. Once we are here,
    // use PSI only as a semantic confirmation that this is a supported Java/Kotlin line comment.
    val fileExtension = psiFile.virtualFile?.extension ?: psiFile.fileType.defaultExtension
    if (fileExtension !in supportedFileExtensions) return false

    val element = psiFile.findElementAt(match.start) ?: return false
    val comment = (element as? PsiComment)
      ?: PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false)
      ?: return false

    return comment.textOffset == match.start && comment.text.startsWith("//")
  }

  @Suppress("ConstPropertyName")
  private companion object {
    private const val MinimumCommentPrefixLength = 2
    private val supportedFileExtensions = setOf("java", "kt")
  }
}
