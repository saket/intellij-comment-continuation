package com.saket.commentcontinuation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

data class UserPreferences(
  val shortcutMode: ShortcutMode = ShortcutMode.Enter
) {
  enum class ShortcutMode(val displayName: String) {
    Enter("Enter"),
    ShiftEnter("Shift + Enter");
  }
}

fun interface UserPreferencesReader {
  fun read(): UserPreferences
}

@Service(Service.Level.APP)
@State(
  name = "CommentContinuationSettings",
  storages = [Storage("comment-continuation.xml")],
)
class RealUserPreferencesReader :
  PersistentStateComponent<UserPreferences>,
  UserPreferencesReader {

  private var userPreferences = UserPreferences()

  override fun read(): UserPreferences {
    return userPreferences
  }

  override fun getState(): UserPreferences {
    return userPreferences
  }

  override fun loadState(state: UserPreferences) {
    userPreferences = state
  }

  fun update(transform: (UserPreferences) -> UserPreferences) {
    userPreferences = transform(userPreferences)
  }

  companion object {
    fun instance(): RealUserPreferencesReader =
      ApplicationManager
        .getApplication()
        .getService(RealUserPreferencesReader::class.java)
  }
}
