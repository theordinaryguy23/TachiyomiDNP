package eu.kanade.tachiyomi.data.sync

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until the Task completes.
 */
suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            try {
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("Task failed without exception")
                    )
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Exception) {}
        }
    }
