package com.wulalala66.jiaoji

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 国产 ROM 的权限跳转：不崩、有名字、能给个兜底入口 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OemHelperTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Repo.I.init(ctx)
        Repo.I.clearAll()
    }

    @Test
    fun romNameIsNeverBlank() {
        val name = OemHelper.romName()
        assertNotNull(name)
        assertTrue(name.isNotBlank())
    }

    @Test
    fun fullScreenIntentCheckDoesNotCrash() {
        // API 33 上恒为 true；API 34 会走 NotificationManager.canUseFullScreenIntent
        assertTrue(OemHelper.canUseFullScreenIntent(ctx))
    }

    @Test
    fun permissionPagesAlwaysFallBackToAppDetails() {
        // Robolectric 的 Build.MANUFACTURER 是 robolectric，各家专用页都不存在，
        // 必须退到应用详情页而不是抛异常
        assertTrue(OemHelper.openBackgroundPopup(ctx))
        assertTrue(OemHelper.openAutoStart(ctx))
        assertTrue(OemHelper.openBatterySettings(ctx))
        assertTrue(OemHelper.openFullScreenIntentSettings(ctx))
        assertTrue(OemHelper.openAppDetails(ctx))
    }

    @Test
    fun ringActivityIsNotAliveUntilShown() {
        assertFalse(RingActivity.alive)
    }
}
