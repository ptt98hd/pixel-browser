package app.pixel.browser.feature.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.data.home.HomeBackground
import app.pixel.browser.core.data.home.HomeConfig
import app.pixel.browser.core.data.home.HomeFiles
import app.pixel.browser.core.data.home.ShortcutEdit
import app.pixel.browser.core.ui.PixelIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.pow

/** Test tag ổn định của trang chủ (spec 010). */
object HomeTestTags {
    const val HOME_PAGE = "home_page"
    const val HOME_SEARCH = "home_search"
    const val SHORTCUT_ADD = "home_shortcut_add"
    const val MENU_EDIT = "shortcut_menu_edit"
    const val MENU_REMOVE = "shortcut_menu_remove"
    const val DIALOG = "shortcut_dialog"
    const val DIALOG_TITLE = "shortcut_title"
    const val DIALOG_URL = "shortcut_url"
    const val DIALOG_SAVE = "shortcut_save"
    const val DIALOG_CANCEL = "shortcut_cancel"
    const val DIALOG_ERROR = "shortcut_error"
    const val CUSTOMIZE = "home_customize"
    const val CUSTOMIZE_SHEET = "home_customize_sheet"
    const val BG_DYNAMIC = "home_bg_dynamic"
    const val BG_IMAGE = "home_bg_image"
    const val BG_DIM = "home_bg_dim"
    const val BG_IMAGE_LAYER = "home_bg_image_layer"
    const val BG_DIM_LAYER = "home_bg_dim_layer"

    fun shortcut(id: String): String = "home_shortcut_$id"

    fun bgColor(index: Int): String = "home_bg_color_$index"
}

/** Thuộc tính semantics để test đọc (spec 010 AC22). */
object HomeSemantics {
    /** Alpha của lớp làm tối trên ảnh nền. */
    val DimAlpha: SemanticsPropertyKey<Float> = SemanticsPropertyKey("DimAlpha")
}

/** Callback của [HomePage]. Mặc định no-op để test composable độc lập. */
data class HomeActions(
    val onOpenUrl: (String) -> Unit = {},
    val onSearchClick: () -> Unit = {},
    val onAddShortcut: (title: String, url: String) -> ShortcutEdit = { _, _ -> ShortcutEdit.Invalid },
    val onUpdateShortcut: (id: String, title: String, url: String) -> ShortcutEdit = { _, _, _ ->
        ShortcutEdit.Invalid
    },
    val onRemoveShortcut: (String) -> Unit = {},
    val onMoveShortcut: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onSetBackground: (HomeBackground) -> Unit = {},
    val onSetDim: (Float) -> Unit = {},
    /** Kết quả Photo Picker; null = người dùng hủy. */
    val onImagePicked: (String?) -> Unit = {},
)

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val WIDE_LAYOUT_DP = 600
private const val COMPACT_COLUMNS = 4
private const val WIDE_COLUMNS = 6

/** Độ tối tối thiểu khi vẽ ảnh nền (slider vẫn cho chọn 0..0.8 nhưng dưới sàn này nhìn như nhau). */
private const val MIN_IMAGE_DIM = 0.2f

/** Màu chữ/icon (đen hoặc trắng, ARGB) có độ tương phản WCAG cao hơn trên nền [argb] (spec 010 AC22). */
fun homeContentColor(argb: Int): Int {
    val l = relativeLuminance(argb)
    // Tương phản với trắng = 1.05 / (l + 0.05); với đen = (l + 0.05) / 0.05.
    val vsWhite = 1.05 / (l + 0.05)
    val vsBlack = (l + 0.05) / 0.05
    return if (vsBlack >= vsWhite) BLACK else WHITE
}

