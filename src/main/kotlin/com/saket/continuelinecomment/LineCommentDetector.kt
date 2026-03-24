package com.saket.continuelinecomment

import com.intellij.openapi.editor.Editor

interface LineCommentDetector {
  /**
   * Checks if the caret is inside a line comment on the current line.
   * Returns the index of the first '/' in "//" or -1 if not in a line comment.
   */
  fun indexOfLineComment(editor: Editor, lineStart: Int, lineEnd: Int): Int
}
