package com.example.calling

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Intelligent, multilingual offline contact matcher for Tamil, English, and Hindi.
 */
object ContactMatcher {

    data class MatchResult(
        val contact: ContactItem,
        val confidence: Float,
        val matchedAlias: String
    )

    fun findMatches(query: String, contacts: List<ContactItem>): List<MatchResult> {
        val queryVariants = getQueryVariants(query)
        if (queryVariants.isEmpty() || contacts.isEmpty()) return emptyList()

        val results = mutableListOf<MatchResult>()

        for (contact in contacts) {
            val contactNameNorm = normalizeName(contact.displayName)
            val tokens = contact.displayName
                .lowercase(Locale.ROOT)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotBlank() }
                .map { normalizeName(it) }

            val aliases = (getSynonymAliases(contactNameNorm) + tokens + contactNameNorm).toSet()

            var bestScore = 0.0f
            var bestAlias = contact.displayName

            for (cleanQuery in queryVariants) {
                for (alias in aliases) {
                    val score = computeSimilarity(cleanQuery, alias)
                    if (score > bestScore) {
                        bestScore = score
                        bestAlias = alias
                    }
                }
            }

            // High precision threshold: only accept matches with confidence >= 0.65
            if (bestScore >= 0.65f) {
                results.add(
                    MatchResult(
                        contact = contact.copy(matchScore = bestScore),
                        confidence = bestScore,
                        matchedAlias = bestAlias
                    )
                )
            }
        }

        return results.sortedByDescending { it.confidence }
    }

    private fun getQueryVariants(query: String): List<String> {
        val norm = normalizeName(query)
        if (norm.isBlank()) return emptyList()

        val variants = mutableListOf(norm)
        // If query ended in Tamil 'ு' after dative removal, add candidate with virama '்'
        if (norm.endsWith('\u0BC1')) {
            variants.add(norm.dropLast(1) + '\u0BCD')
        }
        return variants
    }

    /**
     * Strips Tamil dative suffixes (-க்கு, -வுக்கு, -உக்கு), Hindi markers (को), and punctuation.
     */
    fun normalizeName(raw: String): String {
        var text = raw.lowercase(Locale.ROOT).trim()

        // Remove wake words & command tokens if present
        text = text
            .replace("hi kitty", "")
            .replace("kitty", "")
            .replace("call ", "")
            .replace("phone ", "")
            .replace("dial ", "")
            .replace("கால் செய்", "")
            .replace("கால் பண்ணு", "")
            .replace("போன் போடு", "")
            .replace("அழைக்கவும்", "")
            .replace("कॉल करो", "")
            .replace("फोन लगाओ", "")
            .replace("फोन करो", "")
            .trim()

        // Strip Tamil grammatical suffixes
        if (text.endsWith("வுக்கு")) text = text.removeSuffix("வுக்கு")
        else if (text.endsWith("உக்கு")) text = text.removeSuffix("உக்கு")
        else if (text.endsWith("க்கு")) text = text.removeSuffix("க்கு")
        else if (text.endsWith("ிடம்")) text = text.removeSuffix("ிடம்")

        // Strip Hindi postposition "को"
        if (text.endsWith(" को")) text = text.removeSuffix(" को").trim()
        else if (text.endsWith("को")) text = text.removeSuffix("को").trim()

        // Remove extra punctuation
        return text.replace(Regex("[.,:;!?'\"()\\[\\]\\-_/]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun getSynonymAliases(normalizedName: String): Set<String> {
        val set = mutableSetOf<String>()

        when {
            // Mother synonyms
            normalizedName.contains("amma") || normalizedName.contains("அம்மா") || normalizedName.contains("mom") || normalizedName.contains("mother") || normalizedName.contains("माँ") || normalizedName.contains("माताजी") -> {
                set.addAll(listOf("amma", "mom", "mother", "mummy", "அம்மா", "माँ", "माताजी", "மதர்"))
            }

            // Father synonyms
            normalizedName.contains("appa") || normalizedName.contains("அப்பா") || normalizedName.contains("dad") || normalizedName.contains("father") || normalizedName.contains("पापा") || normalizedName.contains("पिताजी") -> {
                set.addAll(listOf("appa", "dad", "father", "daddy", "அப்பா", "पापा", "पिताजी", "ஃபாதர்"))
            }

            // Brother synonyms
            normalizedName.contains("brother") || normalizedName.contains("அண்ணன்") || normalizedName.contains("தம்பி") || normalizedName.contains("anna") || normalizedName.contains("thambi") || normalizedName.contains("भाई") || normalizedName.contains("भैया") -> {
                set.addAll(listOf("brother", "bro", "anna", "thambi", "அண்ணன்", "தம்பி", "भाई", "भैया"))
            }

            // Sister synonyms
            normalizedName.contains("sister") || normalizedName.contains("அக்கா") || normalizedName.contains("தங்கை") || normalizedName.contains("akka") || normalizedName.contains("thangai") || normalizedName.contains("बहन") || normalizedName.contains("दीदी") -> {
                set.addAll(listOf("sister", "sis", "akka", "thangai", "அக்கா", "தங்கை", "बहन", "दीदी"))
            }
        }

        return set
    }

    private fun computeSimilarity(s1: String, s2: String): Float {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        if (s1 == s2) return 1.0f

        // Word-level or token prefix match
        if (s2.startsWith(s1) || s1.startsWith(s2)) return 0.92f
        if (s2.contains(s1) || s1.contains(s2)) return 0.85f

        val dist = levenshteinDistance(s1, s2)
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0f

        return (1.0f - (dist.toFloat() / maxLen.toFloat())).coerceIn(0.0f, 1.0f)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[s1.length][s2.length]
    }
}