private fun relativeLuminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** Trang chủ native, stateless (spec 010 AC19–AC23). [config] null = đang đọc. */
@Composable
fun HomePage(
    config: HomeConfig?,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val background = config?.background ?: HomeBackground.Dynamic
    val contentColor =
        when (background) {
            HomeBackground.Dynamic -> MaterialTheme.colorScheme.onSurface
            is HomeBackground.Solid -> Color(homeContentColor(background.argb))
            is HomeBackground.Image -> Color.White
        }
    var customizeOpen by rememberSaveable { mutableStateOf(false) }
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    // Độ tối đang kéo trên slider: hiển thị trực tiếp, chỉ ghi config khi thả tay.
    var liveDim by remember { mutableStateOf<Float?>(null) }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            actions.onImagePicked(uri?.toString())
        }

    BoxWithConstraints(modifier.testTag(HomeTestTags.HOME_PAGE)) {
        val columns = if (maxWidth >= WIDE_LAYOUT_DP.dp) WIDE_COLUMNS else COMPACT_COLUMNS
        HomeBackgroundLayers(background, liveDim)
        Column(
            Modifier
                .fillMaxSize()
                // Hộp thoại là lớp phủ trong cùng cây: ẩn nội dung bên dưới khỏi trợ năng khi nó mở.
                .semantics { if (dialogOpen) hideFromAccessibility() }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                if (config != null) {
                    PixelIconButton(onClick = { customizeOpen = true }, Modifier.testTag(HomeTestTags.CUSTOMIZE)) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.home_customize),
                            tint = contentColor,
                        )
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
            SearchBox(contentColor, actions.onSearchClick)
            Spacer(Modifier.height(32.dp))
            if (config != null) {
                ShortcutGrid(
                    config = config,
                    columns = columns,
                    contentColor = contentColor,
                    labelShadow = background is HomeBackground.Image,
                    actions = actions,
                    onAdd = {
                        editingId = null
                        dialogOpen = true
                    },
                    onEdit = {
                        editingId = it
                        dialogOpen = true
                    },
                )
            }
        }
        if (config != null && dialogOpen) {
            val editing = editingId?.let { id -> config.shortcuts.firstOrNull { it.id == id } }
            ShortcutDialog(
                initialTitle = editing?.title.orEmpty(),
                initialUrl = editing?.url.orEmpty(),
                isEdit = editing != null,
                onSave = { title, url ->
                    if (editing != null) {
                        actions.onUpdateShortcut(editing.id, title, url)
                    } else {
                        actions.onAddShortcut(title, url)
                    }
                },
                onDismiss = { dialogOpen = false },
            )
        }
    }

    if (config != null && customizeOpen) {
        CustomizeSheet(
            background = config.background,
            liveDim = liveDim,
            onDimChange = { liveDim = it },
            onDimChangeFinished = {
                liveDim?.let(actions.onSetDim)
                liveDim = null
            },
            actions = actions,
            onPickImage = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = { customizeOpen = false },
        )
    }
}

@Composable
private fun BoxScope.HomeBackgroundLayers(
    background: HomeBackground,
    liveDim: Float?,
) {
    when (background) {
        HomeBackground.Dynamic -> {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
        }

        is HomeBackground.Solid -> {
            Box(Modifier.matchParentSize().background(Color(background.argb)))
        }

        is HomeBackground.Image -> {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
            ImageLayer(background.fileName)
            // Sàn độ tối: chữ trắng luôn đọc được kể cả trên ảnh sáng.
            val dim = max(liveDim ?: background.dim, MIN_IMAGE_DIM)
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = dim))
                    .testTag(HomeTestTags.BG_DIM_LAYER)
                    .semantics { this[HomeSemantics.DimAlpha] = dim },
            )
        }
    }
}

/** Ảnh nền đọc từ `filesDir/home/[fileName]` trên IO; chưa đọc được thì chỉ có lớp rỗng (nền theme bên dưới). */
@Composable
private fun BoxScope.ImageLayer(fileName: String) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, fileName) {
        value =
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(HomeFiles.image(context.filesDir, fileName).path)
            }
    }
    Box(Modifier.matchParentSize().testTag(HomeTestTags.BG_IMAGE_LAYER)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun SearchBox(
    contentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(contentColor.copy(alpha = 0.14f))
            .testTag(HomeTestTags.HOME_SEARCH)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = contentColor)
        Text(
            stringResource(R.string.home_search_placeholder),
            color = contentColor.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Nối [HomeViewModel] ↔ [HomePage] (spec 010). */
@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenUrl: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val config by viewModel.config.collectAsState()
    val actions =
        remember(viewModel, onOpenUrl, onSearchClick) {
            HomeActions(
                onOpenUrl = onOpenUrl,
                onSearchClick = onSearchClick,
                onAddShortcut = viewModel::addShortcut,
                onUpdateShortcut = viewModel::updateShortcut,
                onRemoveShortcut = viewModel::removeShortcut,
                onMoveShortcut = viewModel::moveShortcut,
                onSetBackground = viewModel::setBackground,
                onSetDim = viewModel::setDim,
                onImagePicked = viewModel::onImagePicked,
            )
        }
    val snackbar = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.home_message_image_failed)
    LaunchedEffect(viewModel) {
        // Xóa message khi collector đã nhận (không làm đổi key của effect); snackbar chạy tuần tự sau đó.
        viewModel.message.filterNotNull().collect {
            viewModel.onMessageShown()
            snackbar.showSnackbar(failedText)
        }
    }
    Box(Modifier.fillMaxSize()) {
        HomePage(config, actions, Modifier.fillMaxSize())
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
