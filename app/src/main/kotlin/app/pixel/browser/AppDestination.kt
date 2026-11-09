package app.pixel.browser

/** Đích cấp app: `Browser` là gốc, các đích khác là lớp phủ toàn màn hình trên trình duyệt (spec 012; 013/014 thêm). */
enum class AppDestination { Browser, History, Bookmarks, Settings }
