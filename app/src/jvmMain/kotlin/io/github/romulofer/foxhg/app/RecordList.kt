package io.github.romulofer.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.ListRow
import io.github.romulofer.foxhg.app.ui.SearchBox

/** "025  PIKACHU" -> "PIKACHU". Labels are built as an id, two spaces, a name. */
internal fun nameOf(label: String): String = label.substringAfter("  ", label).trim()

/** Where Ctrl+F goes: the search box of the list on screen. Set by [ListPane], used by the window's key handler. */
object SearchFocus {
    internal var requester: FocusRequester? = null

    /** Focuses the visible list's search box; false when no list is on screen. */
    fun request(): Boolean = runCatching { requester?.requestFocus() }.isSuccess && requester != null
}

/** Row to select for Up/Down/Home/End/PageUp/PageDown from [current], or null when the key does not move. */
internal fun stepSelection(rows: List<Int>, current: Int, key: Key): Int? {
    if (rows.isEmpty()) return null
    val at = rows.indexOf(current)
    val target = when (key) {
        Key.DirectionDown -> if (at < 0) 0 else at + 1
        Key.DirectionUp -> if (at < 0) rows.lastIndex else at - 1
        Key.PageDown -> if (at < 0) 0 else at + 10
        Key.PageUp -> if (at < 0) rows.lastIndex else at - 10
        else -> return null
    }
    return rows[target.coerceIn(0, rows.lastIndex)]
}

/** Searchable list of records on the left. Up/Down/PageUp/PageDown in the list or its search box move the selection. */
@Composable
fun ListPane(
    rows: List<Int>,
    selected: Int,
    search: String,
    onSearch: (String) -> Unit,
    placeholder: String,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
    isEdited: (Int) -> Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 272.dp,
    flagged: (Int) -> Boolean = { false },
    detail: (Int) -> String? = { null },
    leading: (@Composable (Int) -> Unit)? = null,
) {
    val state = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    DisposableEffect(searchFocus) {
        SearchFocus.requester = searchFocus
        onDispose { if (SearchFocus.requester === searchFocus) SearchFocus.requester = null }
    }
    val currentRows by rememberUpdatedState(rows)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(selected, rows) {
        val at = rows.indexOf(selected)
        val visible = state.layoutInfo.visibleItemsInfo
        if (at >= 0 && visible.isNotEmpty() && (at <= visible.first().index || at >= visible.last().index)) state.animateScrollToItem(maxOf(0, at - 2))
    }
    Column(
        modifier.width(width).fillMaxHeight().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.isCtrlPressed || e.isAltPressed || e.isMetaPressed) false
            else stepSelection(currentRows, currentSelected, e.key)?.let { currentOnSelect(it); true } ?: false
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchBox(search, onSearch, placeholder, Modifier.fillMaxWidth(), searchFocus)
        if (rows.isEmpty()) {
            Text("No match", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                items(rows) { index ->
                    ListRow(
                        id = index.toString(),
                        text = nameOf(label(index)),
                        selected = index == selected,
                        onClick = { onSelect(index) },
                        detail = detail(index),
                        edited = isEdited(index),
                        flagged = flagged(index),
                        leading = leading?.let { l -> { l(index) } },
                    )
                }
            }
        }
    }
}

/** The scrolling, width-limited area that holds a record's cards. */
@Composable
fun DetailPane(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(start = 20.dp, end = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

/** Searchable list on the left, [detail] for the selected record on the right. Shows the load error instead if any. */
@Composable
fun <T> RecordListDetail(
    model: RecordEditorModel<T>,
    modifier: Modifier = Modifier,
    flagged: (Int) -> Boolean = { false },
    rowDetail: (Int) -> String? = { null },
    onSelect: (Int) -> Unit = { },
    leading: (@Composable (Int) -> Unit)? = null,
    detail: @Composable (T) -> Unit,
) {
    model.loadError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    Row(modifier.fillMaxSize()) {
        ListPane(
            rows = model.rows(),
            selected = model.selectedIndex,
            search = model.search,
            onSearch = { model.search = it },
            placeholder = "Search…",
            label = model::label,
            onSelect = { model.select(it); onSelect(it) },
            isEdited = model::isEdited,
            flagged = flagged,
            detail = rowDetail,
            leading = leading,
        )
        model.selected?.let { record -> DetailPane { detail(record) } }
    }
}
