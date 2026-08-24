package com.mochisofts.mata.app

internal class OneShotFullyDrawnReporter(
    private val reportAction: () -> Unit,
) {
    private var reported = false

    fun report(): Boolean {
        if (reported) return false
        reported = true
        reportAction()
        return true
    }
}
