package com.feiqi.ui.media

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.model.Media
import com.feiqi.data.model.MediaFilter
import com.feiqi.data.model.MediaStatus
import com.feiqi.data.model.MediaType
import com.feiqi.ui.components.CategoryDropdown
import com.feiqi.ui.components.DatePickerField
import com.feiqi.ui.components.DeleteConfirmHost
import com.feiqi.ui.components.EmptyState
import com.feiqi.ui.components.rememberDeleteConfirm
import com.feiqi.ui.theme.Background
import com.feiqi.ui.theme.OnPrimary
import com.feiqi.ui.theme.OnSurfaceVariant
import com.feiqi.ui.theme.Outline
import com.feiqi.ui.theme.Primary
import com.feiqi.ui.theme.Surface
import com.feiqi.ui.theme.SurfaceVariant
import com.feiqi.ui.theme.Tertiary
import com.feiqi.utils.DateUtils
import java.time.LocalDate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

@Composable
fun MediaScreen(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val deleteConfirm = rememberDeleteConfirm()
    val deleteMediaText = stringResource(R.string.delete_media_confirm)
    var editingMedia by remember { mutableStateOf<Media?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Background,
        snackbarHost = { SnackbarHost(hostState = remember { SnackbarHostState() }) }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = stringResource(R.string.tab_media),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item(span = { GridItemSpan(2) }) {
                AddMediaForm(
                    onSubmit = { title, type, status, rating, date, note, cover ->
                        viewModel.addMedia(title, type, status, rating, date, note, cover)
                    }
                )
            }

            item(span = { GridItemSpan(2) }) {
                StatsRow(
                    thisYearCount = uiState.thisYearCount,
                    averageRating = uiState.averageRating,
                    favoriteType = uiState.favoriteType
                )
            }

            item(span = { GridItemSpan(2) }) {
                FootprintCard(
                    year = LocalDate.now().year,
                    distribution = uiState.starDistribution
                )
            }

            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(R.string.media_collection),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        MediaFilter.entries.forEach { option ->
                            val selected = option == uiState.filter
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setFilter(option) },
                                label = { Text(option.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary,
                                    selectedLabelColor = OnPrimary,
                                    containerColor = Surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }

            if (uiState.filteredItems.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    EmptyState(
                        title = stringResource(R.string.media_empty_title),
                        description = stringResource(R.string.media_empty_desc),
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(
                    items = uiState.filteredItems,
                    key = { it.id },
                    contentType = { "media" },
                    span = { GridItemSpan(1) }
                ) { media ->
                    MediaCard(
                        media = media,
                        onEdit = { editingMedia = media },
                        onDelete = {
                            deleteConfirm.request(deleteMediaText) {
                                viewModel.deleteMedia(media)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    DeleteConfirmHost(state = deleteConfirm)

    editingMedia?.let { media ->
        EditMediaDialog(
            media = media,
            onUpdate = { viewModel.updateMedia(it) },
            onDismiss = { editingMedia = null }
        )
    }
}

@Composable
private fun AddMediaForm(
    onSubmit: (
        title: String,
        typeLabel: String,
        statusLabel: String,
        rating: Float,
        date: LocalDate,
        note: String,
        coverUri: String?
    ) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.media_record),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            MediaFormBody(
                initialMedia = null,
                isAddMode = true,
                submitLabel = stringResource(R.string.add_to_media),
                onSubmit = onSubmit
            )
        }
    }
}

/**
 * 书影音「新增 / 编辑」共用表单主体。
 *
 * - [initialMedia] 为空表示新增（字段取默认值）；非空表示编辑（用其字段预填）。
 * - [isAddMode] 为 true 时提交后清空表单；为 false 时不清空（由调用方关闭弹窗）。
 * - [submitLabel] 按钮文案（新增用「加入我的书影音」，编辑用「保存」）。
 */
@Composable
private fun MediaFormBody(
    initialMedia: Media?,
    isAddMode: Boolean,
    submitLabel: String,
    onSubmit: (
        title: String,
        typeLabel: String,
        statusLabel: String,
        rating: Float,
        date: LocalDate,
        note: String,
        coverUri: String?
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialMedia?.title ?: "") }
    var type by remember { mutableStateOf(initialMedia?.type?.label ?: MediaType.MOVIE.label) }
    var status by remember { mutableStateOf(initialMedia?.status?.label ?: MediaStatus.WISH.label) }
    var rating by remember { mutableFloatStateOf(initialMedia?.rating ?: 0f) }
    var date by remember { mutableStateOf(initialMedia?.date ?: DateUtils.today()) }
    var note by remember { mutableStateOf(initialMedia?.note ?: "") }
    var coverUri by remember { mutableStateOf(initialMedia?.coverUri ?: "") }

    val context = LocalContext.current
    // 自行上传封面：从相册/文件选一张图，复制到 App 私有目录后存本地路径，不依赖任何外部图片库。
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: return@rememberLauncherForActivityResult
        val dest = File(dir, "media_${System.currentTimeMillis()}.jpg")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            }
        }
        if (dest.exists() && dest.length() > 0) coverUri = dest.absolutePath
    }

    val typeOptions = MediaType.entries.map { it.label }
    val statusOptions = MediaStatus.entries.map { it.label }

    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.media_name)) },
        placeholder = { Text(stringResource(R.string.media_name_hint)) },
        singleLine = true
    )
    Spacer(modifier = Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CategoryDropdown(
            options = typeOptions,
            selected = type,
            onSelected = { type = it },
            label = stringResource(R.string.media_type),
            modifier = Modifier.weight(1f)
        )
        CategoryDropdown(
            options = statusOptions,
            selected = status,
            onSelected = { status = it },
            label = stringResource(R.string.media_status),
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.media_rating),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
            StarRatingBar(
                rating = rating,
                onRatingChanged = { rating = it },
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        DatePickerField(
            date = date,
            onDateSelected = { date = it },
            label = stringResource(R.string.media_record_date),
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.media_short_review)) },
        placeholder = { Text(stringResource(R.string.media_review_hint)) },
        minLines = 2,
        maxLines = 3
    )
    Spacer(modifier = Modifier.height(12.dp))

    val currentType = MediaType.entries.firstOrNull { it.label == type } ?: MediaType.OTHER
    var showCoverPreview by remember { mutableStateOf(false) }
    val hasCover = coverUri.isNotBlank()

    // 预览区加高并改用 Fit：竖版海报/横版剧照都能在框内完整、按原比例显示，不再被裁切
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (hasCover) 240.dp else 120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
            .clickable {
                if (hasCover) showCoverPreview = true else coverPicker.launch("image/*")
            },
        contentAlignment = Alignment.Center
    ) {
        if (hasCover) {
            MediaCover(
                coverUri = coverUri,
                type = currentType,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = stringResource(R.string.tap_to_upload_cover),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
        }
        if (hasCover) {
            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { showCoverPreview = true }) {
                    Icon(
                        imageVector = Icons.Outlined.ZoomOutMap,
                        contentDescription = stringResource(R.string.view_full_cover),
                        tint = OnSurfaceVariant
                    )
                }
                IconButton(onClick = { coverUri = "" }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = OnSurfaceVariant
                    )
                }
            }
            // 已选图时，底部给一个明确的「重新选择」入口
            Text(
                text = stringResource(R.string.tap_to_change_cover),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .clickable { coverPicker.launch("image/*") }
            )
        }
    }

    if (showCoverPreview && hasCover) {
        CoverPreviewDialog(
            coverUri = coverUri,
            type = currentType,
            onDismiss = { showCoverPreview = false }
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = {
            if (title.isBlank()) {
                // 先传图再提交但未填名称：提示，但不清空用户已填内容（含已上传封面）
                Toast.makeText(context, R.string.media_name_required, Toast.LENGTH_SHORT).show()
                return@Button
            }
            onSubmit(title, type, status, rating, date, note, coverUri)
            if (isAddMode) {
                title = ""
                type = MediaType.MOVIE.label
                status = MediaStatus.WISH.label
                rating = 0f
                date = DateUtils.today()
                note = ""
                coverUri = ""
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            contentColor = OnPrimary
        )
    ) {
        Text(submitLabel)
    }
}

/**
 * 书影音编辑弹窗：复用 [MediaFormBody]，预填待编辑作品字段，
 * 保存时把表单结果映射回 [Media]（保留 id / createdAt），调用 [onUpdate]。
 */
@Composable
private fun EditMediaDialog(
    media: Media,
    onUpdate: (Media) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.edit),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                MediaFormBody(
                    initialMedia = media,
                    isAddMode = false,
                    submitLabel = stringResource(R.string.save),
                    onSubmit = { t, ty, st, r, d, n, c ->
                        val newType = MediaType.entries.find { it.label == ty } ?: media.type
                        val newStatus = MediaStatus.entries.find { it.label == st } ?: media.status
                        onUpdate(
                            media.copy(
                                title = t.trim(),
                                type = newType,
                                status = newStatus,
                                rating = r,
                                date = d,
                                note = n.trim(),
                                coverUri = c?.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun StarRatingBar(
    rating: Float,
    onRatingChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val starValue = index + 1
            val filled = index < rating
            IconButton(
                onClick = {
                    onRatingChanged(if (starValue == rating.toInt()) 0f else starValue.toFloat())
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "$starValue 星",
                    tint = if (filled) Tertiary else Outline,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun RatingStars(
    rating: Float,
    modifier: Modifier = Modifier,
    starSize: Int = 14
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            Icon(
                imageVector = if (index < rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (index < rating) Tertiary else Outline,
                modifier = Modifier.size(starSize.dp)
            )
        }
    }
}

@Composable
private fun StatsRow(
    thisYearCount: Int,
    averageRating: Float,
    favoriteType: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            label = stringResource(R.string.this_year_done),
            value = "${thisYearCount}部",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = stringResource(R.string.average_rating),
            value = "${"%.1f".format(averageRating)}★",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = stringResource(R.string.favorite_type),
            value = favoriteType,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun FootprintCard(
    year: Int,
    distribution: List<Int>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.my_footprint),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnPrimary
                )
                Text(
                    text = stringResource(R.string.year_footprint_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnPrimary.copy(alpha = 0.7f)
                )
            }
            Text(
                text = "$year，我的精神足迹",
                style = MaterialTheme.typography.headlineSmall,
                color = OnPrimary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            val maxCount = (distribution.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                distribution.forEachIndexed { index, count ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnPrimary.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val barHeight = (80 * count / maxCount).coerceAtLeast(4)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(OnPrimary.copy(alpha = 0.35f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${index + 1}星",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}


/**
 * 封面展示。
 *
 * - [contentScale] 默认 [ContentScale.Fit]，保证任意比例的封面都能**完整**显示（不裁切、不变形），
 *   列表缩略图等需要铺满的场景可显式传 [ContentScale.Crop]。
 * - 没有图片时按类型显示默认风格占位（淡色底 + 类型名）。
 * - 图片用内置 BitmapFactory 解码，不依赖任何外部图片库。
 */
@Composable
private fun MediaCover(
    coverUri: String?,
    type: MediaType,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    maxSizePx: Int = 1280
) {
    val bitmap = rememberMediaBitmap(coverUri, maxSizePx)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        val tint = coverTint(type)
        Box(
            modifier = modifier.background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = type.label,
                style = MaterialTheme.typography.headlineSmall,
                color = tint,
                maxLines = 1
            )
        }
    }
}

/**
 * 解码本地封面图。按 [maxSizePx] 做采样降采样，避免大图直接解码导致 OOM。
 */
@Composable
fun rememberMediaBitmap(path: String?, maxSizePx: Int = 1280): ImageBitmap? {
    var bitmap by remember(path, maxSizePx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path, maxSizePx) {
        bitmap = null
        if (!path.isNullOrBlank()) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { decodeSampledBitmap(path, maxSizePx) }.getOrNull()
            }
            bitmap = bmp?.asImageBitmap()
        }
    }
    return bitmap
}

/** 先读尺寸再按需降采样解码，长边不超过 maxSizePx。 */
private fun decodeSampledBitmap(path: String, maxSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val longest = max(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / sample > maxSizePx) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)
}

/**
 * 全屏封面预览：图片按原始比例完整显示（Fit），支持双指缩放与拖动查看细节。
 * 点击空白处或右上角关闭。
 */
@Composable
fun CoverPreviewDialog(
    coverUri: String,
    type: MediaType,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6000000))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            val bitmap = rememberMediaBitmap(coverUri, maxSizePx = 2048)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = min(max(scale * zoom, 1f), 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                )
            } else {
                Text(
                    text = type.label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White
                )
            }

            Text(
                text = stringResource(R.string.cover_preview_hint),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            )
        }
    }
}

private fun coverTint(type: MediaType): Color = when (type) {
    MediaType.MOVIE -> Color(0xFF6D8CC0)
    MediaType.TV -> Color(0xFFB07CC6)
    MediaType.BOOK -> Color(0xFF7FB069)
    MediaType.MUSIC -> Color(0xFFE08BB0)
    else -> Color(0xFFB0A08C)
}

@Composable
private fun MediaCard(
    media: Media,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        var showPreview by remember(media.id) { mutableStateOf(false) }
        val hasCover = !media.coverUri.isNullOrBlank()

        Column {
            // 封面填满卡片；点击图片即可放大查看（移除独立放大按钮）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(SurfaceVariant)
                    .then(
                        if (hasCover) Modifier.clickable { showPreview = true } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (hasCover) {
                    MediaCover(
                        coverUri = media.coverUri,
                        type = media.type,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    MediaCover(
                        coverUri = media.coverUri,
                        type = media.type,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (showPreview && hasCover) {
                CoverPreviewDialog(
                    coverUri = media.coverUri!!,
                    type = media.type,
                    onDismiss = { showPreview = false }
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.edit),
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${media.type.label} · ${media.status.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (media.rating > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    RatingStars(rating = media.rating)
                }
                if (media.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "\"${media.note}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
