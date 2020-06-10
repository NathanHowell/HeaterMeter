package org.alienz.heatermeter.data

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class CompactingWorker(appContext: Context, workerParams: WorkerParameters) :
    ListenableWorker(appContext, workerParams) {

    override fun startWork(): ListenableFuture<Result> {
        val result = SettableFuture.create<Result>()

        GlobalScope.launch {
            try {


                AppDatabase.getInstance(applicationContext)
                    .samples()
                    .trim(Instant.now().minus(Duration.ofDays(2L)))

                result.set(Result.success())
            } catch (e: Throwable) {
                Log.e(CompactingWorker::class.java.simpleName, "Failure trimming old samples", e)
                result.set(Result.failure())
            }
        }

        return result
    }
}