package com.example.juki.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.juki.MyApplication
import com.example.juki.data.local.GoldRateCache
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class GoldRateNetworkTest {
    @Test
    fun realCbrRequestResolvesKoinGraphAndSavesRate() = runBlocking {
        // Сетевую проверку запускаем явно; обычные тесты не зависят от сайта ЦБ.
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveCbr") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = (context.applicationContext as MyApplication).goldRateRepository
        val rate = withTimeout(30_000) { repository.refresh() }

        assertTrue(rate.rublesPerGram > BigDecimal.ZERO)
        assertEquals(rate, repository.rate.value)
        assertEquals(rate, GoldRateCache(context).read())
    }
}
