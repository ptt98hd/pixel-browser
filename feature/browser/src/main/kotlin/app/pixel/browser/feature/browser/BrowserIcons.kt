package app.pixel.browser.feature.browser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icon "history" (Material Symbols, Apache-2.0). Không có trong material-icons-core. */
internal val HistoryIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "History",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData =
                addPathNodes(
                    "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7" +
                        " -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21" +
                        "c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z",
                ),
            fill = SolidColor(Color.Black),
        ).build()
}

/** Icon "settings" (Material Icons, Apache-2.0) – bánh răng. Không có trong material-icons-core. */
internal val SettingsIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData =
                addPathNodes(
                    "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58" +
                        "c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96" +
                        "c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84" +
                        "c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33" +
                        "c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58" +
                        "C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61" +
                        "l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54" +
                        "c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54" +
                        "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32" +
                        "c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94z" +
                        "M12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
                ),
            fill = SolidColor(Color.Black),
        ).build()
}

/** Icon "desktop_windows" (Material Icons, Apache-2.0) – chỉ báo trang máy tính. Không có trong material-icons-core. */
internal val DesktopIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "DesktopWindows",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData =
                addPathNodes(
                    "M21,2L3,2c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h7l-2,3v1h8v-1l-2,-3h7c1.1,0 2,-0.9 2,-2L23,4" +
                        "c0,-1.1 -0.9,-2 -2,-2zM21,16L3,16L3,4h18v12z",
                ),
            fill = SolidColor(Color.Black),
        ).build()
}

/** Icon "tab ẩn danh": mũ và hai tròng kính (vẽ riêng cho Pixel). */
internal val PrivateIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "Private",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData =
                addPathNodes(
                    "M12,3C9.3,3 7.4,4.2 7.4,4.2L6,9h12l-1.4,-4.8C16.6,4.2 14.7,3 12,3z" +
                        "M12,11c-4.4,0 -8,0.9 -8,2v1h16v-1C20,11.9 16.4,11 12,11z" +
                        "M8,15a2,2 0 1,0 0,4a2,2 0 1,0 0,-4z" +
                        "M16,15a2,2 0 1,0 0,4a2,2 0 1,0 0,-4z",
                ),
            fill = SolidColor(Color.Black),
        ).build()
}
