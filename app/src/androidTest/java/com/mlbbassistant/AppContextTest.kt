package com.mlbbassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test — verifies the correct package name is resolved
 * and the app context is accessible on a real device / emulator.
 */
@RunWith(AndroidJUnit4::class)
class AppContextTest {

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.mlbbassistant", appContext.packageName)
    }
}
