package cloud.kvd.androidoptimizer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackageConfirmationTest {
    @get:Rule val compose = createComposeRule()
    private val finding = SystemAppFinding("SalesStatistics", "com.transsion.statisticalsales",
        DebloatCategory.OPTIONAL_SYSTEM_APP, DebloatRecommendation.REVIEW, listOf("Статистика производителя"))

    @Test fun disableRequiresExplicitConfirmationAndCancelDoesNothing() {
        var changes = 0
        compose.setContent { OptimizerTheme {
            PrivacyScreen(listOf(finding), false, null, {}, {}, ShizukuState.READY, null, {}, { _, _ -> changes++ })
        } }
        compose.onNodeWithText("Отключить").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, changes) }
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertEquals(0, changes) }
        compose.onNodeWithText("Отключить").performScrollTo().performClick()
        compose.onNodeWithText("Подтвердить").performClick()
        compose.runOnIdle { assertEquals(1, changes) }
    }

    @Test fun disabledPackagesCanBeFoundAndReenabled() {
        var restored: String? = null
        compose.setContent { OptimizerTheme {
            PrivacyScreen(listOf(finding.copy(enabled = false, recommendation = DebloatRecommendation.KEEP)), false, null,
                {}, {}, ShizukuState.READY, null, {}, { pkg, enabled -> if (enabled) restored = pkg })
        } }
        compose.onNodeWithText("Отключённые").performScrollTo().performClick()
        compose.onNodeWithText("Включить обратно").performScrollTo().performClick()
        compose.onNodeWithText("Подтвердить").performClick()
        compose.runOnIdle { assertEquals("com.transsion.statisticalsales", restored) }
    }

    @Test fun unavailableShizukuCannotChangePackages() {
        compose.setContent { OptimizerTheme {
            PrivacyScreen(listOf(finding), false, null, {}, {}, ShizukuState.UNAVAILABLE, null, {}, { _, _ -> error("Unexpected change") })
        } }
        compose.onNodeWithText("Отключить").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Подключить Shizuku для отключения").assertIsDisplayed()
    }

    @Test fun protectedPackageHasNoDisableAction() {
        compose.setContent { OptimizerTheme {
            PrivacyScreen(listOf(finding.copy(category = DebloatCategory.PROTECTED)), false, null,
                {}, {}, ShizukuState.READY, null, {}, { _, _ -> error("Unexpected change") })
        } }
        compose.onNodeWithText("Отключить").assertDoesNotExist()
    }
}
