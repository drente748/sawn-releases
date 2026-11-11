package com.sawn.domain.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * وضع التركيز العميق: كل تطبيق خارج القائمة البيضاء محجوب، والفكّ نافذة واحدة
 * محدودة الصلاحية (رقم سري + مهلة انتظار + نافذة خروج قصيرة).
 */
class DeepFocusPolicyTest {

    private val whitelist = setOf("com.android.dialer", "com.google.android.apps.maps")

    @Test fun disabledModeNeverRestricts() {
        assertFalse(
            DeepFocusPolicy.isRestricted(
                packageName = "com.instagram.android",
                whitelist = whitelist,
                enabled = false,
                unlockStartsAt = 0L,
                unlockUntil = 0L,
                now = 1_000L,
            ),
        )
    }

    @Test fun appOutsideWhitelistIsRestricted() {
        assertTrue(
            DeepFocusPolicy.isRestricted(
                packageName = "com.instagram.android",
                whitelist = whitelist,
                enabled = true,
                unlockStartsAt = 0L,
                unlockUntil = 0L,
                now = 1_000L,
            ),
        )
    }

    @Test fun whitelistedAppIsAllowed() {
        assertFalse(
            DeepFocusPolicy.isRestricted(
                packageName = "com.android.dialer",
                whitelist = whitelist,
                enabled = true,
                unlockStartsAt = 0L,
                unlockUntil = 0L,
                now = 1_000L,
            ),
        )
    }

    @Test fun delayHoldsTheBlockEvenWithTheRightPin() {
        val now = 1_000_000L
        val startsAt = DeepFocusPolicy.unlockStartsAt(now)
        assertTrue(
            DeepFocusPolicy.isRestricted(
                packageName = "com.instagram.android",
                whitelist = whitelist,
                enabled = true,
                unlockStartsAt = startsAt,
                unlockUntil = DeepFocusPolicy.unlockUntil(now),
                now = startsAt - 1L,
            ),
        )
    }

    @Test fun exitWindowOpensAfterTheDelay() {
        val now = 1_000_000L
        val startsAt = DeepFocusPolicy.unlockStartsAt(now)
        val until = DeepFocusPolicy.unlockUntil(now)
        assertFalse(
            DeepFocusPolicy.isRestricted(
                packageName = "com.instagram.android",
                whitelist = whitelist,
                enabled = true,
                unlockStartsAt = startsAt,
                unlockUntil = until,
                now = startsAt,
            ),
        )
    }

    @Test fun blockingReturnsWhenTheExitWindowCloses() {
        val now = 1_000_000L
        val until = DeepFocusPolicy.unlockUntil(now)
        assertTrue(
            DeepFocusPolicy.isRestricted(
                packageName = "com.instagram.android",
                whitelist = whitelist,
                enabled = true,
                unlockStartsAt = DeepFocusPolicy.unlockStartsAt(now),
                unlockUntil = until,
                now = until,
            ),
        )
    }

    @Test fun unlockWindowIsDelayPlusEscape() {
        val now = 1_000_000L
        assertTrue(
            DeepFocusPolicy.unlockUntil(now) - DeepFocusPolicy.unlockStartsAt(now) ==
                DeepFocusPolicy.ESCAPE_WINDOW_SECONDS * 1_000L,
        )
    }

    @Test fun turningTheMasterSwitchOffStopsAllBlocking() {
        assertFalse(
            DeepFocusPolicy.isRestricted(
                packageName = "com.instagram.android",
                whitelist = whitelist,
                enabled = true,
                masterEnabled = false,
                unlockStartsAt = 0L,
                unlockUntil = 0L,
                now = 1_000L,
            ),
        )
    }
}
