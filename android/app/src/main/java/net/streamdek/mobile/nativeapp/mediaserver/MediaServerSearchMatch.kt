package net.streamdek.mobile.nativeapp.mediaserver

import java.text.Normalizer
import java.util.Locale

/*
 * Whether a media server's search result is actually about what was searched for.
 *
 * Servers answer a search with more than title matches. Plex's search returns hubs of titles
 * linked to the query some other way - through an actor, a director, a genre, a collection - and,
 * when nothing in the library is called what was typed, whatever it considers related. Shown in a
 * section headed by the server's name, those read as matches: searching "Euphoria" listed Tracker,
 * Supergirl and Hoppers. So a result is kept only when one of its titles answers the query.
 *
 * The comparison ignores case, accents and punctuation ("amelie" finds "Amélie", "spiderman" finds
 * "Spider-Man"), wants every word of the query in the title in any order, and forgives one slip in
 * a longer word ("euphria" still finds "Euphoria"), which is about as far as a server's own fuzzy
 * matching reaches that is worth keeping.
 */

/** True when any of [titles] answers [query]. A blank query matches everything. */
internal fun titleMatchesSearch(query: String, vararg titles: String?): Boolean {
    val wanted = searchWords(query)
    if (wanted.isEmpty()) return true
    val compactQuery = wanted.joinToString("")
    return titles.any { title ->
        if (title.isNullOrBlank()) return@any false
        val words = searchWords(title)
        words.joinToString("").contains(compactQuery) ||
            wanted.all { word -> words.any { candidate -> wordMatches(word, candidate) } }
    }
}

private fun wordMatches(wanted: String, candidate: String): Boolean =
    candidate.contains(wanted) ||
        (wanted.length >= 5 && withinOneEdit(wanted, candidate)) ||
        // A longer title word typed with one slip, compared against its start: "euphria" in "euphorias".
        (wanted.length >= 5 && candidate.length > wanted.length && withinOneEdit(wanted, candidate.take(wanted.length + 1)))

/** Lower-case words with accents and punctuation removed. */
internal fun searchWords(text: String): List<String> =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
        .split(NON_WORD)
        .filter { it.isNotEmpty() }

private val DIACRITICS = Regex("\\p{M}+")
private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/** One insertion, deletion or substitution apart, or the same. */
internal fun withinOneEdit(a: String, b: String): Boolean {
    if (a == b) return true
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    val (short, long) = if (a.length <= b.length) a to b else b to a
    var i = 0
    var j = 0
    var edits = 0
    while (i < short.length && j < long.length) {
        if (short[i] == long[j]) {
            i++; j++
            continue
        }
        if (++edits > 1) return false
        if (short.length == long.length) i++
        j++
    }
    return edits + (long.length - j) + (short.length - i) <= 1
}
