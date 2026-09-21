package com.trionsandroid.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsNewerTest {
    @Test fun higherPatchIsNewer() = assertTrue(isNewer("0.2.9", "0.2.8"))

    @Test fun higherMinorIsNewer() = assertTrue(isNewer("v0.3.0", "0.2.9"))

    @Test fun compareNumbersNotText() = assertTrue(isNewer("0.10.0", "0.9.0"))

    @Test fun sameVersionIsNotNewer() = assertFalse(isNewer("0.2.8", "0.2.8"))

    @Test fun missingPartCountsAsZero() = assertFalse(isNewer("0.2", "0.2.0"))

    @Test fun olderIsNotNewer() = assertFalse(isNewer("0.2.7", "0.2.8"))
}
