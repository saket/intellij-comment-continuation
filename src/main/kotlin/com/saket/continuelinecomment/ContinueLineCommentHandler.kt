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
) : EditorActionHandler() {

  private val log = Logger.getInstance(ContinueLineCommentHandler::class.java)

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    try {
      val document = editor.document
      val caretOffset = editor.caretModel.offset
      val lineNumber = document.getLineNumber(caretOffset)
      val lineStart = document.getLineStartOffset(lineNumber)
      val lineEnd = document.getLineEndOffset(lineNumber)

      val slashSlashIndex = detector.indexOfLineComment(editor, lineStart, lineEnd)
      if (slashSlashIndex < 0 || caretOffset <= slashSlashIndex + 1) {
        originalHandler.execute(editor, caret, dataContext)
        return
      }

      val textToInsert = buildContinuationText(
        chars = document.charsSequence,
        lineStart = lineStart,
        slashSlashIndex = slashSlashIndex,
      )

      val project = editor.project
      if (project != null) {
        WriteCommandAction.runWriteCommandAction(
          /* project = */ project,
          /* commandName = */ "Continue Line Comment",
          /* groupID = */ null,
          {
            document.insertString(caretOffset, textToInsert)
            editor.caretModel.moveToOffset(caretOffset + textToInsert.length)
          },
        )
      }
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

  private fun buildContinuationText(
    chars: CharSequence,
    lineStart: Int,
    slashSlashIndex: Int,
  ): String = buildString((slashSlashIndex - lineStart) + 4) {
    append('\n')
    for (offset in lineStart until slashSlashIndex) {
      append(chars[offset])
    }
    append("// ")
  }
}
