package com.saket.commentcontinuation

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.saket.commentcontinuation.UserPreferences.EnterOnEmptyLineBehavior
import com.saket.commentcontinuation.UserPreferences.ShortcutMode
import javax.swing.JComponent

class UserPreferencesScreen : Configurable {
  private val settings = RealUserPreferencesReader.instance()

  private val panel: DialogPanel = panel {
    row("Continue comments with:") {
      comboBox(ShortcutMode.entries)
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
    buttonsGroup("On Enter at an empty continuation line:") {
      row {
        radioButton(
          text = EnterOnEmptyLineBehavior.Exit.displayName,
          value = EnterOnEmptyLineBehavior.Exit,
        )
      }
      row {
        radioButton(
          text = EnterOnEmptyLineBehavior.StepBack.displayName,
          value = EnterOnEmptyLineBehavior.StepBack,
        ).comment(
          "On a deeper-indented continuation line, Enter steps back one indent level. Repeat to exit the comment.",
        )
      }
    }.bind(
      object : MutableProperty<EnterOnEmptyLineBehavior> {
        override fun get() = settings.read().emptyLineBehavior
        override fun set(value: EnterOnEmptyLineBehavior) {
          settings.loadState(settings.read().copy(emptyLineBehavior = value))
        }
      },
      EnterOnEmptyLineBehavior::class.java,
    )
  }

  override fun getDisplayName(): String = "Comment Continuation"
  override fun createComponent(): JComponent = panel
  override fun reset() = panel.reset()
  override fun isModified(): Boolean = panel.isModified()
  override fun apply() = panel.apply()
}
