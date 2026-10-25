package com.example.juki.data.local

import android.content.Context
import com.example.juki.domain.model.GoldRate
import java.math.BigDecimal

class GoldRateCache(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "gold_rate_cache", Context.MODE_PRIVATE
    )

    fun read(): GoldRate? {
        val date = preferences.getString("date", null)?.takeIf { it.isNotBlank() }
            ?: return null
        val price = preferences.getString("rubles_per_gram", null)?.toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO } ?: return null
        return GoldRate(date, price)
    }

    fun save(rate: GoldRate) {
        preferences.edit()
            .putString("date", rate.date)
            .putString("rubles_per_gram", rate.rublesPerGram.toPlainString())
            .apply()
    }
}
