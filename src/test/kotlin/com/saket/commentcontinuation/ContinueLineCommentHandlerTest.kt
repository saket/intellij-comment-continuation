package com.saket.commentcontinuation

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ContinueLineCommentHandlerTest : BasePlatformTestCase() {

  fun `test continues a comment when pressing shift enter at the end`() {
    testShiftEnter(
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

  fun `test continues a comment when pressing shift enter in the middle`() {
    testShiftEnter(
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
    testShiftEnter(
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
    testShiftEnter(
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
    testShiftEnter(
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

  fun `test works on an empty comment`() {
    testShiftEnter(
      before =
        """
        >// ▮
        """.trimMargin(">"),
      after =
        """
        >// 
        >// ▮
        """.trimMargin(">"),
    )
  }

  fun `test works on a comment that has no space after slashes`() {
    testShiftEnter(
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

  fun `test continues comments for multiple carets independently`() {
    testShiftEnter(
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
    myFixture.performEditorAction("EditorStartNewLine")
    assertThat(myFixture.editor.document.text).doesNotContain("\n// ")
  }

  fun `test does not continue for lines that are just code`() {
    ensureHandlerRegistered()
    myFixture.configureByText("test.java", "val x = 1▮".replace("▮", "<caret>"))
    myFixture.performEditorAction("EditorStartNewLine")
    assertThat(myFixture.editor.document.text).doesNotContain("//")
  }

  fun `test does not continue for comments that appear after code`() {
    ensureHandlerRegistered()
    myFixture.configureByText("test.java", "val x = 1 // comment▮".replace("▮", "<caret>"))
    myFixture.performEditorAction("EditorStartNewLine")
    val lines = myFixture.editor.document.text.lines()
    assertThat(lines.size).isGreaterThanOrEqualTo(2)
    assertThat(lines.last().trimStart().startsWith("//")).isFalse()
  }

  private fun testShiftEnter(before: String, after: String) {
    ensureHandlerRegistered()
    myFixture.configureByText("test.java", before.replace("▮", "<caret>"))
    myFixture.performEditorAction("EditorStartNewLine")
    myFixture.checkResult(after.replace("▮", "<caret>"))
  }
}
