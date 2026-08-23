package net.streamdek.mobile.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The release notes this has to survive are real files, so the fixture is one.
 *
 * Every assertion here is something the plain [androidx.compose.material3.Text] this replaced got
 * wrong: the hashes, the hyphens, and the emphasis markers all reached the viewer as punctuation.
 */
class MarkdownTextTest {

  private val releaseNote = """
    # StreamDek TV v0.2.4

    ## What's New

    - Trailers are now sourced from Kinocheck
    - **Continue Watching** remembers the source you used

    ## Improvements

    1. The trailer action now reads Trailer unavailable
    2. Sizes show on every result

    > Update from Settings if the prompt does not appear.
  """.trimIndent()

  @Test
  fun `headings lose their hashes and keep their level`() {
    val headings = parseMarkdownBlocks(releaseNote).filterIsInstance<MarkdownBlock.Heading>()
    assertEquals(
      listOf("StreamDek TV v0.2.4" to 1, "What's New" to 2, "Improvements" to 2),
      headings.map { it.text to it.level },
    )
  }

  @Test
  fun `bullets become list items without their hyphens`() {
    val bullets = parseMarkdownBlocks(releaseNote)
      .filterIsInstance<MarkdownBlock.ListItem>()
      .filter { it.marker == "•" }
    assertEquals(
      listOf(
        "Trailers are now sourced from Kinocheck",
        "**Continue Watching** remembers the source you used",
      ),
      bullets.map { it.text },
    )
  }

  @Test
  fun `numbered items keep their own numbers rather than being recounted`() {
    val numbered = parseMarkdownBlocks(releaseNote)
      .filterIsInstance<MarkdownBlock.ListItem>()
      .filter { it.marker != "•" }
    assertEquals(listOf("1.", "2."), numbered.map { it.marker })
  }

  @Test
  fun `a quote is a quote and not a paragraph beginning with a chevron`() {
    val quotes = parseMarkdownBlocks(releaseNote).filterIsInstance<MarkdownBlock.Quote>()
    assertEquals(listOf("Update from Settings if the prompt does not appear."), quotes.map { it.text })
  }

  @Test
  fun `consecutive lines join into one paragraph and a blank line ends it`() {
    val blocks = parseMarkdownBlocks("First line\nsame paragraph\n\nSecond paragraph")
    assertEquals(
      listOf("First line same paragraph", "Second paragraph"),
      blocks.filterIsInstance<MarkdownBlock.Paragraph>().map { it.text },
    )
  }

  @Test
  fun `a horizontal rule is not mistaken for a bullet`() {
    val blocks = parseMarkdownBlocks("---\n- a bullet")
    assertEquals(1, blocks.count { it is MarkdownBlock.Rule })
    assertEquals(1, blocks.count { it is MarkdownBlock.ListItem })
  }

  @Test
  fun `bold text loses its asterisks and gains its weight`() {
    val rendered = renderMarkdownInline("a **bold** word", Color.Red)
    assertEquals("a bold word", rendered.text)
    val bold = rendered.spanStyles.single()
    assertEquals(FontWeight.Black, bold.item.fontWeight)
    assertEquals("bold", rendered.text.substring(bold.start, bold.end))
  }

  @Test
  fun `emphasis nests`() {
    val rendered = renderMarkdownInline("**bold with _italic_ inside**", Color.Red)
    assertEquals("bold with italic inside", rendered.text)
    val italic = rendered.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
    assertEquals("italic", rendered.text.substring(italic.start, italic.end))
  }

  @Test
  fun `underscores inside a word are not emphasis`() {
    val rendered = renderMarkdownInline("run snake_case_name now", Color.Red)
    assertEquals("run snake_case_name now", rendered.text)
    assertTrue(rendered.spanStyles.isEmpty())
  }

  @Test
  fun `a code span keeps what is inside it verbatim`() {
    val rendered = renderMarkdownInline("run `adb install -r *.apk` first", Color.Red)
    assertEquals("run adb install -r *.apk first", rendered.text)
    assertEquals(1, rendered.spanStyles.size)
  }

  @Test
  fun `a link keeps its words and drops its target`() {
    val rendered = renderMarkdownInline("see [the notes](https://streamdek.net/notes)", Color.Red)
    assertEquals("see the notes", rendered.text)
  }

  @Test
  fun `text with no markup is returned untouched`() {
    val plain = "Nothing to see here - just a sentence."
    assertEquals(plain, renderMarkdownInline(plain, Color.Red).text)
  }
}
