package com.mochisofts.mata.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun prepareFixedData() = resetBenchmarkData()

    @Test
    fun coldLauncherWithoutCompilation() = measureStartup(
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
    ) { startFromLauncher() }

    @Test
    fun coldLauncherOptimized() = measureStartup(StartupMode.COLD) { startFromLauncher() }

    @Test
    fun warmLauncher() = measureStartup(StartupMode.WARM) { startFromLauncher() }

    @Test
    fun hotLauncher() = measureStartup(StartupMode.HOT) { startFromLauncher() }

    @Test
    fun coldNotification() = measureStartup(StartupMode.COLD) { startFromNotification() }

    @Test
    fun coldWidget() = measureStartup(StartupMode.COLD) { startFromWidget() }

    private fun measureStartup(
        startupMode: StartupMode,
        compilationMode: CompilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.UseIfAvailable,
        ),
        launch: androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = startupMode,
        iterations = BENCHMARK_ITERATIONS,
        setupBlock = { pressHome() },
        measureBlock = launch,
    )
}
