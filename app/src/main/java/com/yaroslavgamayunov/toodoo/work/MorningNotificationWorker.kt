package com.yaroslavgamayunov.toodoo.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yaroslavgamayunov.toodoo.R
import com.yaroslavgamayunov.toodoo.domain.GetCountOfDailyTasksUseCase
import com.yaroslavgamayunov.toodoo.ui.MainActivity
import com.yaroslavgamayunov.toodoo.util.TimeUtils
import com.yaroslavgamayunov.toodoo.util.createNotificationChannel
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.Int
import kotlin.Unit
import com.yaroslavgamayunov.toodoo.domain.common.Result as UseCaseResult

class MorningNotificationWorker(
    context: Context,
    workerParameters: WorkerParameters,
    val getCountOfDailyTasksUseCase: GetCountOfDailyTasksUseCase
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        val countOfDailyTasks: Int =
            (getCountOfDailyTasksUseCase(Unit) as? UseCaseResult.Success)?.data
                ?: return Result.retry()

        if (countOfDailyTasks > 0) showNotification(countOfDailyTasks)

        schedule(applicationContext)
        return Result.success()
    }

    private fun showNotification(countOfDailyTasks: Int) {
        val notificationManager =
            applicationContext.getSystemService<NotificationManager>() ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                channelName = applicationContext.getString(R.string.daily_tasks_notification_channel_title),
                channelId = DAILY_TASKS_NOTIFICATION_CHANNEL_ID
            )
        }

        val contentText = applicationContext.resources.getQuantityString(
            R.plurals.dont_forget_to_complete_tasks,
            countOfDailyTasks,
            countOfDailyTasks
        )

        val contentTitle = applicationContext.getString(R.string.daily_tasks_notification_title)
        val intent = Intent(applicationContext, MainActivity::class.java)

        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )

        val notification = NotificationCompat
            .Builder(applicationContext, DAILY_TASKS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(DAILY_TASKS_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val WORKER_TAG = "morning_notification_worker"
        private const val DAILY_TASKS_NOTIFICATION_CHANNEL_ID =
            "daily_tasks_notification_channel_id"
        private const val DAILY_TASKS_NOTIFICATION_ID = 0

        // TODO: Make it customizable
        // Approximate time of day when notification is supposed to be shown
        private val notificationTimeOfDay = LocalTime.of(10, 0)

        fun schedule(context: Context) {
            val closestTime = TimeUtils.getClosestTimeOfDay(notificationTimeOfDay)

            val request =
                OneTimeWorkRequestBuilder<MorningNotificationWorker>()
                    .addTag(WORKER_TAG)
                    .setInitialDelay(
                        Duration.between(Instant.now(), closestTime).seconds,
                        TimeUnit.SECONDS
                    )
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORKER_TAG,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}