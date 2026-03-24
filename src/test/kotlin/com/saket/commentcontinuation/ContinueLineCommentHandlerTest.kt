package com.saket.commentcontinuation

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ContinueLineCommentHandlerTest : BasePlatformTestCase() {

  fun `test continues a Java comment when pressing enter at the end`() {
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

  fun `test continues a Kotlin comment when pressing enter in the middle`() {
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

  fun `test keeps leading spaces on the next line`() {
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

  fun `test keeps leading tabs on the next line`() {
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

  fun `test works with triple slash comments`() {
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

  fun `test works on a comment that has no space after slashes`() {
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

  fun `test exits an auto continued comment on second enter`() {
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

  fun `test exits an auto continued indented comment on second enter`() {
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

  fun `test continues comments for multiple carets independently`() {
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

  fun `test does not continue when the caret is before the comment`() {
    ensureHandlerRegistered()
    myFixture.configureByText("test.java", "▮// hello".replace("▮", "<caret>"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("\n<caret>// hello")
  }

  fun `test does not continue for lines that are just code`() {
    ensureHandlerRegistered()
    myFixture.configureByText("test.java", "val x = 1▮".replace("▮", "<caret>"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    assertThat(myFixture.editor.document.text).doesNotContain("//")
  }

  fun `test does not continue for comments that appear after code`() {
    ensureHandlerRegistered()
    myFixture.configureByText("test.java", "val x = 1 // comment▮".replace("▮", "<caret>"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    val lines = myFixture.editor.document.text.lines()
    assertThat(lines.size).isGreaterThanOrEqualTo(2)
    assertThat(lines.last().trimStart().startsWith("//")).isFalse()
  }

  fun `test does not continue comments in unsupported file types`() {
    ensureHandlerRegistered()
    myFixture.configureByText("test.js", "// hello<caret>")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    assertThat(myFixture.editor.document.text).doesNotContain("\n// ")
  }

  private fun testEnter(fileName: String, before: String, after: String) {
    ensureHandlerRegistered()
    myFixture.configureByText(fileName, before.replace("▮", "<caret>"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult(after.replace("▮", "<caret>"))
  }
}
