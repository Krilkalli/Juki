package com.example.juki.data.remote

import com.example.juki.domain.model.GoldRate
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class GoldRateXmlParser {
    fun parse(input: InputStream): GoldRate {
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            // Кодировку берём из XML-декларации ЦБ (windows-1251).
            parser.setInput(input, null)
            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "Metall")

            var latest: GoldRate? = null
            var latestDate = Long.MIN_VALUE
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.name != "Record" || parser.getAttributeValue(null, "Code") != "1") {
                    skipElement(parser)
                    continue
                }

                val date = parser.getAttributeValue(null, "Date")
                var price: BigDecimal? = null
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    if (parser.name == "Buy") {
                        price = parser.nextText().trim()
                            .replace(" ", "")
                            .replace("\u00a0", "")
                            .replace(',', '.')
                            .toBigDecimalOrNull()
                    } else {
                        skipElement(parser)
                    }
                }

                val timestamp = date?.let(::parseDate)
                val validPrice = price?.takeIf { it > BigDecimal.ZERO }
                if (timestamp != null && validPrice != null && timestamp > latestDate) {
                    latestDate = timestamp
                    latest = GoldRate(date, validPrice)
                }
            }
            return latest ?: throw IOException("В ответе ЦБ нет корректной цены золота")
        } catch (exception: XmlPullParserException) {
            throw IOException("Не удалось разобрать XML с курсом золота", exception)
        }
    }

    private fun parseDate(value: String): Long? {
        if (!value.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) return null
        val format = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("Europe/Moscow")
        }
        return format.parse(value, ParsePosition(0))?.time
    }

    private fun skipElement(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> throw IOException("Незавершённый XML от ЦБ")
            }
        }
    }
}
