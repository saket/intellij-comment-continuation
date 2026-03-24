package com.saket.commentcontinuation

import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.time.Duration
import kotlin.time.measureTime

class LineCommentDetectorBenchmarkTest : BasePlatformTestCase() {

  fun `test string scanning is faster than PSI for rejecting non comment lines`() {
    myFixture.configureByText("View.java", FileContent)
    val lines = FileContent.lines()

    // Find a non-comment line near the middle of the file, inside nested code.
    val middleLine = lines.size / 2
    val targetLineIndex = lines.subList(middleLine, lines.size)
      .indexOfFirst { line ->
        val trimmed = line.trimStart()
        trimmed.isNotEmpty() && !trimmed.startsWith("//")
      }
      .let { it + middleLine }
    assertThat(targetLineIndex).isGreaterThanOrEqualTo(middleLine)

    val lineStart = lines.take(targetLineIndex).sumOf { it.length + 1 }
    val lineEnd = lineStart + lines[targetLineIndex].length

    val stringScanTime = benchmarkHandler(
      handler = ContinueLineCommentHandler(
        originalHandler = NoOpEditorActionHandler,
        actionId = IdeActions.ACTION_EDITOR_ENTER,
        userPreferencesReader = DefaultUserPreferences,
        detector = StringScanLineCommentDetector()
      ),
      editor = myFixture.editor,
      caretOffset = lineEnd,
    )
    val psiTime = benchmarkHandler(
      handler = ContinueLineCommentHandler(
        originalHandler = NoOpEditorActionHandler,
        actionId = IdeActions.ACTION_EDITOR_ENTER,
        userPreferencesReader = DefaultUserPreferences,
        detector = PsiOnlyLineCommentDetector(),
      ),
      editor = myFixture.editor,
      caretOffset = lineEnd,
    )

    val stringScanAvg = stringScanTime / BenchmarkIterations
    val psiAvg = psiTime / BenchmarkIterations
    val speedup = psiTime / stringScanTime

    println(
      """
      |
      |=== Benchmark: $BenchmarkIterations iterations on AOSP View.java (${lines.size} lines) ===
      |Non-comment line ${targetLineIndex + 1}: "${lines[targetLineIndex].trim()}"
      |
      |String scan:  $stringScanAvg avg  ($stringScanTime total)
      |PSI:          $psiAvg avg  ($psiTime total)
      |String scan is ${"%.1f".format(speedup)}x faster
      |
    """.trimMargin()
    )

    assertThat(speedup).isGreaterThan(1.0)
  }

  private fun benchmarkHandler(
    handler: ContinueLineCommentHandler,
    editor: Editor,
    caretOffset: Int,
  ): Duration {
    repeat(WarmupIterations) {
      handler.findConfirmedLineComment(editor, caretOffset)
    }
    return measureTime {
      repeat(BenchmarkIterations) {
        handler.findConfirmedLineComment(editor, caretOffset)
      }
    }
  }

  @Suppress("MayBeConstant")
  companion object {
    private val WarmupIterations = 1_000
    private val BenchmarkIterations = 10_000
    private val NoOpEditorActionHandler = object : EditorActionHandler() {}
    private val DefaultUserPreferences = UserPreferencesReader { UserPreferences() }

    private val FileContent: String by lazy {
      val stream = LineCommentDetectorBenchmarkTest::class.java.classLoader
        .getResourceAsStream("View.java")
        ?: error("View.java not found in test resources")
      stream.bufferedReader().readText()
    }
  }
}

/** Baseline detector that uses PSI tree traversal, used only for benchmark comparison. */
private class PsiOnlyLineCommentDetector : LineCommentDetector {
  override fun findLikelyLineComment(
    editor: Editor,
    lineStart: Int,
    lineEnd: Int
  ): LineCommentMatch? {
    val project = editor.project ?: return null
    val psiDocManager = PsiDocumentManager.getInstance(project)
    psiDocManager.commitDocument(editor.document)
    val psiFile = psiDocManager.getPsiFile(editor.document) ?: return null

    val element = psiFile.findElementAt(lineStart)
      ?: psiFile.findElementAt((lineStart - 1).coerceAtLeast(0))
      ?: return null

    val comment = (element as? PsiComment)
      ?: PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false)
      ?: return null

    if (!comment.text.startsWith("//")) return null
    val commentStart = comment.textOffset
    val commentEnd = commentStart + comment.text.takeWhile { it == '/' }.length
    val isEmptyContinuationLine =
      comment.text.drop(commentEnd - commentStart).all { it.isWhitespace() }
    return LineCommentMatch(
      start = commentStart,
      prefixEnd = commentEnd,
      isEmptyContinuationLine = isEmptyContinuationLine,
    )
  }

  override fun isConfirmedLineComment(editor: Editor, match: LineCommentMatch): Boolean = true
}
