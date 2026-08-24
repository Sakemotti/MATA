package com.mochisofts.mata.app

import android.content.Context
import android.util.Log
import com.mochisofts.mata.BuildConfig
import com.mochisofts.mata.data.backup.BackupCoordinator
import com.mochisofts.mata.data.local.MataDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface StartupState {
    data object Initializing : StartupState
    data class Ready(val appVersionChanged: Boolean) : StartupState
    data object Failed : StartupState
}

internal data class StartupRecoveryResult(
    val appVersionChanged: Boolean,
)

internal fun interface StartupRecovery {
    suspend fun recover(): StartupRecoveryResult
}

internal interface StartupDiagnostics {
    fun started()
    fun succeeded(durationMillis: Long, appVersionChanged: Boolean)
    fun failed(durationMillis: Long)
    fun retryRequested()
}

@Singleton
internal class AndroidStartupDiagnostics @Inject constructor() : StartupDiagnostics {
    override fun started() {
        Log.i(TAG, "$EVENT_REQUIRED_START result=start")
    }

    override fun succeeded(durationMillis: Long, appVersionChanged: Boolean) {
        Log.i(
            TAG,
            "$EVENT_REQUIRED_FINISH result=success duration_ms=$durationMillis " +
                "app_version_changed=$appVersionChanged",
        )
    }

    override fun failed(durationMillis: Long) {
        Log.e(TAG, "$EVENT_REQUIRED_FINISH result=failure duration_ms=$durationMillis")
    }

    override fun retryRequested() {
        Log.i(TAG, "$EVENT_REQUIRED_RETRY result=retry")
    }

    private companion object {
        const val TAG = "MATA/Startup"
        const val EVENT_REQUIRED_START = "STARTUP_REQUIRED_START"
        const val EVENT_REQUIRED_FINISH = "STARTUP_REQUIRED_FINISH"
        const val EVENT_REQUIRED_RETRY = "STARTUP_REQUIRED_RETRY"
    }
}

@Singleton
internal class RequiredStartupRecovery @Inject constructor(
    private val database: MataDatabase,
    private val backupCoordinator: BackupCoordinator,
    private val versionStore: SuccessfulStartupVersionStore,
) : StartupRecovery {
    override suspend fun recover(): StartupRecoveryResult {
        database.openHelper.writableDatabase.query("SELECT 1").use { cursor ->
            check(cursor.moveToFirst()) { "Database verification failed" }
        }
        backupCoordinator.recoverInterruptedOperation()
        return StartupRecoveryResult(
            appVersionChanged = versionStore.recordSuccessfulStartup(BuildConfig.VERSION_CODE.toLong()),
        )
    }
}

@Singleton
internal class SuccessfulStartupVersionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun recordSuccessfulStartup(versionCode: Long): Boolean {
        val previous = preferences.takeIf { it.contains(KEY_VERSION_CODE) }
            ?.getLong(KEY_VERSION_CODE, versionCode)
        check(preferences.edit().putLong(KEY_VERSION_CODE, versionCode).commit()) {
            "Successful startup version could not be persisted"
        }
        return previous != null && previous != versionCode
    }

    private companion object {
        const val PREFERENCES_NAME = "startup_state"
        const val KEY_VERSION_CODE = "last_successful_version_code"
    }
}

@Singleton
class StartupCoordinator @Inject internal constructor(
    private val recovery: StartupRecovery,
    @ApplicationCoroutineScope private val scope: CoroutineScope,
    private val diagnostics: StartupDiagnostics,
) {
    private val mutableState = MutableStateFlow<StartupState>(StartupState.Initializing)
    internal val state: StateFlow<StartupState> = mutableState.asStateFlow()
    private var activeJob: Job? = null

    @Synchronized
    fun start() {
        if (mutableState.value is StartupState.Ready || activeJob?.isActive == true) return
        mutableState.value = StartupState.Initializing
        val startedAtNanos = System.nanoTime()
        diagnostics.started()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = recovery.recover()
                mutableState.value = StartupState.Ready(result.appVersionChanged)
                diagnostics.succeeded(elapsedMillis(startedAtNanos), result.appVersionChanged)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = StartupState.Failed
                diagnostics.failed(elapsedMillis(startedAtNanos))
            } finally {
                clearCompletedJob()
            }
        }
        activeJob = job
        job.start()
    }

    fun retry() {
        if (mutableState.value == StartupState.Failed) {
            diagnostics.retryRequested()
            start()
        }
    }

    @Synchronized
    private fun clearCompletedJob() {
        activeJob = null
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND).coerceAtLeast(0)

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationCoroutineScope

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StartupBindingsModule {
    @Binds
    @Singleton
    abstract fun bindStartupRecovery(recovery: RequiredStartupRecovery): StartupRecovery

    @Binds
    @Singleton
    abstract fun bindStartupDiagnostics(diagnostics: AndroidStartupDiagnostics): StartupDiagnostics
}

@Module
@InstallIn(SingletonComponent::class)
internal object StartupScopeModule {
    @Provides
    @Singleton
    @ApplicationCoroutineScope
    fun provideApplicationCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
