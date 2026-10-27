package com.hutb.timetable.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.data.local.AppSettings
import com.hutb.timetable.ui.theme.HutbTimetableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationSettingsUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun reminderSectionIsAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            HutbTimetableTheme {
                SettingsScreen(
                    settings = AppSettings(context),
                    semesterId = "",
                    onBack = {},
                    onChanged = {}
                )
            }
        }

        compose.onNodeWithText("每日课程提醒").assertExists()
        compose.onNodeWithText("每日课程摘要").assertExists()
        compose.onNodeWithText("数据同步").assertExists()
        compose.onNodeWithText("回到 App 自动同步本周").assertExists()
        compose.onNodeWithText("当前数据同步：暂无记录").assertExists()
    }
}
