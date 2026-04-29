package com.saket.commentcontinuation

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ContinueLineCommentHandlerTest : BasePlatformTestCase() {
  @Test fun `continues a Java comment when pressing enter at the end`() {
    testEnter(
      fileName = "test.java",
      before =
        """
        >// hello▮
        """.trimMargin(">"),
      after =
        """
        >// hello
        >// ▮
        """.trimMargin(">"),
    )
  }

  @Test fun `continues a Kotlin comment when pressing enter in the middle`() {
    testEnter(
      fileName = "test.kt",
      before =
        """
        >// hel▮lo
        """.trimMargin(">"),
      after =
        """
        >// hel
        >// ▮lo
        """.trimMargin(">"),
    )
  }

  @Test fun `continues a Groovy comment when pressing enter at the end`() {
    // Groovy is not in the original {java, kt} allowlist, but its line comment prefix is `//`,
    // so the Commenter-based gate should accept it.
    testEnter(
      fileName = "test.groovy",
      before =
        """
        >// hello▮
        """.trimMargin(">"),
      after =
        """
        >// hello
        >// ▮
        """.trimMargin(">"),
    )
  }

  @Test fun `keeps leading spaces on the next line`() {
    testEnter(
      fileName = "test.java",
      before =
        """
        >    // hello▮
        """.trimMargin(">"),
      after =
        """
        >    // hello
        >    // ▮
        """.trimMargin(">"),
    )
  }

  @Test fun `keeps leading tabs on the next line`() {
    testEnter(
      fileName = "test.kt",
      before =
        """
        >		// hello▮
        """.trimMargin(">"),
      after =
        """
        >		// hello
        >		// ▮
        """.trimMargin(">"),
    )
  }

  @Test fun `works with triple slash comments`() {
    testEnter(
      fileName = "test.java",
      before =
        """
        >/// doc comment▮
        """.trimMargin(">"),
      after =
        """
        >/// doc comment
        >/// ▮
        """.trimMargin(">"),
    )
  }

  @Test fun `works on a comment that has no space after slashes`() {
    testEnter(
      fileName = "test.kt",
      before =
        """
        >//hello▮
        """.trimMargin(">"),
      after =
        """
        >//hello
        >// ▮
        """.trimMargin(">"),
    )
  }

  @Test fun `exits an auto continued comment on second enter`() {
    testEnter(
      fileName = "test.java",
      before =
        """
        >// hello
        >// ▮
        """.trimMargin(">"),
      after =
        """
        >// hello
        >▮
        """.trimMargin(">"),
    )
  }

  @Test fun `exits an auto continued indented comment on second enter`() {
    testEnter(
      fileName = "test.kt",
      before =
        """
        >    // hello
        >    // ▮
        """.trimMargin(">"),
      after =
        """
        >    // hello
        >    ▮
        """.trimMargin(">"),
    )
  }

  @Test fun `continues comments for multiple carets independently`() {
    testEnter(
      fileName = "test.java",
      before =
        """
        >// first▮
        >// second▮
        """.trimMargin(">"),
      after =
        """
        >// first
        >// ▮
        >// second
        >// ▮
        """.trimMargin(">"),
    )
  }

  @Test fun `does not continue when the caret is before the comment`() {
    installHandlers()
    myFixture.configureByText("test.java", "▮// hello".replace("▮", "<caret>"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("\n<caret>// hello")
  }

  @Test fun `does not continue for lines that are just code`() {
    installHandlers()
    myFixture.configureByText("test.java", "val x = 1▮".replace("▮", "<caret>"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    assertThat(myFixture.editor.document.text).doesNotContain("//")
  }

  @Test fun `does not continue for comments that appear after code`() {
    installHandlers()
    myFixture.configureByText("test.java", "val x = 1 // comment▮".replace("▮", "<caret>"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    val lines = myFixture.editor.document.text.lines()
    assertThat(lines.size).isGreaterThanOrEqualTo(2)
    assertThat(lines.last().trimStart().startsWith("//")).isFalse()
  }

  @Test fun `does not continue in languages whose line comment prefix is not slash slash`() {
    // Properties files use `#` for line comments, so even a literal `//` at the start of a line
    // should not trigger continuation.
    installHandlers()
    myFixture.configureByText("test.properties", "// hello<caret>")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    assertThat(myFixture.editor.document.text).doesNotContain("\n// ")
  }

  @Test fun `does not continue in plain text files`() {
    // Plain text has no Commenter at all, so `//` is just text.
    installHandlers()
    myFixture.configureByText("test.txt", "// hello<caret>")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    assertThat(myFixture.editor.document.text).doesNotContain("\n// ")
  }

  @Test fun `shift enter mode disables enter continuation`() {
    installHandlers(
      UserPreferences(shortcutMode = UserPreferences.ShortcutMode.ShiftEnter)
    )
    myFixture.configureByText("test.java", "// hello<caret>")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    assertThat(myFixture.editor.document.text).doesNotContain("\n// ")
  }

  @Test fun `shift enter mode continues comments`() {
    installHandlers(
      UserPreferences(shortcutMode = UserPreferences.ShortcutMode.ShiftEnter)
    )
    myFixture.configureByText("test.java", "// hello<caret>")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
    myFixture.checkResult("// hello\n// <caret>")
  }

  @Test fun `exits an unindented continuation line using the IDE indent`() {
    testEnter(
      fileName = "test.kt",
      before =
        """
        >fun example() {
        >// val foo = "bar"
        >// ▮
        >}
        """.trimMargin(">"),
      after =
        """
        >fun example() {
        >// val foo = "bar"
        >    ▮
        >}
        """.trimMargin(">"),
    )
  }

  @Test fun `enter mode does not continue comments on shift enter`() {
    installHandlers(
      userPreferences = UserPreferences(shortcutMode = UserPreferences.ShortcutMode.Enter)
    )
    myFixture.configureByText("test.java", "// hello<caret>")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
    assertThat(myFixture.editor.document.text).doesNotContain("\n// ")
  }

  @Test fun `does not exit comment continuation on a standalone empty comment line`() {
    testEnter(
      fileName = "test.kt",
      before =
        """
        > val someUnrelatedCode = 42
        > 
        >// ▮
        """.trimMargin(">"),
      after =
        """
        > val someUnrelatedCode = 42
        > 
        >// 
        >// ▮
        """.trimMargin(">"),
    )
  }

  private fun testEnter(fileName: String, before: String, after: String) {
    installHandlers()
    myFixture.configureByText(fileName, before.replace("▮", "<caret>"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult(after.replace("▮", "<caret>"))
  }

  private fun installHandlers(
    userPreferences: UserPreferences = UserPreferences(),
  ) {
    val manager = EditorActionManager.getInstance()
    for (actionId in supportedEditorActionIds) {
      manager.setActionHandler(
        actionId,
        CommentContinuationHandler(
          originalHandler = unwrapOriginalHandler(manager.getActionHandler(actionId)),
          actionId = actionId,
          userPreferencesReader = { userPreferences },
        ),
      )
    }
  }
}

private tailrec fun unwrapOriginalHandler(handler: EditorActionHandler): EditorActionHandler {
  // Test IDE startup can install this plugin's handler before the test replaces it. Unwrap any
  // existing Comment Continuation handler so each test rebuilds from the platform's real handler.
  return if (handler is CommentContinuationHandler) {
    unwrapOriginalHandler(handler.originalHandler)
  } else {
    handler
  }
}

private val supportedEditorActionIds = listOf(
  IdeActions.ACTION_EDITOR_ENTER,
  IdeActions.ACTION_EDITOR_START_NEW_LINE,
)
