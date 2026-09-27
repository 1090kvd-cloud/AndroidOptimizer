package cloud.kvd.androidoptimizer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import android.app.AppOpsManager
import android.os.Process
import org.robolectric.Shadows
import org.junit.Assert.assertFalse
import android.graphics.Bitmap
import java.io.File
import org.robolectric.annotation.GraphicsMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h873dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun appsTabShowsSearchInsteadOfBlankPage() {
        compose.onAllNodesWithText("Приложения").onFirst().performClick()
        compose.onNodeWithText("Название или пакет").assertIsDisplayed()
    }

    @Test fun privacyTabShowsScannerInsteadOfBlankPage() {
        compose.onNodeWithText("Приватность").performClick()
        compose.onNodeWithText("Проверка конфиденциальности").assertIsDisplayed()
    }

    @Test fun moreTabShowsSettingsInsteadOfBlankPage() {
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Доступ и настройки").assertIsDisplayed()
    }

    @Test fun searchFindsInstalledAppWithoutUsagePermission() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Проверить устройство").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle {
            val activity = compose.activity
            Shadows.shadowOf(activity.getSystemService(AppOpsManager::class.java)).setMode(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), activity.packageName, AppOpsManager.MODE_IGNORED
            )
            assertFalse(AppAnalyzer.hasUsageAccess(activity))
        }
        compose.onNodeWithText("Проверить устройство").performClick()
        compose.onAllNodesWithText("Приложения").onFirst().performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Разрешить статистику использования").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Название или пакет").performTextInput("cloud.kvd.androidoptimizer")
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("cloud.kvd.androidoptimizer") and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("cloud.kvd.androidoptimizer") and !hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText("Разрешить статистику использования").assertIsDisplayed()
    }

    @Test fun unmatchedSearchHasAnExplicitEmptyState() {
        compose.onAllNodesWithText("Приложения").onFirst().performClick()
        compose.onNodeWithText("Название или пакет").performTextInput("__nonexistent_package__")
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Ничего не найдено. Измените поиск или фильтр.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Ничего не найдено. Измените поиск или фильтр.").assertIsDisplayed()
    }

    @Test fun screenshotsAndReturnHome() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Проверить устройство").fetchSemanticsNodes().isNotEmpty()
        }
        capture("home")
        compose.onAllNodesWithText("Приложения").onFirst().performClick()
        compose.onNodeWithText("Название или пакет").assertIsDisplayed()
        capture("apps")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Приватность").performClick()
        compose.onNodeWithText("Проверка конфиденциальности").assertIsDisplayed()
        capture("privacy")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Доступ и настройки").assertIsDisplayed()
        capture("settings")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Проверить устройство").assertIsDisplayed()
    }

    @Test fun homeHasNoDuplicateNavigationAndStorageReturnsHome() {
        compose.onAllNodesWithText("Приложения").assertCountEquals(1)
        compose.onNodeWithText("Рекомендации").assertDoesNotExist()
        compose.onNodeWithText("Главная").assertDoesNotExist()
        compose.onNodeWithText("Память").performClick()
        compose.onNodeWithText("Управление хранилищем").assertIsDisplayed()
        capture("storage")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Настройки").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val file = File("build/outputs/screenshots/$name.png")
        file.parentFile?.mkdirs()
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
