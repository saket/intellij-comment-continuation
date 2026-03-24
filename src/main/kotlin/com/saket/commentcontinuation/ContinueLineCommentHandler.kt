package com.saket.commentcontinuation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

class ContinueLineCommentHandler(
  private val originalHandler: EditorActionHandler,
  private val detector: LineCommentDetector = StringScanLineCommentDetector(),
) : EditorActionHandler(/* runForEachCaret = */ true) {

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    try {
      unsafeDoExecute(editor, caret, dataContext)
    } catch (e: Exception) {
      log.error("Comment Continuation failed, falling back to default handler", e)
      originalHandler.execute(editor, caret, dataContext)
    }
  }

  override fun isEnabledForCaret(
    editor: Editor,
    caret: Caret,
    dataContext: DataContext?,
  ): Boolean = true

  private fun unsafeDoExecute(
    editor: Editor,
    caret: Caret?,
    dataContext: DataContext,
  ) {
    val project = editor.project
    if (project == null) {
      // IntelliJ can create editors that are not attached to a project, such as standalone or
      // temporary editors, some diff/preview/viewer surfaces, and tests or other platform-created
      // editors. Our custom continuation runs inside a project-scoped write command, so it cannot
      // handle those cases.
      originalHandler.execute(editor, caret, dataContext)
      return
    }

    val activeCaret = caret ?: editor.caretModel.currentCaret
    val document = editor.document
    val caretOffset = activeCaret.offset
    val lineCommentMatch = findConfirmedLineComment(editor, caretOffset)
    if (lineCommentMatch == null) {
      originalHandler.execute(editor, caret, dataContext)
      return
    }

    val chars = document.charsSequence
    val lineNumber = document.getLineNumber(caretOffset)
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineEnd = document.getLineEndOffset(lineNumber)
    if (lineCommentMatch.isEmptyContinuationLine) {
      // Pressing Enter again on an empty generated comment line should exit the continuation,
      // similar to how markdown editors exit list items on a second Enter.
      WriteCommandAction.runWriteCommandAction(project, "Exit Comment Continuation", null, {
        document.deleteString(lineCommentMatch.start, lineEnd)
        activeCaret.moveToOffset(lineCommentMatch.start)
      })
      return
    }

    val textToInsert = buildContinuationText(chars, lineStart, lineCommentMatch)
    WriteCommandAction.runWriteCommandAction(project, "Comment Continuation", null, {
      document.insertString(caretOffset, textToInsert)
      activeCaret.moveToOffset(caretOffset + textToInsert.length)
    })
  }

  internal fun findConfirmedLineComment(editor: Editor, caretOffset: Int): LineCommentMatch? {
    val document = editor.document
    val lineNumber = document.getLineNumber(caretOffset)
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineEnd = document.getLineEndOffset(lineNumber)

    // Raw string scanning is the hot-path filter. PSI only runs for likely comment lines.
    val lineCommentMatch = detector.findLikelyLineComment(editor, lineStart, lineEnd)
    if (lineCommentMatch == null || caretOffset <= lineCommentMatch.start + 1) {
      // This excludes normal code, trailing comments, and carets placed before the comment marker.
      return null
    }

    // Only override Enter for Java/Kotlin line comments. Everything else should keep the IDE's
    // built-in Enter behavior.
    if (!detector.isConfirmedLineComment(editor, lineCommentMatch)) {
      return null
    }
    return lineCommentMatch
  }

  private fun buildContinuationText(
    chars: CharSequence,
    lineStart: Int,
    lineCommentMatch: LineCommentMatch,
  ): String {
    val indentLength = lineCommentMatch.start - lineStart
    val commentPrefixLength = lineCommentMatch.prefixEnd - lineCommentMatch.start
    return buildString(indentLength + commentPrefixLength + minimumCommentPrefixLength) {
      append('\n')
      for (offset in lineStart until lineCommentMatch.start) {
        append(chars[offset])
      }
      for (offset in lineCommentMatch.start until lineCommentMatch.prefixEnd) {
        append(chars[offset])
      }
      append(' ')
    }
  }

  private companion object {
    private const val minimumCommentPrefixLength = 2
    private val log = Logger.getInstance(ContinueLineCommentHandler::class.java)
  }
}
