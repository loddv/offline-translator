package dev.davidv.translator.overlayChrome

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.davidv.translator.Language
import dev.davidv.translator.R

data class LanguageToolbarViews(
  val root: View,
  val sourceLabel: TextView,
  val targetLabel: TextView,
  val ocrButton: View? = null,
  val ocrIcon: ImageView? = null,
)

object OverlayChromeFactory {
  private const val ACTIVE_OCR_BLUE = "#4488FF"

  fun createLanguageToolbar(
    context: Context,
    dpToPx: (Int) -> Int,
    forcedSourceLanguage: Language?,
    forcedTargetLanguage: Language?,
    defaultTargetLanguage: Language,
    onClose: () -> Unit,
    onSourceClick: () -> Unit,
    onSwap: () -> Unit,
    onTargetClick: () -> Unit,
    showOcrButton: Boolean = false,
    onOcrClick: (() -> Unit)? = null,
    onMenuClick: () -> Unit,
  ): LanguageToolbarViews {
    val toolbar = FrameLayout(context)
    val pad = dpToPx(6)
    toolbar.setPadding(pad, pad, pad, pad)

    val btnSize = dpToPx(32)
    val iconPad = dpToPx(6)
    val closeBtn = ImageView(context)
    closeBtn.setImageResource(R.drawable.cancel)
    closeBtn.setPadding(iconPad, iconPad, iconPad, iconPad)
    closeBtn.setOnClickListener { onClose() }
    val closePill = makePill(context, dpToPx, closeBtn)
    toolbar.addView(
      closePill,
      FrameLayout.LayoutParams(btnSize, btnSize).apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
      },
    )

    val langRow = LinearLayout(context)
    langRow.orientation = LinearLayout.HORIZONTAL
    langRow.gravity = Gravity.CENTER_VERTICAL
    langRow.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))

    val sourceLabel = TextView(context)
    sourceLabel.text = forcedSourceLanguage?.shortDisplayName ?: "Auto"
    sourceLabel.setTextColor(Color.WHITE)
    sourceLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    sourceLabel.gravity = Gravity.CENTER
    sourceLabel.maxLines = 1
    sourceLabel.ellipsize = TextUtils.TruncateAt.END
    sourceLabel.setOnClickListener { onSourceClick() }
    langRow.addView(sourceLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

    val swapBtn = ImageView(context)
    swapBtn.setImageResource(R.drawable.compare)
    swapBtn.setColorFilter(Color.WHITE)
    val swapSize = dpToPx(20)
    val swapPad = dpToPx(6)
    swapBtn.setPadding(swapPad, 0, swapPad, 0)
    swapBtn.setOnClickListener { onSwap() }
    langRow.addView(swapBtn, LinearLayout.LayoutParams(swapSize + swapPad * 2, swapSize))

    val currentTarget = forcedTargetLanguage ?: defaultTargetLanguage
    val targetLabel = TextView(context)
    targetLabel.text = currentTarget.shortDisplayName
    targetLabel.setTextColor(Color.WHITE)
    targetLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    targetLabel.gravity = Gravity.CENTER
    targetLabel.maxLines = 1
    targetLabel.ellipsize = TextUtils.TruncateAt.END
    targetLabel.setOnClickListener { onTargetClick() }
    langRow.addView(targetLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

    val langPill = makePill(context, dpToPx, langRow)
    toolbar.addView(
      langPill,
      FrameLayout.LayoutParams(dpToPx(200), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
        gravity = Gravity.CENTER
      },
    )

    val rightActions =
      LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
      }

    var ocrIcon: ImageView? = null
    val ocrPill =
      onOcrClick?.let {
        val ocrBtn =
          ImageView(context).apply {
            setImageResource(R.drawable.activity_zone)
            setColorFilter(Color.WHITE)
            setPadding(iconPad, iconPad, iconPad, iconPad)
            setOnClickListener { onOcrClick() }
          }
        ocrIcon = ocrBtn
        makePill(context, dpToPx, ocrBtn).also { pill ->
          pill.visibility = if (showOcrButton) View.VISIBLE else View.GONE
          rightActions.addView(
            pill,
            LinearLayout.LayoutParams(btnSize, btnSize).apply {
              marginEnd = dpToPx(6)
            },
          )
        }
      }

    val menuBtn = ImageView(context)
    menuBtn.setImageResource(R.drawable.more_vert)
    menuBtn.setPadding(iconPad, iconPad, iconPad, iconPad)
    menuBtn.setOnClickListener { onMenuClick() }
    val menuPill = makePill(context, dpToPx, menuBtn)
    rightActions.addView(menuPill, LinearLayout.LayoutParams(btnSize, btnSize))
    toolbar.addView(
      rightActions,
      FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
      },
    )

    return LanguageToolbarViews(toolbar, sourceLabel, targetLabel, ocrPill, ocrIcon)
  }

  fun setOcrButtonActive(
    ocrButton: View?,
    ocrIcon: ImageView?,
    active: Boolean,
  ) {
    ocrIcon?.setColorFilter(Color.parseColor(if (active) ACTIVE_OCR_BLUE else "#FFFFFF"))
    if (ocrButton != null && !active) {
      ocrButton.alpha = 1f
    }
  }

  fun createLanguagePicker(
    context: Context,
    dpToPx: (Int) -> Int,
    isSource: Boolean,
    availableLangs: List<Language>,
    onPick: (Language?) -> Unit,
  ): ScrollView {
    val scroll = ScrollView(context)
    val list = LinearLayout(context)
    list.orientation = LinearLayout.VERTICAL
    val bg = GradientDrawable()
    bg.setColor(Color.parseColor("#E0303030"))
    bg.cornerRadius = dpToPx(12).toFloat()
    list.background = bg
    val listPad = dpToPx(8)
    list.setPadding(listPad, listPad, listPad, listPad)

    if (isSource) {
      addMenuItem(context, list, dpToPx, "\u2728  Auto-detect") {
        onPick(null)
      }
    }

    for (lang in availableLangs) {
      addMenuItem(context, list, dpToPx, lang.displayName) {
        onPick(lang)
      }
    }

    scroll.addView(list)
    return scroll
  }

  fun createMenuContainer(
    context: Context,
    dpToPx: (Int) -> Int,
  ): LinearLayout {
    val container = LinearLayout(context)
    container.orientation = LinearLayout.VERTICAL
    val bg = GradientDrawable()
    bg.setColor(Color.parseColor("#E0303030"))
    bg.cornerRadius = dpToPx(12).toFloat()
    container.background = bg
    val pad = dpToPx(8)
    container.setPadding(pad, pad, pad, pad)
    return container
  }

  fun makePill(
    context: Context,
    dpToPx: (Int) -> Int,
    view: View,
  ): FrameLayout {
    val pill = FrameLayout(context)
    val bg = GradientDrawable()
    bg.setColor(Color.parseColor("#CC303030"))
    bg.cornerRadius = dpToPx(20).toFloat()
    pill.background = bg
    pill.addView(view)
    return pill
  }

  fun addMenuItem(
    context: Context,
    parent: LinearLayout,
    dpToPx: (Int) -> Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
  ) {
    val item = TextView(context)
    item.text = label
    item.setTextColor(if (enabled) Color.WHITE else Color.parseColor("#60FFFFFF"))
    item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    val pad = dpToPx(12)
    item.setPadding(pad, pad, pad, pad)
    item.setOnClickListener { onClick() }
    parent.addView(item)
  }
}
