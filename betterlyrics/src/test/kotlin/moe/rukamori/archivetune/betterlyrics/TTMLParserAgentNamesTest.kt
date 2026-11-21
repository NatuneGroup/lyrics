package moe.rukamori.archivetune.betterlyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class TTMLParserAgentNamesTest {
    private val ttml =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\">\n" +
            "  <head><metadata>\n" +
            "    <ttm:agent type=\"person\" xml:id=\"v1\"><ttm:name type=\"full\">Alice</ttm:name></ttm:agent>\n" +
            "    <ttm:agent type=\"person\" xml:id=\"v2\"><ttm:name type=\"full\">Bob</ttm:name></ttm:agent>\n" +
            "  </metadata></head>\n" +
            "  <body><div>\n" +
            "    <p begin=\"00:00:01.000\" end=\"00:00:02.000\" ttm:agent=\"v1\">lead</p>\n" +
            "    <p begin=\"00:00:03.000\" end=\"00:00:04.000\" ttm:agent=\"v1 v2\">together</p>\n" +
            "    <p begin=\"00:00:05.000\" end=\"00:00:06.000\" ttm:agent=\"v3\">unnamed</p>\n" +
            "  </div></body>\n" +
            "</tt>"

    @Test
    fun `lines carry the display names of their declared singers`() {
        val lines = TTMLParser.parseTTML(ttml)
        assertEquals(listOf(listOf("Alice"), listOf("Alice", "Bob"), emptyList()), lines.map { it.agentNames })
        assertEquals(listOf("v1", "v1 v2", "v3"), lines.map { it.agent })
    }
}
