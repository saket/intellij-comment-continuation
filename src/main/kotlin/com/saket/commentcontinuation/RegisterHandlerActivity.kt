package com.saket.commentcontinuation

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean

class RegisterHandlerActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    ensureHandlerRegistered()
  }
}

// Kept top-level because IntelliJ warns when extension implementation companion
// objects contain mutable state or helpers beyond constants and a logger.
private val registered = AtomicBoolean(false)

internal fun ensureHandlerRegistered() {
  if (!registered.compareAndSet(false, true)) {
    return
  }
  val manager = EditorActionManager.getInstance()
  val userPreferencesReader = RealUserPreferencesReader.instance()
  for (actionId in SupportedEditorActionIds) {
    val handler = CommentContinuationHandler(
      actionId = actionId,
      userPreferencesReader = userPreferencesReader,
      originalHandler = manager.getActionHandler(actionId),
    )
    manager.setActionHandler(actionId, handler)
  }
}

private val SupportedEditorActionIds = listOf(
  IdeActions.ACTION_EDITOR_ENTER,
  IdeActions.ACTION_EDITOR_START_NEW_LINE,
)
