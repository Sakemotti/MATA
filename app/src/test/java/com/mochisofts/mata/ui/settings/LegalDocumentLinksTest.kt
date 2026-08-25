package com.mochisofts.mata.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalDocumentLinksTest {
    @Test
    fun `approved legal URLs require exact host and path over HTTPS`() {
        assertTrue(
            isApprovedLegalUrl(
                "https://mochisofts.com/mata/privacy",
                PRIVACY_POLICY_PATH,
            ),
        )
        assertTrue(isApprovedLegalUrl("https://mochisofts.com/mata/terms", TERMS_PATH))
        assertFalse(isApprovedLegalUrl("http://mochisofts.com/mata/privacy", PRIVACY_POLICY_PATH))
        assertFalse(isApprovedLegalUrl("https://example.com/mata/privacy", PRIVACY_POLICY_PATH))
        assertFalse(isApprovedLegalUrl("https://mochisofts.com/mata/privacy?next=1", PRIVACY_POLICY_PATH))
        assertFalse(isApprovedLegalUrl("https://mochisofts.com/mata/privacy/extra", PRIVACY_POLICY_PATH))
        assertFalse(isApprovedLegalUrl("", PRIVACY_POLICY_PATH))
    }
}
