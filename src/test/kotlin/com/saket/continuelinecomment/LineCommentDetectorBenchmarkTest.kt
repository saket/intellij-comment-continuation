package com.saket.continuelinecomment

import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.time.Duration
import kotlin.time.measureTime

class LineCommentDetectorBenchmarkTest : BasePlatformTestCase() {

  fun `test string scanning is faster than PSI for finding line comments`() {
    myFixture.configureByText("View.java", FileContent)
    val lines = FileContent.lines()

    // Find a comment line near the middle of the file, inside nested code.
    val middleLine = lines.size / 2
    val targetLineIndex = lines.subList(middleLine, lines.size)
      .indexOfFirst { it.trimStart().startsWith("//") }
      .let { it + middleLine }
    assertThat(targetLineIndex).isGreaterThanOrEqualTo(middleLine)

    val lineStart = lines.take(targetLineIndex).sumOf { it.length + 1 }
    val lineEnd = lineStart + lines[targetLineIndex].length

    val stringScanTime = benchmark(
      detector = StringScanLineCommentDetector(),
      editor = myFixture.editor,
      lineStart = lineStart,
      lineEnd = lineEnd
    )
    val psiTime = benchmark(
      detector = PsiLineCommentDetector(),
      editor = myFixture.editor,
      lineStart = lineStart,
      lineEnd = lineEnd
    )

    val stringScanAvg = stringScanTime / BenchmarkIterations
    val psiAvg = psiTime / BenchmarkIterations
    val speedup = psiTime / stringScanTime

    println(
      """
      |
      |=== Benchmark: $BenchmarkIterations iterations on AOSP View.java (${lines.size} lines) ===
      |Comment on line ${targetLineIndex + 1}: "${lines[targetLineIndex].trim()}"
      |
      |String scan:  $stringScanAvg avg  ($stringScanTime total)
      |PSI:          $psiAvg avg  ($psiTime total)
      |String scan is ${"%.1f".format(speedup)}x faster
      |
    """.trimMargin()
    )

    assertThat(speedup).isGreaterThan(1.0)
  }

  private fun benchmark(
    detector: LineCommentDetector,
    editor: Editor,
    lineStart: Int,
    lineEnd: Int,
  ): Duration {
    repeat(WarmupIterations) {
      detector.indexOfLineComment(editor, lineStart, lineEnd)
    }
    return measureTime {
      repeat(BenchmarkIterations) {
        detector.indexOfLineComment(editor, lineStart, lineEnd)
      }
    }
  }

  @Suppress("MayBeConstant")
  companion object {
    private val WarmupIterations = 1_000
    private val BenchmarkIterations = 10_000

    private val FileContent: String by lazy {
      val stream = LineCommentDetectorBenchmarkTest::class.java.classLoader
        .getResourceAsStream("View.java")
        ?: error("View.java not found in test resources")
      stream.bufferedReader().readText()
    }
  }
}

/** Baseline detector that uses PSI tree traversal, used only for benchmark comparison. */
private class PsiLineCommentDetector : LineCommentDetector {
  override fun indexOfLineComment(editor: Editor, lineStart: Int, lineEnd: Int): Int {
    val project = editor.project ?: return -1
    val psiDocManager = PsiDocumentManager.getInstance(project)
    psiDocManager.commitDocument(editor.document)
    val psiFile = psiDocManager.getPsiFile(editor.document) ?: return -1

    val element = psiFile.findElementAt(lineStart)
      ?: psiFile.findElementAt((lineStart - 1).coerceAtLeast(0))
      ?: return -1

    val comment = (element as? PsiComment)
      ?: PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false)
      ?: return -1

    if (!comment.text.startsWith("//")) return -1
    return comment.textOffset
  }
}