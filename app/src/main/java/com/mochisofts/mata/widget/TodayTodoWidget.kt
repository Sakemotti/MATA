package com.mochisofts.mata.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.Button
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mochisofts.mata.R
import com.mochisofts.mata.app.MainActivity
import com.mochisofts.mata.core.common.ValidationException
import com.mochisofts.mata.data.local.WidgetInstanceStateDao
import com.mochisofts.mata.data.local.WidgetInstanceStateEntity
import com.mochisofts.mata.data.widget.ERROR_COMPLETE
import com.mochisofts.mata.data.widget.LOAD_ACTION_ERROR
import com.mochisofts.mata.data.widget.LOAD_ERROR
import com.mochisofts.mata.data.widget.LOAD_LOADING
import com.mochisofts.mata.data.widget.LOAD_STALE
import com.mochisofts.mata.data.widget.WidgetRefreshAlarmScheduler
import com.mochisofts.mata.data.widget.WidgetRefreshCoordinator
import com.mochisofts.mata.data.widget.WidgetSnapshotJson
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.data.widget.widgetEntryPoint
import com.mochisofts.mata.domain.model.WidgetCategoryGroup
import com.mochisofts.mata.domain.model.WidgetDisplayModel
import com.mochisofts.mata.domain.model.WidgetTodoItem
import com.mochisofts.mata.domain.repository.TodoRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TodayTodoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = RESPONSIVE_SIZE_MODE
    override val previewSizeMode: PreviewSizeMode = RESPONSIVE_SIZE_MODE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val stateDao = widgetEntryPoint(context).widgetStateDao()
        val initialState = stateDao.find(appWidgetId)
        val stateUpdates = stateDao.observe(appWidgetId)
        provideContent {
            val state by stateUpdates.collectAsState(initialState)
            val model = state?.snapshotJson?.let { value ->
                runCatching { WidgetSnapshotJson.decode(value) }.getOrNull()
            }?.takeIf { it.snapshotVersion == WidgetDisplayModel.CURRENT_VERSION }
            val lastUpdated = state?.lastSuccessAt?.let { timestamp ->
                val time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
                context.getString(R.string.widget_last_updated_format, TIME_FORMAT.format(time))
            }
            GlanceTheme {
                TodayTodoWidgetContent(
                    appWidgetId = appWidgetId,
                    state = state,
                    model = model,
                    lastUpdated = lastUpdated,
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val now = System.currentTimeMillis()
        val date = LocalDate.now().toString()
        val model = WidgetDisplayModel(
            generatedAt = now,
            calendarDate = date,
            totalCount = 2,
            groups = listOf(
                WidgetCategoryGroup(
                    categoryId = "preview",
                    categoryName = context.getString(R.string.widget_preview_category),
                    colorIndex = 4,
                    iconName = "Home",
                    sortOrder = 0,
                    logicalDate = date,
                    logicalDateLabel = null,
                    items = listOf(
                        WidgetTodoItem(
                            todoId = "preview-one",
                            definitionRevision = 1,
                            title = context.getString(R.string.widget_preview_todo_one),
                            logicalDate = date,
                            deadlineAt = now + 60 * 60 * 1_000,
                            deadlineLabel = context.getString(R.string.widget_preview_deadline_one),
                            overdue = false,
                        ),
                        WidgetTodoItem(
                            todoId = "preview-two",
                            definitionRevision = 1,
                            title = context.getString(R.string.widget_preview_todo_two),
                            logicalDate = date,
                            deadlineAt = now + 12 * 60 * 60 * 1_000,
                            deadlineLabel = context.getString(R.string.widget_preview_deadline_two),
                            overdue = false,
                        ),
                    ),
                ),
            ),
            holidayDataProvisional = false,
            nextRefreshAt = now + 60 * 60 * 1_000,
        )
        provideContent {
            GlanceTheme {
                TodayTodoWidgetContent(
                    appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
                    state = null,
                    model = model,
                    lastUpdated = null,
                )
            }
        }
    }

    companion object {
        val COMPACT_SIZE = DpSize(110.dp, 110.dp)
        val STANDARD_SIZE = DpSize(250.dp, 180.dp)
        val WIDE_SIZE = DpSize(320.dp, 110.dp)
        val TALL_SIZE = DpSize(110.dp, 280.dp)
        val EXPANDED_SIZE = DpSize(320.dp, 280.dp)
        private val RESPONSIVE_SIZE_MODE = SizeMode.Responsive(
            setOf(COMPACT_SIZE, STANDARD_SIZE, WIDE_SIZE, TALL_SIZE, EXPANDED_SIZE),
        )
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm")
    }
}

@Composable
private fun TodayTodoWidgetContent(
    appWidgetId: Int,
    state: WidgetInstanceStateEntity?,
    model: WidgetDisplayModel?,
    lastUpdated: String?,
) {
    val context = LocalContext.current
    val compact = LocalSize.current.width < 180.dp
    val padding = if (compact) 8.dp else 14.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.background)
            .padding(padding),
    ) {
        when {
            model == null && (state?.loadState == LOAD_ERROR || state?.loadState == LOAD_STALE ||
                state?.snapshotJson != null
            ) -> WidgetErrorContent(context)
            model == null -> WidgetLoadingContent(context)
            else -> WidgetModelContent(
                context = context,
                appWidgetId = appWidgetId,
                state = state,
                model = model,
                compact = compact,
                lastUpdated = lastUpdated,
            )
        }
    }
}

