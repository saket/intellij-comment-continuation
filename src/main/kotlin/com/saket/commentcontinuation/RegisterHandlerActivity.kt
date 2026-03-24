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

private val registered = AtomicBoolean(false)

internal fun ensureHandlerRegistered() {
  if (!registered.compareAndSet(false, true)) {
    return
  }

  val manager = EditorActionManager.getInstance()
  val originalHandler = manager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
  manager.setActionHandler(
    IdeActions.ACTION_EDITOR_ENTER,
    ContinueLineCommentHandler(originalHandler),
  )
}
