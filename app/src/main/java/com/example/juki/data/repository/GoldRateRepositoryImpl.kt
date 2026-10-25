package com.example.juki.data.repository

import com.example.juki.data.local.GoldRateCache
import com.example.juki.data.remote.CbrApi
import com.example.juki.data.remote.GoldRateXmlParser
import com.example.juki.domain.model.GoldRate
import com.example.juki.domain.repository.GoldRateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class GoldRateRepositoryImpl(
    private val api: CbrApi,
    private val cache: GoldRateCache,
    private val parser: GoldRateXmlParser = GoldRateXmlParser()
) : GoldRateRepository {
    private val mutableRate = MutableStateFlow(cache.read())
    override val rate: StateFlow<GoldRate?> = mutableRate.asStateFlow()
    private val refreshMutex = Mutex()

    override suspend fun refresh(): GoldRate = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val timeZone = TimeZone.getTimeZone("Europe/Moscow")
            val format = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
                this.timeZone = timeZone
            }
            val calendar = Calendar.getInstance(timeZone)
            val toDate = format.format(calendar.time)
            // Берём диапазон, чтобы выходные и праздники не давали пустой ответ.
            calendar.add(Calendar.DAY_OF_YEAR, -31)
            val fromDate = format.format(calendar.time)

            val updated = api.getMetal(fromDate, toDate).use { body ->
                parser.parse(body.byteStream())
            }
            cache.save(updated)
            mutableRate.value = updated
            updated
        }
    }
}
