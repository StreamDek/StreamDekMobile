package net.streamdek.mobile.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Release notes are written in Markdown, so they are shown as Markdown.
 *
 * Every release note this app has displayed was authored as a `.md` file — headings, bullet lists,
 * the occasional bit of emphasis — and every one of them was rendered by handing the raw file to a
 * single [Text]. What the viewer saw was the punctuation: "## What's New" with the hashes, "- The
 * trailer action now..." with the hyphen, and no separation between one section and the next.
 *
 * This is a deliberately small renderer rather than a Markdown library. Release notes use a
 * handful of constructs and nothing else, and the cost of a dependency that also does tables,
 * footnotes and reference links is a dependency to keep current for the rest of the app's life.
 * Anything not recognised is shown verbatim, which is what the plain [Text] did — so the worst
 * case here is exactly the behaviour it replaces.
 */

/** One rendered block of a release note. */
internal sealed interface MarkdownBlock {
  data class Heading(val text: String, val level: Int) : MarkdownBlock
  data class Paragraph(val text: String) : MarkdownBlock
  /** [marker] is the bullet or the number, already decided, so the renderer does no counting. */
  data class ListItem(val marker: String, val text: String, val depth: Int) : MarkdownBlock
  data class Quote(val text: String) : MarkdownBlock
  /** Verbatim: whitespace is the only thing holding a code sample together. */
  data class Code(val text: String) : MarkdownBlock
  object Rule : MarkdownBlock
}

private val HeadingPattern = Regex("""^(#{1,6})\s+(.*)$""")
private val BulletPattern = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NumberedPattern = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val RulePattern = Regex("""^\s*([-*_])\s*(\1\s*){2,}$""")
private val QuotePattern = Regex("""^\s*>\s?(.*)$""")

/**
 * Splits a note into blocks.
 *
 * Line-based, because Markdown's block level is line-based and release notes never nest deeper
 * than a sub-bullet. Consecutive plain lines join into one paragraph the way Markdown joins them;
 * a blank line ends it.
 */
internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> = buildList {
  val paragraph = StringBuilder()
  var inFence = false

  fun flushParagraph() {
    val text = paragraph.toString().trim()
    paragraph.setLength(0)
    if (text.isNotEmpty()) add(MarkdownBlock.Paragraph(text))
  }

  markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { rawLine ->
    val line = rawLine.trimEnd()
    val trimmed = line.trim()
    if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
      flushParagraph()
      inFence = !inFence
      return@forEach
    }
    if (inFence) {
      add(MarkdownBlock.Code(line))
      return@forEach
    }
    when {
      trimmed.isEmpty() -> flushParagraph()
      RulePattern.matches(line) -> {
        flushParagraph()
        add(MarkdownBlock.Rule)
      }
      HeadingPattern.matches(line) -> {
        flushParagraph()
        val match = HeadingPattern.find(line)!!
        // Trailing hashes are a closing fence in Markdown, not part of the heading.
        val text = match.groupValues[2].trim().trimEnd('#').trim()
        if (text.isNotEmpty()) add(MarkdownBlock.Heading(text, match.groupValues[1].length))
      }
      BulletPattern.matches(line) -> {
        flushParagraph()
        val match = BulletPattern.find(line)!!
        add(MarkdownBlock.ListItem("•", match.groupValues[2].trim(), indentDepth(match.groupValues[1])))
      }
      NumberedPattern.matches(line) -> {
        flushParagraph()
        val match = NumberedPattern.find(line)!!
        add(MarkdownBlock.ListItem("${match.groupValues[2]}.", match.groupValues[3].trim(), indentDepth(match.groupValues[1])))
      }
      QuotePattern.matches(line) -> {
        flushParagraph()
        add(MarkdownBlock.Quote(QuotePattern.find(line)!!.groupValues[1].trim()))
      }
      else -> {
        if (paragraph.isNotEmpty()) paragraph.append(' ')
        paragraph.append(trimmed)
      }
    }
  }
  flushParagraph()
}

/** Two spaces or one tab is a level. Anything shallower than that is not an indent. */
private fun indentDepth(indent: String): Int =
  (indent.replace("\t", "  ").length / 2).coerceIn(0, 3)

/**
 * Every inline construct in one alternation, matched left to right.
 *
 * One pattern rather than a pass per construct so the leftmost marker in the text always wins,
 * which is what stops a stray asterisk inside a code span from being read as emphasis. The order
 * of the branches settles the ties: code first so `a_b_c` in backticks keeps its underscores, and
 * `**` before `*` so bold is never read as two italics.
 *
 * Groups, in order: 1 link text, 2 link target, 3 code, 4 bold marker, 5 bold, 6 strikethrough,
 * 7 italic marker, 8 italic.
 */
