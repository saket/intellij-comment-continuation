package com.saket.continuelinecomment

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ContinueLineCommentHandlerTest : BasePlatformTestCase() {

  fun `test continues a comment when pressing shift enter at the end`() {
    testShiftEnter(
      before = "// hello▮",
      after = "// hello\n// ▮",
    )
  }

  fun `test continues a comment when pressing shift enter in the middle`() {
    testShiftEnter(
      before = "// hel▮lo",
      after = "// hel\n// ▮lo",
    )
  }

  fun `test keeps leading spaces on the next line`() {
    testShiftEnter(
      before = "    // hello▮",
      after = "    // hello\n    // ▮",
    )
  }

  fun `test keeps leading tabs on the next line`() {
    testShiftEnter(
      before = "\t\t// hello▮",
      after = "\t\t// hello\n\t\t// ▮",
    )
  }

  fun `test works with triple slash comments`() {
    testShiftEnter(
      before = "/// doc comment▮",
      after = "/// doc comment\n// ▮",
    )
  }

  fun `test works on an empty comment`() {
    testShiftEnter(
      before = "// ▮",
      after = "// \n// ▮",
    )
  }

  fun `test works on a comment that has no space after slashes`() {
    testShiftEnter(
      before = "//hello▮",
      after = "//hello\n// ▮",
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
