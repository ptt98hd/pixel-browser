package app.pixel.browser.core.engine

/** Kết quả tìm trong trang (spec 015 B04). [activeIndex] 0-based, vô nghĩa khi [total] == 0. */
data class FindState(
    val query: String = "",
    val activeIndex: Int = 0,
    val total: Int = 0,
    /** false trong lúc đang đếm. */
    val isDone: Boolean = true,
)

/** Độ dài tối đa của truy vấn tìm trong trang; truy vấn dài hơn bị cắt. */
const val MAX_FIND_QUERY = 256