@Composable
private fun WidgetModelContent(
    context: Context,
    appWidgetId: Int,
    state: WidgetInstanceStateEntity?,
    model: WidgetDisplayModel,
    compact: Boolean,
    lastUpdated: String?,
) {
    val headerIntent = todoListIntent(context, model.calendarDate, MainActivity.WIDGET_MODE_DATE, null)
    Column(GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(headerIntent)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.widget_header),
                style = TextStyle(
                    color = WidgetColors.onSurface,
                    fontSize = if (compact) 16.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
            Text(
                text = context.getString(R.string.widget_count_format, model.totalCount),
                style = TextStyle(color = WidgetColors.primary, fontSize = 13.sp),
            )
        }

        if (state?.loadState == LOAD_ACTION_ERROR) {
            StatusText(context.getString(R.string.widget_complete_failed), WidgetColors.error)
        } else if (state?.loadState == LOAD_STALE) {
            StatusText(context.getString(R.string.widget_stale), WidgetColors.error)
            lastUpdated?.let { StatusText(it, WidgetColors.onSurfaceVariant) }
        }
        if (model.holidayDataProvisional) {
            StatusText(context.getString(R.string.widget_holiday_provisional), WidgetColors.onSurfaceVariant)
        }
        val undoOperationId = state?.undoOperationId
        if (undoOperationId != null && (state.undoExpiresAt ?: 0) > System.currentTimeMillis()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(
                        R.string.widget_completed_format,
                        state.undoTodoTitle.orEmpty(),
                    ),
                    style = TextStyle(color = WidgetColors.onSurface, fontSize = 12.sp),
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1,
                )
                Button(
                    text = context.getString(R.string.widget_undo),
                    onClick = actionRunCallback<UndoWidgetCompletionAction>(
                        actionParametersOf(WidgetActionKeys.appWidgetId to appWidgetId),
                    ),
                )
            }
        }

        if (model.groups.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity(headerIntent)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = context.getString(R.string.widget_empty),
                    style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 14.sp),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                model.groups.forEach { group ->
                    item {
                        WidgetCategoryHeader(context, group, compact)
                    }
                    group.items.forEach { item ->
                        item {
                            WidgetTodoRow(context, appWidgetId, model.snapshotVersion, item, compact)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCategoryHeader(
    context: Context,
    group: WidgetCategoryGroup,
    compact: Boolean,
) {
    val intent = todoListIntent(
        context,
        group.logicalDate,
        MainActivity.WIDGET_MODE_CATEGORY,
        group.categoryId ?: MainActivity.WIDGET_UNCATEGORIZED_KEY,
    )
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(intent))
            .padding(top = if (compact) 5.dp else 9.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(categoryIconResource(group.iconName)),
            contentDescription = group.categoryName,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(categoryColor(group.colorIndex)),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = group.categoryName,
            style = TextStyle(
                color = categoryColor(group.colorIndex),
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )
        group.logicalDateLabel?.let { label ->
            Text(label, style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 11.sp))
        }
    }
}

@Composable
private fun WidgetTodoRow(
    context: Context,
    appWidgetId: Int,
    snapshotVersion: Int,
    item: WidgetTodoItem,
    compact: Boolean,
) {
    val actionIntent = widgetTodoActionIntent(
        context = context,
        request = WidgetTodoActionRequest(
            todoId = item.todoId,
            logicalDate = LocalDate.parse(item.logicalDate),
            expectedRevision = item.definitionRevision,
            appWidgetId = appWidgetId,
            snapshotVersion = snapshotVersion,
        ),
    )
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(actionIntent))
            .padding(
                horizontal = if (compact) 4.dp else 8.dp,
                vertical = if (compact) 10.dp else 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight(),
        ) {
            Text(
                text = item.title,
                style = TextStyle(color = WidgetColors.onSurface, fontSize = if (compact) 13.sp else 14.sp),
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.deadlineLabel,
                    style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 11.sp),
                )
                if (item.overdue) {
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = context.getString(R.string.widget_overdue),
                        style = TextStyle(
                            color = WidgetColors.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                if (item.completedCount != null && item.requiredCount != null) {
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = context.getString(
                            R.string.widget_progress_format,
                            item.completedCount,
                            item.requiredCount,
                        ),
                        style = TextStyle(color = WidgetColors.onSurfaceVariant, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetLoadingContent(context: Context) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(context.getString(R.string.widget_loading), style = TextStyle(color = WidgetColors.onSurface))
    }
}

@Composable
private fun WidgetErrorContent(context: Context) {
    val intent = todoListIntent(context, LocalDate.now().toString(), MainActivity.WIDGET_MODE_DATE, null)
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(intent)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(context.getString(R.string.widget_error), style = TextStyle(color = WidgetColors.error))
        Spacer(GlanceModifier.height(6.dp))
        Text(context.getString(R.string.widget_open_app), style = TextStyle(color = WidgetColors.primary))
    }
}

@Composable
private fun StatusText(text: String, color: ColorProvider) {
    Text(
        text = text,
        style = TextStyle(color = color, fontSize = 11.sp),
        modifier = GlanceModifier.fillMaxWidth().padding(top = 3.dp),
        maxLines = 2,
    )
}

private fun todoListIntent(
    context: Context,
    selectedDate: String,
    mode: String,
    selectedCategoryKey: String?,
    todoId: String? = null,
): Intent = Intent(context, MainActivity::class.java).apply {
    action = MainActivity.ACTION_OPEN_WIDGET
    putExtra(MainActivity.EXTRA_WIDGET_DATE, selectedDate)
    putExtra(MainActivity.EXTRA_WIDGET_MODE, mode)
    selectedCategoryKey?.let { putExtra(MainActivity.EXTRA_WIDGET_CATEGORY_KEY, it) }
    todoId?.let { putExtra(MainActivity.EXTRA_TODO_ID, it) }
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
}

object WidgetActionKeys {
    val appWidgetId = ActionParameters.Key<Int>("app_widget_id")
}

class UndoWidgetCompletionAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = parameters[WidgetActionKeys.appWidgetId] ?: return
        if (runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrNull() !=
            appWidgetId
        ) return
        val dependencies = widgetActionEntryPoint(context)
        val state = dependencies.widgetStateDao().find(appWidgetId) ?: return
        val operationId = state.undoOperationId ?: return
        val now = dependencies.clock().millis()
        if ((state.undoExpiresAt ?: 0) <= now) {
            dependencies.widgetStateDao().upsert(state.clearUndo(now))
            TodayTodoWidget().update(context, glanceId)
            return
        }
        val result = dependencies.todoRepository().undoCompletion(operationId)
        if (result.isSuccess || result.exceptionOrNull() is ValidationException) {
            dependencies.widgetStateDao().upsert(state.clearUndo(now))
            dependencies.widgetUpdater().cancelUndoExpiry(appWidgetId)
            dependencies.refreshCoordinator().refreshAll(appWidgetId)
        } else {
            markActionFailure(context, glanceId, appWidgetId, dependencies)
        }
    }
}

