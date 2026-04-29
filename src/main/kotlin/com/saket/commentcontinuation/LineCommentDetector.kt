package com.saket.commentcontinuation

import com.intellij.openapi.editor.Editor

fun interface LineCommentDetector {
  /** Finds a supported start-of-line comment, or returns null. */
  fun findLineComment(editor: Editor, lineStart: Int, lineEnd: Int): LineCommentMatch?
}

data class LineCommentMatch(
  /** Offsets of the `//` (or `///`) marker, from the first slash to just past the last. */
  val markerRange: TextRange,

  /** Whitespace (spaces/tabs) between the marker and the comment's content. */
  val indent: String,

  // todo: kdoc.
  val isEmptyContinuationLine: Boolean,
)
