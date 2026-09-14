package net.streamdek.mobile.nativeapp

import android.graphics.Typeface
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.AbsSeekBar
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView

/**
 * A CloudStream plugin's settings screen, read into parts StreamDek can draw itself.
 *
 * Extensions build their settings from ordinary Android views with styling of their own choosing,
 * which sits badly beside the rest of the app. Rather than restyle views it does not own, StreamDek
 * reads what the screen offers - text, switches, text fields and buttons - and draws those in its own
 * design, driving the plugin's real views underneath so the plugin's own code still does the work.
 *
 * A screen built from anything that cannot be read that way (a list backed by an adapter, a slider,
 * a web page) is left to the plugin to show; [read] answers null for it.
 */
internal object PluginSettingsMirror {
  private const val TAG = "PluginSettingsMirror"

  sealed interface Part {
    /** Stable for as long as the plugin keeps the same view, so lists keep their place. */
    val key: Int

    data class Text(override val key: Int, val text: String, val emphasis: Emphasis) : Part
    data class Toggle(override val key: Int, val label: String, val description: String?, val checked: Boolean, val enabled: Boolean, val view: CompoundButton) : Part
    data class Input(override val key: Int, val label: String, val value: String, val password: Boolean, val view: EditText) : Part
    data class Action(override val key: Int, val label: String, val commits: Boolean, val enabled: Boolean, val view: View) : Part
  }

  enum class Emphasis { Title, Heading, Body, Caption }

  /**
   * What a Save-style control is called. Several plugins only write their choices when one is
   * pressed, so StreamDek presses it for the viewer when the popup closes instead of showing it.
   */
  private val commitLabel = Regex("""\b(save|apply|done|ok|confirm|update)\b""", RegexOption.IGNORE_CASE)

  fun isCommitLabel(label: String?): Boolean = !label.isNullOrBlank() && commitLabel.containsMatchIn(label)

  /**
   * [parts] with each run of switches in alphabetical order, the way StreamDek lists sources.
   * Only runs are sorted, so a heading or button stays above or below the switches it belonged to.
   */
  fun alphabetised(parts: List<Part>): List<Part> {
    val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
    // Emoji and punctuation lead many plugin labels ("📺 Sports"); they should not decide the order.
    fun sortKey(toggle: Part.Toggle) = toggle.label.dropWhile { !it.isLetterOrDigit() }.ifEmpty { toggle.label }
    val result = ArrayList<Part>(parts.size)
    val run = ArrayList<Part.Toggle>()
    fun flush() {
      run.sortWith { a, b -> collator.compare(sortKey(a), sortKey(b)) }
      result += run
      run.clear()
    }
    parts.forEach { part -> if (part is Part.Toggle) run += part else { flush(); result += part } }
    flush()
    return result
  }

  fun read(root: View): List<Part>? = runCatching {
    val parts = mutableListOf<Part>()
    // A switch drawn without text is labelled by the text beside it; those texts belong to it.
    val labels = HashMap<CompoundButton, List<TextView>>()
    collectLabels(root, labels)
    val consumed = labels.values.flatten().toMutableSet()
    if (!walk(root, root, labels, consumed, parts)) return@runCatching null
    parts.toList()
  }.onFailure { Log.w(TAG, "Could not read plugin settings", it) }.getOrNull()

  private fun collectLabels(view: View, labels: MutableMap<CompoundButton, List<TextView>>) {
    if (view.visibility != View.VISIBLE) return
    if (view is CompoundButton && view.text.isNullOrBlank()) {
      val parent = view.parent as? ViewGroup
      val siblings = parent?.let { group -> (0 until group.childCount).map(group::getChildAt) }.orEmpty()
      labels[view] = siblings.filterIsInstance<TextView>()
        .filter { it !is CompoundButton && it !is EditText && it !is android.widget.Button && it.visibility == View.VISIBLE && !it.text.isNullOrBlank() }
        .take(2)
    }
    if (view is ViewGroup) for (index in 0 until view.childCount) collectLabels(view.getChildAt(index), labels)
  }

