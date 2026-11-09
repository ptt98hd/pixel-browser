package app.pixel.browser.feature.bookmarks

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icon Material (Apache-2.0) không có trong `material-icons-core`. */
private fun icon(
    name: String,
    path: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

internal val FolderIcon: ImageVector by lazy {
    icon(
        "Folder",
        "M10,4H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z",
    )
}

internal val NewFolderIcon: ImageVector by lazy {
    icon(
        "CreateNewFolder",
        "M20,6h-8l-2,-2H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8C22,6.9 21.1,6 20,6z" +
            "M19,14h-3v3h-2v-3h-3v-2h3V9h2v3h3V14z",
    )
}

internal val ImportIcon: ImageVector by lazy { icon("FileDownload", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }

internal val ExportIcon: ImageVector by lazy { icon("FileUpload", "M9,16h6v-6h4l-7,-7 -7,7h4zM5,18h14v2H5z") }
