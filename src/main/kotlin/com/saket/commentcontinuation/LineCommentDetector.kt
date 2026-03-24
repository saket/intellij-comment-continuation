package com.saket.commentcontinuation

import com.intellij.openapi.editor.Editor

interface LineCommentDetector {
  /**
   * Fast hot-path precheck. Returns parsed line-comment structure, or null if the current line is
   * not a plausible start-of-line comment candidate.
   */
  fun findLikelyLineComment(editor: Editor, lineStart: Int, lineEnd: Int): LineCommentMatch?

  /**
   * Slower semantic confirmation that runs only after the fast candidate check passes.
   */
  fun isConfirmedLineComment(editor: Editor, match: LineCommentMatch): Boolean
}

data class LineCommentMatch(
  val start: Int,
  val prefixEnd: Int,
  val isEmptyContinuationLine: Boolean,
)
