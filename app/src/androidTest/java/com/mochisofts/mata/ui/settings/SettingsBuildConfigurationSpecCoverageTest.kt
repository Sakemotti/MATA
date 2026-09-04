package com.mochisofts.mata.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsBuildConfigurationSpecCoverageTest {
    @Test
    fun std07_debugBuildUsesExplicitApprovedLegalUrlsAndIdentity() {
        assertTrue(BuildConfig.DEBUG)
        assertEquals("com.mochisofts.mata.debug", BuildConfig.APPLICATION_ID)
        assertEquals("https://mochisofts.com/mata/privacy", BuildConfig.PRIVACY_POLICY_URL)
        assertEquals("https://mochisofts.com/mata/terms", BuildConfig.TERMS_URL)
        assertTrue(isApprovedLegalUrl(BuildConfig.PRIVACY_POLICY_URL, PRIVACY_POLICY_PATH))
        assertTrue(isApprovedLegalUrl(BuildConfig.TERMS_URL, TERMS_PATH))
    }
}
