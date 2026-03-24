package com.saket.commentcontinuation

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class UserPreferencesScreen : Configurable {
  private val settings = RealUserPreferencesReader.instance()

  private val panel: DialogPanel = panel {
    row("Continue comments with:") {
      comboBox(UserPreferences.ShortcutMode.entries)
        .applyToComponent {
          renderer = SimpleListCellRenderer.create("") { mode ->
            mode?.displayName.orEmpty()
          }
        }
        .bindItem(
          getter = { settings.read().shortcutMode },
          setter = { mode ->
            settings.loadState(
              settings.read().copy(shortcutMode = mode ?: UserPreferences().shortcutMode)
            )
          },
        )
    }
  }

  override fun getDisplayName(): String = "Comment Continuation"
  override fun createComponent(): JComponent = panel
  override fun reset() = panel.reset()
  override fun isModified(): Boolean = panel.isModified()
  override fun apply() = panel.apply()
}
