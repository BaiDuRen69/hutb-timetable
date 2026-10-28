package com.hutb.timetable.ui.timetable

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.core.RoomOption
import com.hutb.timetable.ui.theme.HutbTimetableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPickerDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val campuses = listOf(
        RoomOption("south", "南校区"),
        RoomOption("north", "北校区")
    )
    private val buildings = listOf(
        RoomOption("zhicheng", "至诚楼"),
        RoomOption("yuezhi", "乐知楼")
    )

    @Test
    fun oneTapShortcutAppliesWholeBuilding() {
        var confirmed: List<String?>? = null
        compose.setContent {
            HutbTimetableTheme {
                RoomPickerDialog(
                    campuses = campuses,
                    buildings = buildings,
                    classrooms = emptyList(),
                    initialCampusId = null,
                    initialBuildingId = null,
                    initialRoomCode = null,
                    buildingsLoading = false,
                    classroomsLoading = false,
                    onDismiss = {},
                    onPreviewCampus = {},
                    onPreviewBuilding = { _, _, _ -> },
                    onConfirm = { campusId, buildingId, buildingName, roomCode ->
                        confirmed = listOf(campusId, buildingId, buildingName, roomCode)
                    }
                )
            }
        }

        // 底部操作栏固定可见；选完教学楼后可直接用教室区顶部的一键按钮
        compose.onNodeWithText("确定").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("请选择校区和教学楼").assertIsDisplayed()

        compose.onNodeWithText("南校区").performClick()
        compose.onNodeWithText("至诚楼").performClick()

        compose.onNodeWithText("南校区 · 至诚楼 · 全部教室").assertIsDisplayed()
        compose.onNodeWithText("查看全部教室").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("查看「至诚楼」全部教室").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf("south", "zhicheng", "至诚楼", null), confirmed)
        }
    }

    @Test
    fun bottomConfirmAppliesSelectedRoom() {
        var confirmed: List<String?>? = null
        compose.setContent {
            HutbTimetableTheme {
                RoomPickerDialog(
                    campuses = campuses,
                    buildings = buildings,
                    classrooms = listOf(RoomOption("A201", "A201")),
                    initialCampusId = "south",
                    initialBuildingId = "zhicheng",
                    initialRoomCode = null,
                    buildingsLoading = false,
                    classroomsLoading = false,
                    onDismiss = {},
                    onPreviewCampus = {},
                    onPreviewBuilding = { _, _, _ -> },
                    onConfirm = { campusId, buildingId, buildingName, roomCode ->
                        confirmed = listOf(campusId, buildingId, buildingName, roomCode)
                    }
                )
            }
        }

        compose.onNodeWithText("A201").performScrollTo().performClick()
        compose.onNodeWithText("查看这间教室").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf("south", "zhicheng", "至诚楼", "A201"), confirmed)
        }
    }
}