private suspend fun markActionFailure(
    context: Context,
    glanceId: GlanceId,
    appWidgetId: Int,
    dependencies: WidgetActionEntryPoint,
) {
    val now = dependencies.clock().millis()
    val previous = dependencies.widgetStateDao().find(appWidgetId)
    dependencies.widgetStateDao().upsert(
        (previous ?: emptyWidgetState(appWidgetId, now)).copy(
            loadState = LOAD_ACTION_ERROR,
            errorCode = ERROR_COMPLETE,
            lastFailureAt = now,
            updatedAt = now,
        ),
    )
    runCatching { TodayTodoWidget().update(context, glanceId) }
}

private fun emptyWidgetState(appWidgetId: Int, now: Long) = WidgetInstanceStateEntity(
    appWidgetId = appWidgetId,
    snapshotVersion = WidgetDisplayModel.CURRENT_VERSION,
    snapshotJson = null,
    lastSuccessAt = null,
    loadState = LOAD_LOADING,
    errorCode = null,
    lastFailureAt = null,
    undoOperationId = null,
    undoTodoTitle = null,
    undoExpiresAt = null,
    nextRefreshAt = null,
    updatedAt = now,
)

private fun WidgetInstanceStateEntity.clearUndo(now: Long) = copy(
    undoOperationId = null,
    undoTodoTitle = null,
    undoExpiresAt = null,
    updatedAt = now,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetActionEntryPoint {
    fun todoRepository(): TodoRepository
    fun widgetStateDao(): WidgetInstanceStateDao
    fun refreshCoordinator(): WidgetRefreshCoordinator
    fun widgetUpdater(): WidgetUpdater
    fun clock(): Clock
}

private fun widgetActionEntryPoint(context: Context): WidgetActionEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetActionEntryPoint::class.java)

class TodayTodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTodoWidget()
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val updater = widgetEntryPoint(context).widgetUpdater()
        updater.startPeriodic()
        updater.requestUpdate()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val updater = widgetEntryPoint(context).widgetUpdater()
        updater.startPeriodic()
        updater.requestUpdate()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val dependencies = widgetEntryPoint(context)
                appWidgetIds.forEach { appWidgetId ->
                    dependencies.widgetStateDao().delete(appWidgetId)
                    dependencies.widgetUpdater().cancelUndoExpiry(appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val dependencies = widgetReceiverEntryPoint(context)
                dependencies.widgetUpdater().stopPeriodic()
                dependencies.alarmScheduler().cancel()
                dependencies.widgetStateDao().deleteAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetReceiverEntryPoint {
    fun widgetUpdater(): WidgetUpdater
    fun alarmScheduler(): WidgetRefreshAlarmScheduler
    fun widgetStateDao(): WidgetInstanceStateDao
}

private fun widgetReceiverEntryPoint(context: Context): WidgetReceiverEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetReceiverEntryPoint::class.java)

private fun categoryColor(index: Int): ColorProvider {
    val safeIndex = index.coerceIn(LIGHT_CATEGORY_COLORS.indices)
    return DayNightColorProvider(
        Color(LIGHT_CATEGORY_COLORS[safeIndex]),
        Color(DARK_CATEGORY_COLORS[safeIndex]),
    )
}

private fun categoryIconResource(iconName: String): Int = when (iconName) {
    "Home" -> R.drawable.ic_widget_home
    "ShoppingCart" -> R.drawable.ic_widget_shopping_cart
    "Restaurant" -> R.drawable.ic_widget_restaurant
    "Favorite" -> R.drawable.ic_widget_favorite
    "FitnessCenter" -> R.drawable.ic_widget_fitness
    "DirectionsRun" -> R.drawable.ic_widget_running
    "School" -> R.drawable.ic_widget_school
    "MenuBook" -> R.drawable.ic_widget_book
    "Work" -> R.drawable.ic_widget_work
    "SportsEsports" -> R.drawable.ic_widget_game
    "Event" -> R.drawable.ic_widget_event
    "Pets" -> R.drawable.ic_widget_pets
    else -> R.drawable.ic_widget_category
}

private object WidgetColors {
    val background = DayNightColorProvider(Color(0xFFFDFDF5), Color(0xFF1A1C18))
    val onSurface = DayNightColorProvider(Color(0xFF1A1C18), Color(0xFFE3E3DC))
    val onSurfaceVariant = DayNightColorProvider(Color(0xFF43483F), Color(0xFFC3C8BC))
    val primary = DayNightColorProvider(Color(0xFF386A20), Color(0xFF9CD67D))
    val error = DayNightColorProvider(Color(0xFFBA1A1A), Color(0xFFFFB4AB))
}

private val LIGHT_CATEGORY_COLORS = longArrayOf(
    0xFFC62828, 0xFFAD1457, 0xFF6A1B9A, 0xFF283593,
    0xFF1565C0, 0xFF0277BD, 0xFF00838F, 0xFF00796B,
    0xFF2E7D32, 0xFF558B2F, 0xFF827717, 0xFFF9A825,
    0xFFEF6C00, 0xFFD84315, 0xFF5D4037, 0xFF546E7A,
)

private val DARK_CATEGORY_COLORS = longArrayOf(
    0xFFEF9A9A, 0xFFF48FB1, 0xFFCE93D8, 0xFF9FA8DA,
    0xFF90CAF9, 0xFF81D4FA, 0xFF80DEEA, 0xFF80CBC4,
    0xFFA5D6A7, 0xFFC5E1A5, 0xFFE6EE9C, 0xFFFFF59D,
    0xFFFFCC80, 0xFFFFAB91, 0xFFBCAAA4, 0xFFB0BEC5,
)
