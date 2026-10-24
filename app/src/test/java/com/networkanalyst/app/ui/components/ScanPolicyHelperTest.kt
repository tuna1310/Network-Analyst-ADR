package com.networkanalyst.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPolicyHelperTest {

    @Test
    fun shouldShowWarning_whenNotAccepted_returnsTrue() {
        assertTrue(ScanPolicyHelper.shouldShowWarning(isAccepted = false))
        assertFalse(ScanPolicyHelper.canProceedWithScan(isAccepted = false))
    }

    @Test
    fun shouldShowWarning_whenAccepted_returnsFalse() {
        assertFalse(ScanPolicyHelper.shouldShowWarning(isAccepted = true))
        assertTrue(ScanPolicyHelper.canProceedWithScan(isAccepted = true))
    }
}
