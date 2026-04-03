package com.saket.commentcontinuation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager

class CommentContinuationHandler(
  internal val originalHandler: EditorActionHandler,
  private val actionId: String,
  private val userPreferencesReader: UserPreferencesReader,
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
    // IntelliJ's built-in editor actions can invoke each other internally. Because this plugin
    // wraps both Enter and Shift+Enter, delegating one action can otherwise re-enter this handler
    // through the other wrapped action.
    if (reentrySuppressionDepth.get() > 0) {
      originalHandler.execute(editor, caret, dataContext)
      return
    }
    if (!isEnabledForCurrentShortcutMode()) {
      // Suppress re-entry because "Disabled" has to apply to the full delegated call chain, not
      // just the first wrapped action. For example, IntelliJ's Shift+Enter handler can internally
      // invoke Enter.
      withReentrySuppressed {
        originalHandler.execute(editor, caret, dataContext)
      }
      return
    }

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

    val lineNumber = document.getLineNumber(caretOffset)
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineEnd = document.getLineEndOffset(lineNumber)
    val lineIndent = document.charsSequence.subSequence(lineStart, lineCommentMatch.start).toString()

    // Only exit on second Enter for empty comment lines that were actually created by continuing a
    // previous comment. A standalone `//` line should still continue normally.
    if (lineCommentMatch.isEmptyContinuationLine && hasPreviousLineComment(editor, lineNumber)) {
      // Pressing Enter again on an empty generated comment line should exit the continuation,
      // similar to how markdown editors exit list items on a second Enter.
      WriteCommandAction.runWriteCommandAction(project, "Exit Comment Continuation", null, {
        document.deleteString(lineCommentMatch.start, lineEnd)

        // Match the IDE's normal Enter behavior after removing the generated `//`: resync PSI,
        // re-indent the now-blank line, then move the caret to the end of that indent.
        val psiFile = PsiDocumentManager.getInstance(project).let {
          it.commitDocument(document)
          it.getPsiFile(document)
        }
        if (psiFile != null) {
          CodeStyleManager.getInstance(project).adjustLineIndent(psiFile, lineStart)
        }
        activeCaret.moveToOffset(document.getLineEndOffset(lineNumber))
      })
      return
    }

    val commentPrefix = buildContinuationPrefix(document.charsSequence, lineCommentMatch)
    val commentMarker = commentPrefix.removeSuffix(" ")
    withReentrySuppressed {
      originalHandler.execute(editor, caret, dataContext)
    }
    WriteCommandAction.runWriteCommandAction(project, "Comment Continuation", null, {
      val currentLineNumber = document.getLineNumber(activeCaret.offset)
      val currentLineStart = document.getLineStartOffset(currentLineNumber)
      val currentIndentEnd = findIndentEnd(document.charsSequence, currentLineStart)
      val currentIndentLength = currentIndentEnd - currentLineStart
      if (lineIndent != document.charsSequence.subSequence(currentLineStart, currentIndentEnd).toString()) {
        document.replaceString(currentLineStart, currentIndentEnd, lineIndent)
        if (activeCaret.offset >= currentIndentEnd) {
          activeCaret.moveToOffset(activeCaret.offset + (lineIndent.length - currentIndentLength))
        }
      }
      if (lineAlreadyHasCommentMarker(document, activeCaret.offset, lineIndent, commentMarker)) {
        return@runWriteCommandAction
      }

      val insertionOffset = activeCaret.offset
      document.insertString(insertionOffset, commentPrefix)
      activeCaret.moveToOffset(insertionOffset + commentPrefix.length)
    })
  }

  internal fun findConfirmedLineComment(editor: Editor, caretOffset: Int): LineCommentMatch? {
    val document = editor.document
    val lineNumber = document.getLineNumber(caretOffset)
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineEnd = document.getLineEndOffset(lineNumber)

    val lineCommentMatch = detector.findLineComment(editor, lineStart, lineEnd)
    if (lineCommentMatch == null || caretOffset <= lineCommentMatch.start + 1) {
      // This excludes normal code, trailing comments, and carets placed before the comment marker.
      return null
    }
    return lineCommentMatch
  }

  private fun hasPreviousLineComment(editor: Editor, lineNumber: Int): Boolean {
    if (lineNumber == 0) return false

    val document = editor.document
    val previousLineNumber = lineNumber - 1
    val previousLineStart = document.getLineStartOffset(previousLineNumber)
    val previousLineEnd = document.getLineEndOffset(previousLineNumber)
    return detector.findLineComment(editor, previousLineStart, previousLineEnd) != null
  }

  private fun isEnabledForCurrentShortcutMode(): Boolean {
    return when (userPreferencesReader.read().shortcutMode) {
      UserPreferences.ShortcutMode.Enter -> actionId == IdeActions.ACTION_EDITOR_ENTER
      UserPreferences.ShortcutMode.ShiftEnter -> actionId == IdeActions.ACTION_EDITOR_START_NEW_LINE
    }
  }

  private fun buildContinuationPrefix(
    chars: CharSequence,
    lineCommentMatch: LineCommentMatch,
  ): String {
    val commentPrefixLength = lineCommentMatch.prefixEnd - lineCommentMatch.start
    return buildString(commentPrefixLength + MinimumCommentPrefixLength) {
      for (offset in lineCommentMatch.start until lineCommentMatch.prefixEnd) {
        append(chars[offset])
      }
      append(' ')
    }
  }

  private fun lineAlreadyHasCommentMarker(
    document: com.intellij.openapi.editor.Document,
    caretOffset: Int,
    expectedIndent: String,
    commentMarker: String,
  ): Boolean {
    val chars = document.charsSequence
    val lineNumber = document.getLineNumber(caretOffset)
    val lineStart = document.getLineStartOffset(lineNumber)
    val markerStart = lineStart + expectedIndent.length
    val markerEnd = markerStart + commentMarker.length
    if (markerEnd > document.getLineEndOffset(lineNumber)) {
      return false
    }
    return chars.subSequence(markerStart, markerEnd).toString() == commentMarker
  }

  private fun findIndentEnd(chars: CharSequence, lineStart: Int): Int {
    var offset = lineStart
    while (offset < chars.length && (chars[offset] == ' ' || chars[offset] == '\t')) {
      offset++
    }
    return offset
  }

  @Suppress("ConstPropertyName")
  private companion object {
    private const val MinimumCommentPrefixLength = 2
    private val log = Logger.getInstance(CommentContinuationHandler::class.java)

    // Re-entry happens synchronously on the current editor-action thread, so this shares
    // suppression state across both wrapped handlers only for the active call chain.
    private val reentrySuppressionDepth = ThreadLocal.withInitial { 0 }

    // Suppresses nested editor-action delegation on the current thread so a disabled
    // shortcut does not re-enter comment continuation via another wrapped built-in action.
    private inline fun withReentrySuppressed(block: () -> Unit) {
      reentrySuppressionDepth.set(reentrySuppressionDepth.get() + 1)
      try {
        block()
      } finally {
        reentrySuppressionDepth.set(reentrySuppressionDepth.get() - 1)
      }
    }
  }
}
