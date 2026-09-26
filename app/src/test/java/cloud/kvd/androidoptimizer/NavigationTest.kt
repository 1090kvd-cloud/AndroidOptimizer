package cloud.kvd.androidoptimizer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun appsTabShowsSearchInsteadOfBlankPage() {
        compose.onNodeWithText("Приложения").performClick()
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
}
