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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.ListRow
import io.github.romulofer.foxhg.app.ui.SearchBox

/** "025  PIKACHU" -> "PIKACHU". Labels are built as an id, two spaces, a name. */
internal fun nameOf(label: String): String = label.substringAfter("  ", label).trim()

/** Searchable list of records on the left. */
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
    Column(modifier.width(width).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchBox(search, onSearch, placeholder, Modifier.fillMaxWidth())
        val state = rememberLazyListState()
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
        if (rows.isEmpty()) Text("No match", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
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
