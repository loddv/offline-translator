package dev.davidv.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class TranslatorRuleSetProvider : RuleSetProvider {
  override val ruleSetId: String = "translator-rules"

  override fun instance(config: Config): RuleSet =
    RuleSet(
      ruleSetId,
      listOf(
        NoHardcodedUiString(config),
      ),
    )
}
