package dev.davidv.detekt

import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.Assert.assertEquals
import org.junit.Test

class NoHardcodedUiStringTest {
  private val rule = NoHardcodedUiString()

  @Test
  fun `flags a plain literal passed to Text`() {
    val findings = rule.compileAndLint(
      """
      fun Text(s: String) {}
      fun screen() { Text("Hello") }
      """.trimIndent(),
    )
    assertEquals(1, findings.size)
  }

  @Test
  fun `flags a literal passed to Toast makeText`() {
    val findings = rule.compileAndLint(
      """
      object Toast { fun makeText(c: Any?, s: String, len: Int) {} }
      fun show() { Toast.makeText(null, "Bad thing", 0) }
      """.trimIndent(),
    )
    assertEquals(1, findings.size)
  }

  @Test
  fun `flags an interpolated template with literal text`() {
    val findings = rule.compileAndLint(
      """
      fun Text(s: String) {}
      fun screen(x: Int, y: Int) { Text("Converting ${'$'}x of ${'$'}y") }
      """.trimIndent(),
    )
    assertEquals(1, findings.size)
  }

  @Test
  fun `ignores a variable argument`() {
    val findings = rule.compileAndLint(
      """
      fun Text(s: String) {}
      fun screen(label: String) { Text(label) }
      """.trimIndent(),
    )
    assertEquals(0, findings.size)
  }

  @Test
  fun `ignores a pure variable template`() {
    val findings = rule.compileAndLint(
      """
      fun Text(s: String) {}
      fun screen(x: Int) { Text("${'$'}x") }
      """.trimIndent(),
    )
    assertEquals(0, findings.size)
  }

  @Test
  fun `ignores a symbol-only literal`() {
    val findings = rule.compileAndLint(
      """
      fun Text(s: String) {}
      fun screen() { Text("/") }
      """.trimIndent(),
    )
    assertEquals(0, findings.size)
  }

  @Test
  fun `ignores literals inside a Preview composable`() {
    val findings = rule.compileAndLint(
      """
      annotation class Preview
      fun Text(s: String) {}
      @Preview
      fun screenPreview() { Text("Sample") }
      """.trimIndent(),
    )
    assertEquals(0, findings.size)
  }

  @Test
  fun `flags concatenated literals`() {
    val findings = rule.compileAndLint(
      """
      fun Text(s: String) {}
      fun screen() { Text("A long " + "sentence split across lines") }
      """.trimIndent(),
    )
    assertEquals(1, findings.size)
  }

  @Test
  fun `flags if-expression with literal branches`() {
    val findings = rule.compileAndLint(
      """
      fun Text(s: String) {}
      fun screen(b: Boolean) { Text(if (b) "Detecting" else "Re-detecting") }
      """.trimIndent(),
    )
    assertEquals(1, findings.size)
  }

  @Test
  fun `does not flag a contentDescription literal nested in a Text modifier lambda`() {
    val findings = rule.compileAndLint(
      """
      class Mod { fun semantics(block: Mod.() -> Unit): Mod = this; var contentDescription: String = "" }
      fun Text(label: String, modifier: Mod = Mod()) {}
      fun screen(label: String) {
        Text(label, modifier = Mod().semantics { contentDescription = "Main screen" })
      }
      """.trimIndent(),
    )
    assertEquals(0, findings.size)
  }

  @Test
  fun `ignores literals passed to unrelated calls`() {
    val findings = rule.compileAndLint(
      """
      fun log(s: String) {}
      fun screen() { log("debug message") }
      """.trimIndent(),
    )
    assertEquals(0, findings.size)
  }
}
