package cloud.kvd.androidoptimizer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
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
        compose.onNodeWithText("Поиск по названию или пакету").assertIsDisplayed()
    }

    @Test fun privacyTabShowsScannerInsteadOfBlankPage() {
        compose.onNodeWithText("Privacy").performClick()
        compose.onNodeWithText("Проверка конфиденциальности").assertIsDisplayed()
    }

    @Test fun moreTabShowsSettingsInsteadOfBlankPage() {
        compose.onNodeWithText("Ещё").performClick()
        compose.onNodeWithText("Доступ и настройки").assertIsDisplayed()
    }

    @Test fun searchFindsInstalledAppWithoutUsagePermission() {
        compose.onAllNodesWithText("Приложения").onFirst().performClick()
        compose.onNodeWithText("Поиск по названию или пакету").performTextInput("cloud.kvd.androidoptimizer")
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("cloud.kvd.androidoptimizer") and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("cloud.kvd.androidoptimizer") and !hasSetTextAction()).assertIsDisplayed()
    }

    @Test fun unmatchedSearchHasAnExplicitEmptyState() {
        compose.onAllNodesWithText("Приложения").onFirst().performClick()
        compose.onNodeWithText("Поиск по названию или пакету").performTextInput("__nonexistent_package__")
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
        compose.onNodeWithText("Поиск по названию или пакету").assertIsDisplayed()
        capture("apps")
        compose.onNodeWithText("Privacy").performClick()
        compose.onNodeWithText("Проверка конфиденциальности").assertIsDisplayed()
        capture("privacy")
        compose.onNodeWithText("Ещё").performClick()
        compose.onNodeWithText("Доступ и настройки").assertIsDisplayed()
        capture("settings")
        compose.onNodeWithText("Главная").performClick()
        compose.onNodeWithText("Проверить устройство").assertIsDisplayed()
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
