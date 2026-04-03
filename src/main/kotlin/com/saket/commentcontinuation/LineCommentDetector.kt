package com.saket.commentcontinuation

import com.intellij.openapi.editor.Editor

fun interface LineCommentDetector {
  /** Finds a supported start-of-line comment, or returns null. */
  fun findLineComment(editor: Editor, lineStart: Int, lineEnd: Int): LineCommentMatch?
}

data class LineCommentMatch(
  val start: Int,
  val prefixEnd: Int,
  val isEmptyContinuationLine: Boolean,
)
