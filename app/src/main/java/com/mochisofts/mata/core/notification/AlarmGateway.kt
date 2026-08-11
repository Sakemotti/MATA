package com.mochisofts.mata.core.notification

interface AlarmGateway {
    fun schedule(
        candidateKey: String,
        requestCode: Int,
        triggerAtMillis: Long,
        exact: Boolean,
    )

    fun cancel(candidateKey: String, requestCode: Int)
}
