// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.model

import java.util.Locale

/**
 * Whether every word of [query] appears in one of [texts], ignoring case and letter accents
 * ("ё" finds "е", "café" finds "cafe"). A blank query matches nothing.
 */
fun matchesQuery(
    query: String,
    vararg texts: String?,
): Boolean {
    val words = query.normalizedForSearch().split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return false
    val haystack = texts.filterNotNull().joinToString(" ") { it.normalizedForSearch() }
    return words.all { it in haystack }
}

private val Accents = Regex("\\p{Mn}+")

private fun String.normalizedForSearch(): String =
    java.text.Normalizer
        .normalize(lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Accents, "")
        .replace('ё', 'е')
        .trim()
