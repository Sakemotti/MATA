package com.mochisofts.mata.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.time.LocalDate

internal const val TARGET_PACKAGE = "com.mochisofts.mata"
internal const val BENCHMARK_ITERATIONS = 10

private const val MAIN_ACTIVITY = "$TARGET_PACKAGE.app.MainActivity"
private const val ACTION_OPEN_NOTIFICATION = "$TARGET_PACKAGE.action.OPEN_NOTIFICATION"
private const val ACTION_OPEN_WIDGET = "$TARGET_PACKAGE.action.OPEN_WIDGET"
private const val ACTION_SEED_BENCHMARK_DATA = "$TARGET_PACKAGE.action.SEED_BENCHMARK_DATA"
private const val EXTRA_TODO_ID = "todo_id"
private const val EXTRA_LOGICAL_DATE = "logical_date"
private const val EXTRA_CANDIDATE_KEY = "candidate_key"
private const val EXTRA_WIDGET_DATE = "widget_date"
private const val EXTRA_WIDGET_MODE = "widget_mode"
private const val WIDGET_MODE_DATE = "DATE"
private const val BENCHMARK_TODO_ID = "00000000-0000-0000-0000-000000000001"
private const val BENCHMARK_RECEIVER =
    "$TARGET_PACKAGE/com.mochisofts.mata.benchmark.BenchmarkDataReceiver"

internal fun resetBenchmarkData() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    check(device.executeShellCommand("pm clear $TARGET_PACKAGE").contains("Success"))
    val broadcastResult = device.executeShellCommand(
        "am broadcast --receiver-foreground -a $ACTION_SEED_BENCHMARK_DATA -n $BENCHMARK_RECEIVER",
    )
    check(broadcastResult.contains("Broadcast completed"))
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.startFromLauncher() {
    startActivityAndWait()
}

internal fun MacrobenchmarkScope.startFromNotification() {
    startActivityAndWait(
        targetIntent(ACTION_OPEN_NOTIFICATION)
            .putExtra(EXTRA_TODO_ID, BENCHMARK_TODO_ID)
            .putExtra(EXTRA_LOGICAL_DATE, LocalDate.now().toString())
            .putExtra(EXTRA_CANDIDATE_KEY, "benchmark"),
    )
}

internal fun MacrobenchmarkScope.startFromWidget() {
    startActivityAndWait(
        targetIntent(ACTION_OPEN_WIDGET)
            .putExtra(EXTRA_WIDGET_DATE, LocalDate.now().toString())
            .putExtra(EXTRA_WIDGET_MODE, WIDGET_MODE_DATE),
    )
}

internal fun scrollTodoList() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.waitForIdle()
    val centerX = device.displayWidth / 2
    val upperY = device.displayHeight / 4
    val lowerY = device.displayHeight * 3 / 4
    repeat(4) {
        device.swipe(centerX, lowerY, centerX, upperY, 20)
        device.waitForIdle()
    }
    repeat(2) {
        device.swipe(centerX, upperY, centerX, lowerY, 20)
        device.waitForIdle()
    }
}

private fun targetIntent(action: String): Intent = Intent(action).apply {
    setClassName(TARGET_PACKAGE, MAIN_ACTIVITY)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}
