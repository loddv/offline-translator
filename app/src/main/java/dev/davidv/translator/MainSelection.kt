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
  private val languages = availability.allLanguages()

  private fun installed(language: Language): Boolean = availability.availabilityFor(language)?.translatorFiles == true

  fun initial(): MainSelection {
    if (!availability.hasLanguages) return MainSelection.NoLanguages
    val defaultTarget = languages.firstOrNull { it.code == settings.defaultTargetLanguageCode && installed(it) }
    // With only the en→X direction installed, X has no source at all, so the configured target
    // can be unusable; any installed language with a source beats leaving the screen without a pair.
    val candidates = listOfNotNull(defaultTarget) + languages.filter { it.isEnglish } + languages.filter(::installed)
    val selection =
      candidates.firstNotNullOfOrNull { target ->
        sourceFor(target)?.let { MainSelection.Ready(it, TargetTabs.of(target)) }
      }
    return checkNotNull(selection) { "languages are installed but no installed pair translates" }
  }

  fun reconciled(current: MainSelection): MainSelection {
    if (current !is MainSelection.Ready || !availability.hasLanguages) return initial()
    val source = current.source.takeIf(::installed) ?: sourceFor(current.target)
    val targets = source?.let { reachableTabs(current.targets, it) }
    return if (source != null && targets != null) MainSelection.Ready(source, targets) else initial()
  }

  // A source can never also be a target tab (it would translate to itself), so it leaves the
  // tabs, and a sole tab is replaced by an alternate target.
  fun normalized(
    source: Language,
    targets: TargetTabs,
  ): MainSelection.Ready =
    when {
      source !in targets.tabs -> MainSelection.Ready(source, targets)
      targets.tabs.size > 1 -> MainSelection.Ready(source, targets.remove(source))
      // A source with only its en→X direction installed translates to nothing else; the pair is
      // left as picked rather than swapping in a target that can't be reached.
      else -> MainSelection.Ready(source, alternateTarget(source)?.let(TargetTabs::of) ?: targets)
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
    val preferred = languages.firstOrNull { it.code == settings.defaultSourceLanguageCode }
    return (listOfNotNull(preferred) + languages).firstOrNull { it != target && canTranslate(it, target) }
  }

  private fun alternateTarget(source: Language): Language? {
    val candidates = languages.filter { it != source && canTranslate(source, it) }
    return candidates.firstOrNull { it.code == settings.defaultTargetLanguageCode }
      ?: candidates.firstOrNull { metadata[it]?.favorite == true }
      ?: candidates.minByOrNull { it.displayName }
  }
}
