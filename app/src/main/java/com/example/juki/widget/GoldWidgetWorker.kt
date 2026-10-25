package com.example.juki.widget

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.juki.MyApplication
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class GoldWidgetWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        if (GoldWidgetRenderer.widgetIds(applicationContext).isEmpty()) return Result.success()
        GoldWidgetRenderer.updateAll(applicationContext, GoldWidgetStatus.LOADING)
        return try {
            val repository = (applicationContext as MyApplication).goldRateRepository
            repository.refresh()
            GoldWidgetRenderer.updateAll(applicationContext)
            Result.success()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w("GoldWidgetWorker", "Не удалось обновить курс золота", exception)
            GoldWidgetRenderer.updateAll(applicationContext, GoldWidgetStatus.ERROR)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val REFRESH_WORK = "gold_widget_refresh"
        private const val PERIODIC_WORK = "gold_widget_periodic"
        private const val UPDATE_HOURS = 6L

        fun enqueueRefresh(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                REFRESH_WORK, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<GoldWidgetWorker>().build()
            )
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<GoldWidgetWorker>(UPDATE_HOURS, TimeUnit.HOURS)
                .setInitialDelay(UPDATE_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(REFRESH_WORK)
                cancelUniqueWork(PERIODIC_WORK)
            }
        }
    }
}
