package com.mochisofts.mata.data.backup

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DataMutationGate @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withMutation(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun <T> withBackupOperation(block: suspend () -> T): T = mutex.withLock { block() }
}
