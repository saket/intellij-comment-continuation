package com.saket.commentcontinuation

/**
 * An immutable text range from [start] (inclusive) to [end] (exclusive).
 * Inspired by Compose UI.
 */
data class TextRange(val start: Int, val end: Int) {
  val length: Int get() = end - start
}

internal fun Char.isHorizontalWhitespace(): Boolean {
  return this == ' ' || this == '\t'
}
