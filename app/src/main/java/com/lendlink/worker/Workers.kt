package com.lendlink.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.lendlink.data.local.AppDatabase
import com.lendlink.data.repository.BorrowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

const val CHANNEL_ID = "lendlink_alerts"
const val PENALTY_AMOUNT = 1_000L

class DeadlineCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            createChannel(applicationContext)
            val db = AppDatabase.get(applicationContext)
            val repo = BorrowRepository(db.borrowDao(), db.lenderCreditHistoryDao(),
                db.borrowerPaymentHistoryDao(), db.lendHistoryDao(), db.borrowHistoryDao())
            val records = repo.getAllActive()
            val now = System.currentTimeMillis()
            records.forEach { rec ->
                val remaining = rec.deadline - now
                val hrs = remaining / 3_600_000L
                when {
                    remaining > 0 && hrs <= 24 && hrs > 10 ->
                        notify(applicationContext, rec.recordId.hashCode(),
                            "⏰ Return Reminder — 24 Hours Left",
                            "Please return '${rec.itemName}' to ${rec.lenderName}")
                    remaining > 0 && hrs <= 10 ->
                        notify(applicationContext, (rec.recordId + hrs).hashCode(),
                            "⚠️ ${hrs}h Left to Return",
                            "Return '${rec.itemName}' to ${rec.lenderName} ASAP!")
                }
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}

class PenaltyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            createChannel(applicationContext)
            val db = AppDatabase.get(applicationContext)
            val repo = BorrowRepository(db.borrowDao(), db.lenderCreditHistoryDao(),
                db.borrowerPaymentHistoryDao(), db.lendHistoryDao(), db.borrowHistoryDao())
            val now = System.currentTimeMillis()
            repo.getAllActive().filter { it.deadline < now && it.status == "active" }.forEach { rec ->
                repo.applyPenalty(rec, PENALTY_AMOUNT)
                notify(applicationContext, (rec.recordId + "_pen").hashCode(),
                    "💸 Overdue Penalty Charged",
                    "₩${PENALTY_AMOUNT} deducted for overdue '${rec.itemName}'. Return it now!")
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}

object WorkerScheduler {
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.enqueueUniquePeriodicWork("deadline_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DeadlineCheckWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build())
        wm.enqueueUniquePeriodicWork("penalty_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<PenaltyWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build())
    }
}

fun createChannel(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(CHANNEL_ID, "LendLink Alerts", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Deadline reminders and overdue penalty alerts"; enableVibration(true) }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}

fun notify(ctx: Context, id: Int, title: String, body: String) {
    try {
        NotificationManagerCompat.from(ctx).notify(id,
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title).setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
    } catch (_: SecurityException) { /* Permission not granted */ }
}
