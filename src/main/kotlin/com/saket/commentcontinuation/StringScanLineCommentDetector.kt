package com.saket.commentcontinuation

import com.intellij.lang.Language
import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.text.CharArrayUtil


class StringScanLineCommentDetector : LineCommentDetector {
  override fun findLineComment(
    editor: Editor,
    lineStart: Int,
    lineEnd: Int
  ): LineCommentMatch? {
    val chars = editor.document.charsSequence
    val contentStart = contentStartOrNull(chars, lineStart, lineEnd) ?: return null

    val prefix = lineCommentPrefixAt(editor, contentStart) ?: return null
    return parseLineComment(chars, contentStart, lineEnd, prefix)
  }

  /**
   * Inexpensive pre-check: returns the offset of the first non-whitespace char if it could be a comment
   * marker registered by a language commenter, or null otherwise.
   */
  private fun contentStartOrNull(chars: CharSequence, lineStart: Int, lineEnd: Int): Int? {
    val offset = skipHorizontalWhitespace(chars, lineStart, lineEnd)
    return offset.takeIf { it < lineEnd && chars[it] in lineCommentStartChars }
  }

  private fun skipHorizontalWhitespace(chars: CharSequence, from: Int, to: Int): Int {
    var offset = from
    while (offset < to && chars[offset].isHorizontalWhitespace()) {
      offset++
    }
    return offset
  }

  /**
   * Resolves the line-comment prefix (e.g. `//`, `#`, `;`, `--`) for the language at [offset].
   *
   * Uses the language *at the offset* rather than the file's base language so injected/composite
   * files (templates, fenced code, etc.) resolve to the correct commenter.
   */
  private fun lineCommentPrefixAt(editor: Editor, offset: Int): String? {
    val project = editor.project ?: return null
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    psiDocumentManager.commitDocument(editor.document)
    val psiFile = psiDocumentManager.getPsiFile(editor.document) ?: return null

    val language = PsiUtilCore.getLanguageAtOffset(psiFile, offset)
    val prefix = LanguageCommenters.INSTANCE.forLanguage(language)?.lineCommentPrefix
    return prefix?.takeIf { it.isNotEmpty() }
  }

  private fun parseLineComment(
    chars: CharSequence,
    contentStart: Int,
    lineEnd: Int,
    prefix: String,
  ): LineCommentMatch? {
    // 1. Match the language's comment prefix at the start of the trimmed line.
    if (contentStart + prefix.length > lineEnd || !chars.startsWith(prefix, contentStart)) return null
    var prefixEnd = contentStart + prefix.length

    // 2. Consume extra repeated marker chars only for single-repeated-char prefixes
    //    (`//` -> `///`, `#` -> `##`, `;` -> `;;`, `--` -> `---`, ...).
    val fillChar = prefix.singleRepeatedCharOrNull()
    if (fillChar != null) {
      while (prefixEnd < lineEnd && chars[prefixEnd] == fillChar) {
        prefixEnd++
      }
    }

    // 3. Measure indentation after the marker and detect whether any content follows.
    val indentEnd = CharArrayUtil.shiftForward(chars, prefixEnd, HORIZONTAL_WHITESPACE).coerceAtMost(lineEnd)
    val isEmptyContinuationLine = (indentEnd until lineEnd).all { chars[it].isWhitespace() }

    return LineCommentMatch(
      markerRange = TextRange(contentStart, prefixEnd),
      indent = chars.substring(prefixEnd, indentEnd),
      isEmptyContinuationLine = isEmptyContinuationLine,
    )
  }

  /** Returns the fill char if the prefix is one char repeated (`//`, `##`, `--`), else null. */
  private fun String.singleRepeatedCharOrNull(): Char? {
    val first = firstOrNull() ?: return null
    return first.takeIf { all { c -> c == first } }
  }

  companion object {
    private const val HORIZONTAL_WHITESPACE = " \t"

    internal fun prewarm() {
      // Reading the lazy property initializes the cache before editor handlers are installed.
      lineCommentStartChars
    }

    private val lineCommentStartChars: Set<Char> by lazy(LazyThreadSafetyMode.NONE) {
      Language.getRegisteredLanguages().mapNotNullTo(mutableSetOf()) { language ->
        LanguageCommenters.INSTANCE.forLanguage(language)?.lineCommentPrefix?.firstOrNull()
      }
    }
  }
}
