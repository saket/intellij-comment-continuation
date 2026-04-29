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
    // use PSI only as a semantic confirmation that this is a supported line comment.
    val fileExtension = psiFile.virtualFile?.extension ?: psiFile.fileType.defaultExtension
    if (fileExtension.lowercase() !in supportedFileExtensions) return null

    val element = psiFile.findElementAt(lineCommentMatch.markerRange.start) ?: return null
    val comment = (element as? PsiComment)
      ?: PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false)
      ?: return null

    return if (
      comment.textOffset == lineCommentMatch.markerRange.start &&
      (comment.text.startsWith("//") || comment.text.startsWith("#") ||
              comment.text.startsWith("--") || comment.text.startsWith(";"))
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

    // 1. Skip leading horizontal whitespace (indentation).
    var offset = lineStart
    while (offset < lineEnd && chars[offset].isHorizontalWhitespace()) {
      offset++
    }
    if (offset >= lineEnd) return null

    // 2. Detect comment marker. Inline detection avoids getOrNull() boxing into Char?.
      val markerChar: Char = when (val first = chars[offset]) {
      '#', ';' -> first
      '-', '/' -> {
        // These markers require at least two identical chars in a row.
        if (offset + 1 >= lineEnd || chars[offset + 1] != first) return null
        first
      }
      else -> return null
    }

    // 3. Consume any number of repeated marker chars (`##`, `///`, `---`, `;;;`, ...).
    var prefixEnd = offset + 1
    while (prefixEnd < lineEnd && chars[prefixEnd] == markerChar) {
      prefixEnd++
    }

    // 4. Single pass over the rest of the line:
    //    - measure indentation (horizontal whitespace right after the marker)
    //    - simultaneously detect whether the remainder contains any non-whitespace.
    var indentEnd = prefixEnd
    while (indentEnd < lineEnd && chars[indentEnd].isHorizontalWhitespace()) {
      indentEnd++
    }
    var isEmptyContinuationLine = true
    var i = indentEnd
    while (i < lineEnd) {
      if (!chars[i].isWhitespace()) {
        isEmptyContinuationLine = false
        break
      }
      i++
    }

    // 5. Avoid allocating an empty string when there's no indent after the marker.
    val indent = if (indentEnd == prefixEnd) "" else chars.substring(prefixEnd, indentEnd)

    return LineCommentMatch(
      markerRange = TextRange(offset, prefixEnd),
      indent = indent,
      isEmptyContinuationLine = isEmptyContinuationLine,
    )
  }

  private companion object {
    // Use lowercase letters only
    private val supportedFileExtensions = setOf(
      "c", "h", "cpp", "hpp", "cc", "hh", "cxx", "hxx", "cu", "cuh",
      "cs", "m", "mm",
      "java", "kt", "kts", "scala", "sc", "groovy", "ceylon",
      "js", "mjs", "cjs", "jsx", "ts", "mts", "tsx", "php", "dart",
      "go", "rs", "swift", "d", "zig", "odin", "v", "vala", "vapi", "jai", "carbon",
      "pike", "pmod", "ck", "nut", "glsl", "vert", "frag", "geom", "comp", "hlsl", "fx",
      "sol", "jolie", "ice",
      "py", "pyw", "pyi", "sh", "bash", "zsh", "fish", "rb", "rake", "gemspec",
      "yml", "yaml", "toml", "ini", "cfg", "conf", "properties", "r", "pl", "pm",
      "tcl", "cmake", "make", "mk", "dockerfile", "jl", "nim", "cr", "elixir", "ex", "exs",
      "awk", "sed", "ps1", "psm1", "psd1", "nx", "pp", "service", "timer", "target",
      "sql", "psql", "mysql", "plsql", "lua", "hs", "lhs", "ada", "adb", "ads",
      "elm", "purs", "vhdl", "vhd", "eiffel", "vif",
      "lisp", "cl", "el", "scm", "ss", "rkt", "clj", "cljs", "cljc", "edn",
      "asm", "s", "nasm", "inc", "wasm", "inf", "reg", "ahk", "iss"
    )
  }
}
