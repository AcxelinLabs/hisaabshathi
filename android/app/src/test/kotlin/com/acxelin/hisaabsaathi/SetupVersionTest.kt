package com.acxelin.hisaabsaathi

import com.acxelin.hisaabsaathi.data.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Setup shows on a new phone, once after updating to a version that adds to it, then never again. */
class SetupVersionTest {
    @Test fun whenSetupShows() {
        assertTrue(Settings().needsSetup)                                             // new phone
        assertTrue(Settings(onboarded = true, setupDone = 0).needsSetup)             // updated from 0.9.2 or earlier
        assertFalse(Settings(onboarded = true, setupDone = Settings.SETUP_VERSION).needsSetup)
    }
}
