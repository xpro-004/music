package com.xrotune.app.util

import android.util.Log

enum class OperationType(val value: String) {
    CREATE("create"), UPDATE("update"), DELETE("delete"), LIST("list"), GET("get"), WRITE("write"),
}

/** Logs operation metadata only; never emit user IDs, emails, document paths or raw payloads. */
fun handleFirestoreError(
    exception: Exception,
    operationType: OperationType,
    @Suppress("UNUSED_PARAMETER") path: String?,
): String {
    Log.e("FirestoreError", "Firestore ${operationType.value} failed (${exception.javaClass.simpleName})")
    return "Cloud data operation failed. Please try again."
}
