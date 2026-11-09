package app.pixel.browser.core.data.home

import java.io.File

/** Vị trí file của trang chủ dưới `filesDir` (spec 010): cấu hình và ảnh nền chung một thư mục. */
object HomeFiles {
    const val DIR_NAME = "home"
    const val CONFIG_NAME = "config.json"

    fun dir(filesDir: File): File = File(filesDir, DIR_NAME)

    fun config(filesDir: File): File = File(dir(filesDir), CONFIG_NAME)

    fun image(
        filesDir: File,
        fileName: String,
    ): File = File(dir(filesDir), fileName)
}
