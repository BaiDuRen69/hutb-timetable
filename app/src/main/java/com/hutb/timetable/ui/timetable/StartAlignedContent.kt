package com.hutb.timetable.ui.timetable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/**
 * 让超宽内容从容器左边缘开始布局。
 *
 * Row/Box 在子项宽于自身时会压缩或居中子项，横向表格会因此从周三/周四开始显示。
 * 这里用无界宽度测量子项，再按容器宽度布局，并把子项固定放在 (0,0)，
 * 超出视口的部分由父级 clipToBounds 裁剪。
 */
@Composable
internal fun StartAlignedContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        require(measurables.size == 1) {
            "StartAlignedContent 只接受一个子项，当前为 ${measurables.size} 个"
        }
        val placeable = measurables.first().measure(
            constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        )
        layout(
            width = constraints.constrainWidth(placeable.width),
            height = constraints.constrainHeight(placeable.height)
        ) {
            placeable.place(0, 0)
        }
    }
}