  /** False when the screen holds something that cannot be drawn faithfully. */
  private fun walk(
    root: View,
    view: View,
    labels: Map<CompoundButton, List<TextView>>,
    consumed: Set<TextView>,
    parts: MutableList<Part>,
  ): Boolean {
    if (view.visibility != View.VISIBLE) return true
    val key = System.identityHashCode(view)
    when {
      view is WebView || view is AdapterView<*> || view is AbsSeekBar || isUnreadableContainer(view) -> return false
      view is CompoundButton -> {
        val own = view.text?.toString()?.trim().orEmpty()
        val beside = labels[view].orEmpty().map { it.text.toString().trim() }
        val label = own.ifBlank { beside.firstOrNull().orEmpty() }.ifBlank { view.contentDescription?.toString().orEmpty() }
        val description = if (own.isNotBlank()) beside.firstOrNull() else beside.getOrNull(1)
        parts += Part.Toggle(key, label, description, view.isChecked, view.isEnabled, view)
      }
      view is EditText -> {
        val password = (view.inputType and InputType.TYPE_MASK_VARIATION) in setOf(
          InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )
        val label = view.hint?.toString().orEmpty().ifBlank { view.contentDescription?.toString().orEmpty() }
        parts += Part.Input(key, label, view.text?.toString().orEmpty(), password, view)
      }
      view is TextView && view in consumed -> Unit
      view is android.widget.Button || (view is TextView && view.hasOnClickListeners()) -> {
        val label = (view as TextView).text?.toString()?.trim().orEmpty().ifBlank { view.contentDescription?.toString().orEmpty() }
        if (label.isNotBlank()) parts += Part.Action(key, label, isCommitLabel(label), view.isEnabled, view)
      }
      view is TextView -> {
        val text = view.text?.toString()?.trim().orEmpty()
        if (text.isNotBlank()) parts += Part.Text(key, text, emphasis(view, isFirstText = parts.none { it is Part.Text }))
      }
      view is ImageView && view.hasOnClickListeners() -> {
        val label = view.contentDescription?.toString().orEmpty()
        if (label.isNotBlank()) parts += Part.Action(key, label, isCommitLabel(label), view.isEnabled, view)
      }
      view is ViewGroup && view !== root && view.hasOnClickListeners() && !hasControls(view) -> {
        // A tappable row made of text, such as "Open login page".
        val label = texts(view).joinToString(" - ")
        if (label.isNotBlank()) parts += Part.Action(key, label, isCommitLabel(label), view.isEnabled, view)
      }
      view is ViewGroup -> for (index in 0 until view.childCount) {
        if (!walk(root, view.getChildAt(index), labels, consumed, parts)) return false
      }
    }
    return true
  }

  /** Containers that recycle or page their children, so what is attached is not all there is. */
  private fun isUnreadableContainer(view: View): Boolean {
    var type: Class<*>? = view.javaClass
    while (type != null && type != View::class.java) {
      if (type.name in unreadableContainers) return true
      type = type.superclass
    }
    return false
  }

  private val unreadableContainers = setOf(
    "androidx.recyclerview.widget.RecyclerView",
    "androidx.viewpager.widget.ViewPager",
    "androidx.viewpager2.widget.ViewPager2",
  )

  private fun hasControls(group: ViewGroup): Boolean = (0 until group.childCount).any { index ->
    when (val child = group.getChildAt(index)) {
      is CompoundButton, is EditText, is android.widget.Button -> true
      is ViewGroup -> hasControls(child)
      else -> child.hasOnClickListeners()
    }
  }

  private fun texts(group: ViewGroup): List<String> = (0 until group.childCount).flatMap { index ->
    when (val child = group.getChildAt(index)) {
      is TextView -> listOfNotNull(child.text?.toString()?.trim()?.takeIf { it.isNotBlank() && child.visibility == View.VISIBLE })
      is ViewGroup -> texts(child)
      else -> emptyList()
    }
  }

  private fun emphasis(view: TextView, isFirstText: Boolean): Emphasis {
    val sp = view.textSize / TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, view.resources.displayMetrics)
    val bold = view.typeface?.let { it.isBold || it.style and Typeface.BOLD != 0 } == true
    return when {
      sp >= 20f || (isFirstText && bold && sp >= 17f) -> Emphasis.Title
      sp >= 17f || bold -> Emphasis.Heading
      sp < 13f -> Emphasis.Caption
      else -> Emphasis.Body
    }
  }
}
