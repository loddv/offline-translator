/*
 * Copyright (C) 2024 David V
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.davidv.translator

sealed interface MainSelection {
  // The catalog always carries English, but until another language is installed there is no
  // pair to translate.
  data object NoLanguages : MainSelection

  data class Ready(
    val source: Language,
    val targets: TargetTabs,
  ) : MainSelection {
    val target: Language get() = targets.active
  }
}

class MainSelectionRules(
  private val availability: LanguageAvailabilityState,
  private val settings: AppSettings,
  private val metadata: Map<Language, LanguageMetadata>,
  private val canTranslate: (from: Language, to: Language) -> Boolean,
) {
  private val sources = availability.sourceLanguages()

  fun initial(): MainSelection {
    if (!availability.hasLanguages) return MainSelection.NoLanguages
    val targets = availability.targetLanguages()
    val defaultTarget = targets.firstOrNull { it.code == settings.defaultTargetLanguageCode }
    val candidates = listOfNotNull(defaultTarget) + targets.filter { it.isEnglish } + targets
    val selection =
      candidates.firstNotNullOfOrNull { target ->
        sourceFor(target)?.let { MainSelection.Ready(it, TargetTabs.of(target)) }
      }
    return checkNotNull(selection) { "languages are installed but no installed pair translates" }
  }

  fun reconciled(current: MainSelection): MainSelection {
    if (current !is MainSelection.Ready || !availability.hasLanguages) return initial()
    val source = current.source.takeIf(availability::canBeSource) ?: sourceFor(current.target)
    val targets = source?.let { reachableTabs(current.targets, it) }
    return if (source != null && targets != null) MainSelection.Ready(source, targets) else initial()
  }

  // A source can never also be a target tab (it would translate to itself) and no tab may name
  // a language the source can't reach, so those leave the tabs, and an emptied tab row gets an
  // alternate target. Callers only pass sources the pickers offered, which always reach one.
  fun normalized(
    source: Language,
    targets: TargetTabs,
  ): MainSelection.Ready {
    val reachable = reachableTabs(targets, source)
    checkNotNull(reachable) { "${source.code} was picked as a source but translates to no installed language" }
    return MainSelection.Ready(source, reachable)
  }

  fun withSource(
    current: MainSelection.Ready,
    source: Language,
    change: FromLangChange,
  ): MainSelection.Ready {
    val carriedTarget =
      current.source.takeIf {
        change == FromLangChange.MovePreviousToTarget && it != source && canTranslate(source, it)
      }
    return normalized(source, carriedTarget?.let(current.targets::select) ?: current.targets)
  }

  // The button's state can lag a catalog publish, so a swap that no longer translates is dropped.
  fun swapped(current: MainSelection.Ready): MainSelection.Ready? {
    if (!canTranslate(current.target, current.source)) return null
    return normalized(current.target, current.targets.select(current.source))
  }

  private fun reachableTabs(
    targets: TargetTabs,
    source: Language,
  ): TargetTabs? {
    val kept = targets.tabs.filter { it != source && canTranslate(source, it) }
    if (kept.isEmpty()) return alternateTarget(source)?.let(TargetTabs::of)
    return (targets.tabs - kept.toSet()).fold(targets, TargetTabs::remove)
  }

  private fun sourceFor(target: Language): Language? {
    val preferred = sources.firstOrNull { it.code == settings.defaultSourceLanguageCode }
    return (listOfNotNull(preferred) + sources).firstOrNull { it != target && canTranslate(it, target) }
  }

  private fun alternateTarget(source: Language): Language? {
    val candidates = availability.targetLanguages().filter { it != source && canTranslate(source, it) }
    return candidates.firstOrNull { it.code == settings.defaultTargetLanguageCode }
      ?: candidates.firstOrNull { metadata[it]?.favorite == true }
      ?: candidates.minByOrNull { it.displayName }
  }
}
