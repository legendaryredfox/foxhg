package io.github.romulofer.foxhg.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Small caption above a field. Turns into the error colour and shows [hint] when [invalid]. */
@Composable
private fun FieldLabel(label: String, invalid: Boolean = false, hint: String? = null) {
    Text(
        if (invalid && hint != null) "$label  ·  $hint" else label,
        style = MaterialTheme.typography.labelSmall,
        color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun fieldModifier(invalid: Boolean, hovered: Boolean = false): Modifier {
    val shape = MaterialTheme.shapes.small
    val border = when {
        invalid -> MaterialTheme.colorScheme.error
        hovered -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.outline
    }
    return Modifier.height(Fox.fieldHeight).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, border, shape)
}

/** A compact integer field. Typing is free; the value is applied only when it parses and lies in [range]. */
@Composable
fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier.width(96.dp),
    /** Name for tests to find the field by (`number:<tag>`); the label by default. */
    tag: String = label,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val invalid = text.toIntOrNull()?.let { it !in range } ?: true
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (label.isNotEmpty() || invalid) FieldLabel(label, invalid, "${range.first}–${range.last}")
        BasicTextField(
            value = text,
            onValueChange = { new ->
                val signed = range.first < 0
                text = new.filterIndexed { i, c -> c.isDigit() || (signed && i == 0 && c == '-') }
                    .take(range.last.toString().length + if (signed) 1 else 0)
                text.toIntOrNull()?.takeIf { it in range }?.let(onChange)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            decorationBox = { inner ->
                Box(fieldModifier(invalid).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) { inner() }
            },
            modifier = Modifier.fillMaxWidth().testTag("number:$tag"),
        )
    }
}

/** Pick an id by name from [options] (index = id) in a searchable popup. Ids beyond the list show as `#id`. */
@Composable
fun PickerField(
    label: String,
    selected: Int,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier.width(200.dp),
    /** A picture or swatch drawn in front of an option (and of the selected one in the field). */
    leading: (@Composable (Int) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        FieldLabel(label)
        Box {
            Row(
                fieldModifier(false, hovered || open).hoverable(source).testTag("picker:$label").clickable { open = true }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.let { it(selected); Spacer(Modifier.width(6.dp)) }
                Text(
                    options.getOrNull(selected) ?: "#$selected",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("%d".format(selected), style = Fox.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  ▾", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) PickerPopup(selected, options, onSelect = { onSelect(it); open = false }, onDismiss = { open = false }, leading = leading)
        }
    }
}

private class BelowAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.left.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val below = anchorBounds.bottom + 4
        val y = if (below + popupContentSize.height <= windowSize.height) below else maxOf(0, anchorBounds.top - popupContentSize.height - 4)
        return IntOffset(x, y)
    }
}

@Composable
private fun PickerPopup(selected: Int, options: List<String>, onSelect: (Int) -> Unit, onDismiss: () -> Unit, leading: (@Composable (Int) -> Unit)? = null) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query, options) {
        val q = query.trim()
        if (q.isEmpty()) options.indices.toList()
        else options.indices.filter { options[it].contains(q, ignoreCase = true) || it.toString() == q }
    }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, (matches.indexOf(selected)) - 3))
    LaunchedEffect(Unit) { focus.requestFocus() }
    Popup(popupPositionProvider = BelowAnchor(), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.width(300.dp).onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false
                else when (e.key) {
                    Key.Escape -> { onDismiss(); true }
                    Key.Enter -> { matches.firstOrNull()?.let(onSelect); true }
                    else -> false
                }
            },
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchBox(query, { query = it }, "Search…", Modifier.fillMaxWidth().focusRequester(focus))
                if (matches.isEmpty()) {
                    Text("No match", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp), state = list) {
                        itemsIndexed(matches) { _, id ->
                            ListRow(
                                id = id.toString(),
                                text = options[id],
                                selected = id == selected,
                                onClick = { onSelect(id) },
                                leading = leading?.let { l -> { l(id) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Compact search input with a placeholder and a clear button. */
@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Row(fieldModifier(false).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Text("✕", Modifier.clickable { onChange("") }.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        modifier = modifier.testTag("search:$placeholder"),
    )
}

/** One row of a list: id in monospace, a name, optional trailing detail, selection highlight, edited dot. */
@Composable
fun ListRow(
    id: String,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    detail: String? = null,
    edited: Boolean = false,
    flagged: Boolean = false,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val bg = when {
        selected -> scheme.primaryContainer
        hovered -> scheme.surfaceVariant
        else -> Color.Transparent
    }
    Row(
        modifier.fillMaxWidth().height(30.dp).background(bg, MaterialTheme.shapes.small).hoverable(source).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(id.padStart(3, '0'), style = Fox.mono, color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant, modifier = Modifier.width(30.dp))
        leading?.invoke()
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
        if (flagged) Text("≠", style = MaterialTheme.typography.labelMedium, color = scheme.tertiary)
        if (edited) Box(Modifier.width(7.dp).height(7.dp).background(scheme.primary, MaterialTheme.shapes.extraSmall))
    }
}

/** A titled group of fields on a raised surface. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                actions?.invoke(this)
            }
            content()
        }
    }
}

/** Fields that wrap onto the next line when the window is narrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.Bottom,
    ) { content() }
}

/** Small pill, used for the game name, counts and "soon". */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.background(if (emphasis) scheme.primaryContainer else scheme.surfaceVariant, MaterialTheme.shapes.large).padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (emphasis) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
    }
}

/** A pill that can be switched on and off (flags, TM/HM compatibility). */
@Composable
fun ToggleChip(text: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier
            .background(if (checked) scheme.primaryContainer else if (hovered) scheme.surfaceVariant else scheme.surface, MaterialTheme.shapes.large)
            .border(1.dp, if (checked) scheme.primary.copy(alpha = 0.6f) else scheme.outline, MaterialTheme.shapes.large)
            .hoverable(source)
            .testTag("chip:$text")
            .clickable { onToggle(!checked) }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (checked) scheme.onPrimaryContainer else scheme.onSurface)
    }
}

/** A thin bar showing [value] out of [max], coloured from weak to strong. */
@Composable
fun StatBar(value: Int, max: Int = 160, modifier: Modifier = Modifier) {
    val fraction = (value.toFloat() / max).coerceIn(0f, 1f)
    val color = when {
        value < 50 -> Color(0xFFE5484D)
        value < 80 -> Color(0xFFF08C32)
        value < 100 -> Color(0xFFE5B800)
        value < 130 -> Color(0xFF46A758)
        else -> Color(0xFF12A594)
    }
    Box(modifier.height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)) {
        Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(color, MaterialTheme.shapes.small))
    }
}

/** Title of the record being edited: id in monospace, then the name. */
@Composable
fun EditorHeader(id: String, name: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(id.padStart(3, '0'), style = Fox.mono.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        trailing?.invoke(this)
    }
}

/** Multi-line text input in the same style as the fields; [invalid] draws the error border. */
@Composable
fun TextArea(
    value: String,
    onChange: (String) -> Unit,
    invalid: Boolean,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 160.dp,
    transformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val shape = MaterialTheme.shapes.small
    val border = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = Fox.mono.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = transformation,
        modifier = modifier.testTag("textarea").heightIn(min = minHeight).background(MaterialTheme.colorScheme.surface, shape).border(1.dp, border, shape).padding(12.dp),
    )
}
