package com.saket.continuelinecomment

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

class ContinueLineCommentHandler(
  private val originalHandler: EditorActionHandler,
  private val detector: LineCommentDetector = StringScanLineCommentDetector(),
) : EditorActionHandler(true) {

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    try {
      unsafeDoExecute(editor, caret, dataContext)
    } catch (e: Exception) {
      log.error("Continue Line Comment failed, falling back to default handler", e)
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
    val lineNumber = document.getLineNumber(caretOffset)
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineEnd = document.getLineEndOffset(lineNumber)

    val slashSlashIndex = detector.indexOfLineComment(editor, lineStart, lineEnd)
    if (slashSlashIndex < 0 || caretOffset <= slashSlashIndex + 1) {
      // The detector only continues comments that begin the line after indentation. This branch
      // covers non-comment lines, trailing code comments, and carets placed before the "//" marker.
      originalHandler.execute(editor, caret, dataContext)
      return
    }

    val textToInsert = buildContinuationText(
      chars = document.charsSequence,
      lineStart = lineStart,
      slashSlashIndex = slashSlashIndex,
    )
    WriteCommandAction.runWriteCommandAction(
      /* project = */ project,
      /* commandName = */ "Continue Line Comment",
      /* groupID = */ null,
      {
        document.insertString(caretOffset, textToInsert)
        activeCaret.moveToOffset(caretOffset + textToInsert.length)
      },
    )
  }

  private fun buildContinuationText(
    chars: CharSequence,
    lineStart: Int,
    slashSlashIndex: Int,
  ): String {
    val minimumCommentPrefixLength = 2
    var commentPrefixEnd = slashSlashIndex + minimumCommentPrefixLength
    while (commentPrefixEnd < chars.length && chars[commentPrefixEnd] == '/') {
      commentPrefixEnd++
    }
    val indentLength = slashSlashIndex - lineStart
    val commentPrefixLength = commentPrefixEnd - slashSlashIndex
    return buildString(indentLength + commentPrefixLength + minimumCommentPrefixLength) {
      append('\n')
      for (offset in lineStart until slashSlashIndex) {
        append(chars[offset])
      }
      for (offset in slashSlashIndex until commentPrefixEnd) {
        append(chars[offset])
      }
      append(' ')
    }
  }

  private companion object {
    val log = Logger.getInstance(ContinueLineCommentHandler::class.java)
  }
}
