package dev.davidv.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

class NoHardcodedUiString(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = "NoHardcodedUiString",
    severity = Severity.Maintainability,
    description = "User-facing string literals must come from string resources " +
      "(stringResource / getString) so they can be translated.",
    debt = Debt.TEN_MINS,
  )

  private val sinks: List<String> by config(DEFAULT_SINKS)

  override fun visitCallExpression(expression: KtCallExpression) {
    super.visitCallExpression(expression)
    val callee = expression.calleeExpression?.text ?: return
    if (callee !in sinks) return
    if (expression.isInsidePreview()) return

    for (arg in expression.valueArguments) {
      val argExpr = arg.getArgumentExpression() ?: continue
      if (!argExpr.containsTranslatableLiteral()) continue
      report(
        CodeSmell(
          issue,
          Entity.from(argExpr),
          "Hardcoded UI string passed to $callee(...); move it to a string resource.",
        ),
      )
    }
  }

  // Recurses only through string concatenation, parentheses and if/when branches —
  // not into lambdas or nested calls, so a contentDescription buried in a modifier
  // lambda of a Text(...) call is not flagged.
  private fun KtExpression.containsTranslatableLiteral(): Boolean =
    when (this) {
      is KtStringTemplateExpression ->
        entries.any { it is KtLiteralStringTemplateEntry && it.text.any(Char::isLetter) }
      is KtParenthesizedExpression -> expression?.containsTranslatableLiteral() ?: false
      is KtBinaryExpression ->
        (left?.containsTranslatableLiteral() ?: false) || (right?.containsTranslatableLiteral() ?: false)
      is KtIfExpression ->
        (then?.containsTranslatableLiteral() ?: false) || (`else`?.containsTranslatableLiteral() ?: false)
      is KtWhenExpression -> entries.any { it.expression?.containsTranslatableLiteral() ?: false }
      else -> false
    }

  private fun KtCallExpression.isInsidePreview(): Boolean =
    parents.filterIsInstance<KtNamedFunction>().any { fn ->
      fn.annotationEntries.any { it.shortName?.asString() == "Preview" }
    }

  companion object {
    private val DEFAULT_SINKS = listOf(
      "Text",
      "makeText",
      "setContentTitle",
      "setContentText",
      "setDescription",
      "showStatus",
    )
  }
}
