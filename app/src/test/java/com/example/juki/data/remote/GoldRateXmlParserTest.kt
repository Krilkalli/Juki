package com.example.juki.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

class GoldRateXmlParserTest {
    private val parser = GoldRateXmlParser()

    @Test
    fun selectsLatestGoldRecordRegardlessOfOrderAndOtherMetals() {
        val result = parse("""
            <Metall>
                <Record Date="01.10.2026" Code="1"><Buy>11000,50</Buy><Sell>12000,00</Sell></Record>
                <Record Date="30.09.2026" Code="1"><Buy>10500,00</Buy></Record>
                <Record Date="02.10.2026" Code="2"><Buy>150,00</Buy></Record>
            </Metall>
        """)

        assertEquals("01.10.2026", result.date)
        assertEquals(BigDecimal("11000.50"), result.rublesPerGram)
    }

    @Test
    fun readsWindows1251DeclarationAndNonBreakingSpaces() {
        val xml = """
            <?xml version="1.0" encoding="windows-1251"?>
            <Metall name="Котировки металлов">
                <Record Date="24.10.2026" Code="1"><Buy>10 482,37</Buy></Record>
            </Metall>
        """.trimIndent()
        val result = xml.byteInputStream(charset("windows-1251")).use(parser::parse)

        assertEquals(BigDecimal("10482.37"), result.rublesPerGram)
    }

    @Test
    fun rejectsResponsesWithoutValidGoldInsteadOfInventingAPrice() {
        val responses = listOf(
            "<Metall/>",
            "<Metall><Record Date=\"24.10.2026\" Code=\"2\"><Buy>150,00</Buy></Record></Metall>",
            "<Metall><Record Date=\"31.02.2026\" Code=\"1\"><Buy>10000,00</Buy></Record></Metall>",
            "<Metall><Record Date=\"24.10.2026\" Code=\"1\"><Buy>-1</Buy></Record></Metall>",
            "<Metall><Record Date=\"24.10.2026\" Code=\"1\"><Buy>NaN</Buy></Record></Metall>",
            "<html><body>Service unavailable</body></html>",
            "<Metall><Record"
        )

        responses.forEach { xml ->
            assertThrows(IOException::class.java) { parse(xml) }
        }
    }

    private fun parse(xml: String) = xml.trimIndent().byteInputStream().use(parser::parse)
}
