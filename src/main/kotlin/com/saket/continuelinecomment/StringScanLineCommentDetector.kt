package com.saket.continuelinecomment

import com.intellij.openapi.editor.Editor

class StringScanLineCommentDetector : LineCommentDetector {
  override fun indexOfLineComment(editor: Editor, lineStart: Int, lineEnd: Int): Int {
    val chars = editor.document.charsSequence
    var offset = lineStart
    while (offset < lineEnd && (chars[offset] == ' ' || chars[offset] == '\t')) {
      offset++
    }
    if (offset + 1 < lineEnd && chars[offset] == '/' && chars[offset + 1] == '/') {
      return offset
    }
    return -1
  }
}
