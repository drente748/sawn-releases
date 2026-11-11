package com.sawn.domain.update

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * سياسة التحديث التلقائي.
 *
 * هذه القرارات تحدّد هل يتصل التطبيق بالخادم، وهل ينزّل ٣٧ ميغابايت على بيانات
 * المستخدم — فكل خطأ هنا خطأ في الشبكة أو في الاستهلاك، لا في المظهر.
 */
class UpdateAutoPolicyTest {

    @Test
    fun `disabled auto update does nothing at all`() {
        assertEquals(
            UpdateAction.NONE,
            UpdateAutoPolicy.decide(
                autoUpdateEnabled = false,
                autoDownloadEnabled = true,
                availability = UpdateAvailability.NEWER,
            ),
        )
    }

    @Test
    fun `disabled auto update does not even retry after a failure`() {
        assertEquals(
            UpdateAction.NONE,
            UpdateAutoPolicy.decide(
                autoUpdateEnabled = false,
                autoDownloadEnabled = false,
                availability = UpdateAvailability.FAILED,
            ),
        )
    }

    @Test
    fun `a new version is downloaded when both switches are on`() {
        assertEquals(
            UpdateAction.DOWNLOAD,
            UpdateAutoPolicy.decide(
                autoUpdateEnabled = true,
                autoDownloadEnabled = true,
                availability = UpdateAvailability.NEWER,
                availableVersionCode = 31,
            ),
        )
    }

    @Test
    fun `a new version is only announced when auto download is off`() {
        assertEquals(
            UpdateAction.NOTIFY,
            UpdateAutoPolicy.decide(
                autoUpdateEnabled = true,
                autoDownloadEnabled = false,
                availability = UpdateAvailability.NEWER,
                availableVersionCode = 31,
            ),
        )
    }

    @Test
    fun `an already downloaded version is not downloaded again`() {
        assertEquals(
            UpdateAction.NOTIFY,
            UpdateAutoPolicy.decide(
                autoUpdateEnabled = true,
                autoDownloadEnabled = true,
                availability = UpdateAvailability.NEWER,
                pendingVersionCode = 31,
                availableVersionCode = 31,
            ),
        )
    }

    @Test
    fun `a newer version than the downloaded one replaces it`() {
        assertEquals(
            UpdateAction.DOWNLOAD,
            UpdateAutoPolicy.decide(
                autoUpdateEnabled = true,
                autoDownloadEnabled = true,
                availability = UpdateAvailability.NEWER,
                pendingVersionCode = 30,
                availableVersionCode = 31,
            ),
        )
    }

    @Test
    fun `being up to date clears the pending update`() {
        assertEquals(
            UpdateAction.CLEAR,
            UpdateAutoPolicy.decide(
                autoUpdateEnabled = true,
                autoDownloadEnabled = true,
                availability = UpdateAvailability.UP_TO_DATE,
                pendingVersionCode = 31,
            ),
        )
    }

    @Test
    fun `a transient failure is retried`() {
        assertEquals(
            UpdateAction.RETRY,
            UpdateAutoPolicy.decide(
                autoUpdateEnabled = true,
                autoDownloadEnabled = true,
                availability = UpdateAvailability.FAILED,
            ),
        )
    }

    @Test
    fun `an unconfigured channel does nothing`() {
        assertEquals(
            UpdateAction.NONE,
            UpdateAutoPolicy.decide(
                autoUpdateEnabled = true,
                autoDownloadEnabled = true,
                availability = UpdateAvailability.DISABLED,
            ),
        )
    }
}