private val InlinePattern = Regex(
  """!?\[([^\]]*)]\(([^)\s]*)(?:\s+"[^"]*")?\)""" +
    """|`([^`]+)`""" +
    """|(\*\*|__)(\S(?:[\s\S]*?\S)?)\4""" +
    """|~~(\S(?:[\s\S]*?\S)?)~~""" +
    """|(?<![*\w])([*_])(\S(?:[^*_]*?\S)?)\7(?![*\w])""",
)

/**
 * Inline emphasis, as styling rather than as punctuation.
 *
 * Recursive so emphasis nests — `**bold with _italic_ inside**` styles both — with a depth cap
 * because a note is not worth a stack overflow.
 *
 * Links keep their text and lose their target. A television has no browser to hand one to, and on
 * a phone an update note is read in a modal the viewer cannot leave, so a bare URL beside the
 * words would be noise in both places.
 */
internal fun renderMarkdownInline(source: String, codeColor: Color): AnnotatedString =
  buildAnnotatedString { appendMarkdownInline(source, codeColor, depth = 0) }

private fun AnnotatedString.Builder.appendMarkdownInline(source: String, codeColor: Color, depth: Int) {
  if (depth > 4 || source.isEmpty()) {
    append(source)
    return
  }
  var cursor = 0
  while (cursor < source.length) {
    val match = InlinePattern.find(source, cursor)
    if (match == null) {
      append(source.substring(cursor))
      return
    }
    append(source.substring(cursor, match.range.first))
    val groups = match.groupValues
    when {
      match.groups[3] != null -> withMarkdownStyle(
        SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor),
      ) { append(groups[3]) }
      match.groups[5] != null -> withMarkdownStyle(SpanStyle(fontWeight = FontWeight.Black)) {
        appendMarkdownInline(groups[5], codeColor, depth + 1)
      }
      match.groups[6] != null -> withMarkdownStyle(
        SpanStyle(textDecoration = TextDecoration.LineThrough),
      ) { appendMarkdownInline(groups[6], codeColor, depth + 1) }
      match.groups[8] != null -> withMarkdownStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
        appendMarkdownInline(groups[8], codeColor, depth + 1)
      }
      // A link, or an image whose source cannot be fetched here either way. Its text is the part
      // worth keeping; an empty one leaves nothing to show, so the target stands in.
      else -> withMarkdownStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
        appendMarkdownInline(groups[1].ifBlank { groups[2] }, codeColor, depth + 1)
      }
    }
    // A zero-width match would never advance; the patterns cannot produce one, but the loop is
    // guarded anyway rather than relying on that staying true.
    cursor = maxOf(match.range.last + 1, match.range.first + 1)
  }
}

private inline fun AnnotatedString.Builder.withMarkdownStyle(style: SpanStyle, body: () -> Unit) {
  val marker = pushStyle(style)
  body()
  pop(marker)
}

/**
 * Release notes, rendered.
 *
 * [color] is the body colour; headings take it at full strength and body text at [bodyAlpha], so
 * one call sits correctly in both the update dialog and the settings card without either having to
 * pass a palette.
 */
@Composable
fun MarkdownText(
  markdown: String,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.onSurface,
  bodyAlpha: Float = 0.82f,
) {
  val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
  val accent = MaterialTheme.colorScheme.primary
  val bodyColor = color.copy(alpha = bodyAlpha)
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    blocks.forEach { block ->
      when (block) {
        is MarkdownBlock.Heading -> Text(
          renderMarkdownInline(block.text, accent),
          color = color,
          style = when (block.level) {
            1 -> MaterialTheme.typography.titleMedium
            2 -> MaterialTheme.typography.titleSmall
            else -> MaterialTheme.typography.labelLarge
          },
          fontWeight = FontWeight.Black,
          modifier = Modifier.padding(top = 4.dp),
        )
        is MarkdownBlock.Paragraph -> Text(
          renderMarkdownInline(block.text, accent),
          color = bodyColor,
          style = MaterialTheme.typography.bodyMedium,
        )
        is MarkdownBlock.ListItem -> Row(
          modifier = Modifier.fillMaxWidth().padding(start = (block.depth * 14).dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Text(
            block.marker,
            color = accent,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Black,
          )
          Text(
            renderMarkdownInline(block.text, accent),
            color = bodyColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
          )
        }
        is MarkdownBlock.Quote -> Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(Modifier.width(3.dp).height(18.dp).background(accent.copy(alpha = 0.55f)))
          Text(
            renderMarkdownInline(block.text, accent),
            color = bodyColor,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.weight(1f),
          )
        }
        is MarkdownBlock.Code -> Text(
          block.text,
          color = bodyColor,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
        )
        MarkdownBlock.Rule -> Box(
          Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(color.copy(alpha = 0.18f)),
        )
      }
    }
  }
}
