package com.mochisofts.mata.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI

internal const val PRIVACY_POLICY_PATH = "/mata/privacy"
internal const val TERMS_PATH = "/mata/terms"

internal fun isApprovedLegalUrl(url: String, expectedPath: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme == "https" &&
        uri.host == "mochisofts.com" &&
        uri.path == expectedPath &&
        uri.query == null &&
        uri.fragment == null
}

internal fun openLegalDocument(
    context: Context,
    url: String,
    expectedPath: String,
): Boolean {
    if (!isApprovedLegalUrl(url, expectedPath)) return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    return runCatching { context.startActivity(intent) }.isSuccess
}
