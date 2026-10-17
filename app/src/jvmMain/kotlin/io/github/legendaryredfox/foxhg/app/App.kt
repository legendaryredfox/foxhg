package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.Role
import io.github.legendaryredfox.foxhg.app.ui.ScrollHint
import androidx.compose.ui.Alignment
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.FoxTheme
import io.github.legendaryredfox.foxhg.app.ui.Loading
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.data.evolution.EvolutionData
import io.github.legendaryredfox.foxhg.core.data.learnset.Learnset
import io.github.legendaryredfox.foxhg.app.ui.AudioPlayer
import io.github.legendaryredfox.foxhg.app.ui.IconBitmaps
import io.github.legendaryredfox.foxhg.app.ui.ProvideSounds
import io.github.legendaryredfox.foxhg.app.ui.SoundContext
import io.github.legendaryredfox.foxhg.core.sound.Sounds
import io.github.legendaryredfox.foxhg.app.ui.ProvideIcons
import io.github.legendaryredfox.foxhg.core.data.eggmove.EggMoves
import io.github.legendaryredfox.foxhg.core.graphics.Icons
import io.github.legendaryredfox.foxhg.core.data.event.EventFile
import io.github.legendaryredfox.foxhg.core.data.item.ItemData
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.battletower.BattleTowerSet
import io.github.legendaryredfox.foxhg.core.data.battletower.BattleTowerTrainer
import io.github.legendaryredfox.foxhg.core.data.move.MoveData
import io.github.legendaryredfox.foxhg.core.data.trade.NpcTrade

@Composable
fun App(model: AppModel) {
    FoxTheme(dark = when (model.settings.theme) { ThemeChoice.SYSTEM -> null; ThemeChoice.LIGHT -> false; ThemeChoice.DARK -> true }) {
        Surface(Modifier.fillMaxSize().dropTarget(model), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopBar(model)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    // The editor tabs only exist once a ROM, a project or a save file is open.
                    if (model.project != null || model.save != null) {
                        Sidebar(model, Modifier.width(232.dp).fillMaxHeight())
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    }
                    Content(model, Modifier.weight(1f).fillMaxHeight())
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatusBar(model)
            }
            AppDialogs(model)
        }
    }
}

/** Files dragged from the file manager onto the window open like File > Open: see [Actions.openDropped]. **[UNVERIFIED]** with a real drag; the action is tested. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun Modifier.dropTarget(model: AppModel): Modifier = dragAndDropTarget(
    shouldStartDragAndDrop = { it.awtTransferable.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor) },
    target = object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val files = runCatching { event.awtTransferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as? List<*> }.getOrNull()
                ?.filterIsInstance<java.io.File>()?.map { it.toPath() } ?: return false
            Actions.openDropped(model, files)
            return files.isNotEmpty()
        }
    },
)

@Composable
private fun TopBar(model: AppModel) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val iconPixels = with(LocalDensity.current) { 30.dp.toPx() }
        Image(remember(iconPixels) { AppIcon.painter(iconPixels) }, contentDescription = "foxhg", modifier = Modifier.size(30.dp))
        Text(model.projectName ?: "foxhg", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        model.header?.let { header ->
            Chip(header.game.displayName.replace("Pokémon ", "").replace(" (English)", " · English"))
        }
        SaveIndicator(model)
        Spacer(Modifier.weight(1f))
        ProjectMenu(model)
        OutlinedButton(onClick = { Actions.run(model) }, enabled = model.project != null, modifier = Modifier.testTag("run")) { Text("Run") }
        if (model.isDirty) Button(onClick = { Actions.build(model) }, enabled = model.project != null) { Text("Build ROM…") }
        else OutlinedButton(onClick = { Actions.build(model) }, enabled = model.project != null) { Text("Build ROM…") }
    }
}

/** Where the user's edits stand: saved in the project, saving, or only in memory. */
@Composable
private fun SaveIndicator(model: AppModel) {
    val scheme = MaterialTheme.colorScheme
    if (model.project == null) return
    when {
        model.projectDir == null && model.needsProjectToKeepWork ->
            Chip("● Not saved. Save as project…", emphasis = true, modifier = Modifier.clickable { Actions.saveAs(model) })
        model.projectDir == null -> Unit
        model.hasUnsavedWork -> Text(if (model.autosave) "Saving…" else "● Unsaved changes", style = MaterialTheme.typography.bodySmall, color = scheme.primary)
        else -> Text("✓ Saved", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun Sidebar(model: AppModel, modifier: Modifier) {
    val hasRom = model.project != null
    val scroll = rememberScrollState()
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // The ROM editors need a ROM or project, the save editors an open save file; each kind shows only when its file is open.
        val ready = EditorKind.entries.filter { it.implemented && if (it.group == SAVE_GROUP) model.save != null else hasRom }.groupBy { it.group }
        ready.forEach { (group, editors) ->
            GroupLabel(group)
            editors.forEach { NavItem(it.title, model.selected == it, enabled = true, soon = false) { model.selected = it } }
        }
        val soon = EditorKind.entries.filter { !it.implemented }
        if (hasRom && soon.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            GroupLabel("Coming soon")
            soon.forEach { NavItem(it.title, selected = false, enabled = false, soon = true) {} }
        }
    }
    ScrollHint(scroll)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(), Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun NavItem(title: String, selected: Boolean, enabled: Boolean, soon: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) bringIntoView.bringIntoView() }
    Row(
        Modifier.testTag("nav:$title").padding(start = 12.dp).fillMaxWidth().height(34.dp).bringIntoViewRequester(bringIntoView)
            .background(if (selected) scheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                selected -> scheme.onPrimaryContainer
                soon || !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
                else -> scheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusBar(model: AppModel) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val error = model.error
        when {
            error != null -> Text(error, color = scheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            else -> Text(model.status ?: "Ready", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        model.project?.let { p ->
            val edited = p.editedPaths.size + p.editedOverlays.size + if (p.editedArm9 != null) 1 else 0
            if (model.revision >= 0) Text(if (edited == 0) "No changes" else "${plural(edited, "file")} changed", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        model.romPath?.let { Text(it.fileName.toString(), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Text("v${AppVersion.current}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("app-version"))
    }
}

@Composable
private fun Content(model: AppModel, modifier: Modifier) {
    val project = model.project
    val selected = model.selected
    val icons = remember(project) { project?.let { IconBitmaps(Icons(it)) } }
    val soundContext = remember(project) { project?.let { SoundContext(Sounds(it), AudioPlayer()) } }
    androidx.compose.runtime.DisposableEffect(soundContext) { onDispose { soundContext?.player?.stop() } }
    ProvideSounds(soundContext) {
    ProvideIcons(icons) {
    androidx.compose.runtime.key(model.undoEpoch) {
    when {
        selected == EditorKind.SAVE_PLAYER && model.save != null -> SavePlayerScreen(model.save!!, modifier)
        selected == EditorKind.SAVE_PARTY && model.save != null -> SavePartyScreen(model.save!!, model.project?.names, model::saveRules, modifier)
        selected == EditorKind.SAVE_BAG && model.save != null -> SaveBagScreen(model.save!!, model.project?.names, modifier)
        selected == EditorKind.SAVE_POSITION && model.save != null -> SavePositionScreen(model.save!!, model::mapName, modifier)
        selected == EditorKind.SAVE_FILE && model.save != null -> SaveFileScreen(model.save!!, modifier)
        project == null -> EmptyState(model, modifier)
        selected == null -> Welcome(model, modifier)
        selected == EditorKind.PERSONAL -> Loading(project, "Personal data", { PersonalEditorModel(project, onChanged = model::projectChanged).also { m -> model.selectionMemory[EditorKind.PERSONAL]?.let(m::select) } }) { editor ->
            KeepSelection(model, EditorKind.PERSONAL, editor.selectedIndex)
            PersonalEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.BATTLE_DISPLAY -> Loading(project, "battle display", { BattleDisplayEditorModel(project, onChanged = model::projectChanged).also { m -> model.selectionMemory[EditorKind.BATTLE_DISPLAY]?.let(m::select) } }) { editor ->
            KeepSelection(model, EditorKind.BATTLE_DISPLAY, editor.selectedIndex)
            BattleDisplayEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.PICKUP -> Loading(project, "pickup", { PickupEditorModel(project, model::projectChanged) }) { editor ->
            PickupEditor(editor, modifier)
        }
        selected == EditorKind.GROWTH -> Loading(project, "growth rates", { GrowthEditorModel(project, model::projectChanged) }) { editor ->
            GrowthEditor(editor, modifier)
        }
        selected == EditorKind.TMHM_BULK -> Loading(project, "TM/HM compatibility", { TmHmBulkEditorModel(project, model::projectChanged) }) { editor ->
            TmHmBulkEditor(editor, modifier)
        }
        selected == EditorKind.TRAINER_FLAGS -> Loading(project, "trainer flags", { TrainerFlagBulkEditorModel(project, model::projectChanged) }) { editor ->
            TrainerFlagBulkEditor(editor, modifier)
        }
        selected == EditorKind.BATTLE_TOWER_TRAINERS -> Loading(project, "Battle Tower", {
            val sets = (NarcRecords.load(project, BattleTowerSet.SPEC) as? FoxResult.Ok)?.value?.records ?: emptyList()
            val classes = (NarcRecords.load(project, BattleTowerTrainer.SPEC) as? FoxResult.Ok)?.value?.records?.map { it.trainerClass } ?: emptyList()
            model.recordEditor(EditorKind.BATTLE_TOWER_TRAINERS, project, BattleTowerTrainer.SPEC, battleTowerTrainerLabel(project.names) { classes.getOrNull(it) ?: 0 }, 0) to sets
        }) { (editor, sets) ->
            BattleTowerTrainerEditor(editor, sets, modifier.padding(16.dp))
        }
        selected == EditorKind.BATTLE_TOWER_SETS -> Loading(project, "Battle Tower sets", { model.recordEditor(EditorKind.BATTLE_TOWER_SETS, project, BattleTowerSet.SPEC, RecordEditorModel.speciesLabel(project.names), 1) }) { editor ->
            BattleTowerSetEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.MARTS -> Loading(project, "Poké Marts", { MartEditorModel(project, model::projectChanged) }) { editor ->
            MartEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.TYPE_CHART -> Loading(project, "type chart", { TypeChartEditorModel(project, model::projectChanged) }) { editor ->
            TypeChartEditor(editor, modifier)
        }
        selected == EditorKind.LEARNSET -> Loading(project, "learnsets", { model.recordEditor(EditorKind.LEARNSET, project, Learnset.SPEC, RecordEditorModel.speciesLabel(project.names), 1) }) { editor ->
            LearnsetEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.EVOLUTIONS -> Loading(project, "evolutions", { model.recordEditor(EditorKind.EVOLUTIONS, project, EvolutionData.SPEC, RecordEditorModel.speciesLabel(project.names), 1) }) { editor ->
            EvolutionEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.MOVES -> Loading(project, "moves", { model.recordEditor(EditorKind.MOVES, project, MoveData.SPEC, RecordEditorModel.moveLabel(project.names), 1) }) { editor ->
            MoveEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.EGG_MOVES -> Loading(project, "egg moves", { model.recordEditor(EditorKind.EGG_MOVES, project, EggMoves.SPEC, { "%03d".format(it) }, 0) }) { editor ->
            EggMoveEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.STARTERS -> Loading(project, "starters", { StarterEditorModel(project, onChanged = model::projectChanged) }) { editor ->
            StarterEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.TRADES -> Loading(project, "trades", { model.recordEditor(EditorKind.TRADES, project, NpcTrade.SPEC, RecordEditorModel.tradeLabel(project.names), 0) }) { editor ->
            TradeEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.TMHM -> Loading(project, "TMs and HMs", { TmHmEditorModel(project, onChanged = model::projectChanged) }) { editor ->
            TmHmEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.ITEMS -> Loading(project, "items", { model.recordEditor(EditorKind.ITEMS, project, ItemData.SPEC, RecordEditorModel.itemLabel(project.names), 1) }) { editor ->
            ItemEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.HIDDEN_ITEMS -> Loading(project, "hidden items", { HiddenItemEditorModel(project, onChanged = model::projectChanged).also { m -> model.selectionMemory[EditorKind.HIDDEN_ITEMS]?.let(m::select) } }) { editor ->
            KeepSelection(model, EditorKind.HIDDEN_ITEMS, editor.selectedIndex)
            HiddenItemEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.FLY -> Loading(project, "fly table", { FlyEditorModel(project, onChanged = model::projectChanged).also { m -> model.selectionMemory[EditorKind.FLY]?.let(m::select) } }) { editor ->
            KeepSelection(model, EditorKind.FLY, editor.selectedIndex)
            FlyEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.TRAINERS -> Loading(project, "trainers", { TrainerEditorModel(project, onChanged = model::projectChanged).also { m -> model.selectionMemory[EditorKind.TRAINERS]?.let(m::select) } }) { editor ->
            KeepSelection(model, EditorKind.TRAINERS, editor.selectedIndex)
            TrainerEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.MAPS -> Loading(project, "maps", { MapEditorModel(project, onChanged = model::projectChanged).also { m -> model.selectionMemory[EditorKind.MAPS]?.let(m::select) } }) { editor ->
            KeepSelection(model, EditorKind.MAPS, editor.maps.selectedIndex)
            MapEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.EVENTS -> Loading(project, "events", {
            EventLabels(project).let { labels ->
                labels to model.recordEditor(EditorKind.EVENTS, project, EventFile.SPEC, labels::label, 0).also { m ->
                    // Open on the first file that has events instead of the empty file 0.
                    if (model.selectionMemory[EditorKind.EVENTS] == null) m.records.indexOfFirst { it.count > 0 }.takeIf { it >= 0 }?.let(m::select)
                }
            }
        }) { (labels, editor) ->
            EventEditor(editor, labels, modifier.padding(16.dp))
        }
        selected == EditorKind.SCRIPTS -> Loading(project, "scripts", {
            val labels = ScriptLabels(project)
            labels to ScriptEditorModel(project, labels, model::projectChanged).also { m -> model.selectionMemory[EditorKind.SCRIPTS]?.let(m::select) }
        }) { (labels, editor) ->
            KeepSelection(model, EditorKind.SCRIPTS, editor.files.selectedIndex)
            ScriptEditor(editor, labels, modifier.padding(16.dp))
        }
        selected == EditorKind.SOUNDS -> SoundEditor(modifier.padding(16.dp))
        selected == EditorKind.TOOLBOX -> PatchToolbox(remember(project) { PatchToolboxModel(project, onChanged = model::projectChanged) }, modifier.padding(16.dp))
        selected == EditorKind.FILES -> Loading(project, "files", { FileTreeModel(project) }) { editor ->
            FileTreeEditor(editor, model, modifier.padding(16.dp))
        }
        selected == EditorKind.TEXT -> Loading(project, "text archives", {
            MessageEditorModel(project, onChanged = model::projectChanged).also { m ->
                // The bank and the message are remembered as one number: bank * 100000 + message.
                model.selectionMemory[EditorKind.TEXT]?.let { m.selectBank(it / 100000); m.selectMessage(it % 100000) }
            }
        }) { editor ->
            KeepSelection(model, EditorKind.TEXT, editor.banks.selectedIndex * 100000 + editor.messageIndex)
            MessageEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.TRAINER_CLASSES -> Loading(project, "trainer classes", { TrainerClassEditorModel(project, onChanged = model::projectChanged) }) { editor ->
            TrainerClassEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.ROCK_SMASH -> Loading(project, "Rock Smash", {
            RockSmashEditorModel(project, model::projectChanged, model.selectionMemory[EditorKind.ROCK_SMASH] ?: 0, { model.selectionMemory[EditorKind.ROCK_SMASH] = it }).also { m ->
                // Open on the first map that drops items instead of the empty first header.
                if (model.selectionMemory[EditorKind.ROCK_SMASH] == null) m.sites.records.indexOfFirst { it.odds > 0 }.takeIf { it >= 0 }?.let(m.sites::select)
            }
        }) { editor ->
            RockSmashEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.HEADBUTT -> Loading(project, "headbutt trees", { HeadbuttEditorModel(project, model::projectChanged, model.selectionMemory[EditorKind.HEADBUTT] ?: 0, { model.selectionMemory[EditorKind.HEADBUTT] = it }) }) { editor ->
            HeadbuttEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.BUG_CONTEST -> Loading(project, "bug contest", { BugContestEditorModel(project, model::projectChanged) }) { editor ->
            BugContestEditor(editor, modifier)
        }
        selected == EditorKind.HEADERS -> Loading(project, "map headers", { MapHeaderEditorModel(project, onChanged = model::projectChanged).also { m -> model.selectionMemory[EditorKind.HEADERS]?.let(m::select) } }) { editor ->
            KeepSelection(model, EditorKind.HEADERS, editor.selectedIndex)
            MapHeaderEditor(editor, modifier.padding(16.dp), onOpen = model::openAt)
        }
        selected == EditorKind.MATRICES -> Loading(project, "matrices", { createMatrixEditor(project, model::projectChanged, model.selectionMemory[EditorKind.MATRICES] ?: 0) }) { editor ->
            KeepSelection(model, EditorKind.MATRICES, editor.records.selectedIndex)
            MatrixEditor(editor, modifier.padding(16.dp))
        }
        selected == EditorKind.ENCOUNTERS -> Loading(project, "wild encounters", { EncounterEditorModel(project, onChanged = model::projectChanged).also { m -> model.selectionMemory[EditorKind.ENCOUNTERS]?.let(m.editor::select) } }) { editor ->
            KeepSelection(model, EditorKind.ENCOUNTERS, editor.editor.selectedIndex)
            EncounterEditor(editor, modifier.padding(16.dp))
        }
        else -> Box(modifier, contentAlignment = Alignment.Center) { Text("${selected.title} is not implemented yet (docs/plan.md)", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    }
    }
    }
}

/** Remembers [current] as the selection of editor [kind], so a rebuilt editor (undo, switching screens) comes back to it. */
@Composable
private fun KeepSelection(model: AppModel, kind: EditorKind, current: Int) {
    androidx.compose.runtime.LaunchedEffect(current) { model.selectionMemory[kind] = current }
}

/** A record editor that remembers its selection in [AppModel.selectionMemory], so undo and switching screens keep the place. */
private fun <T> AppModel.recordEditor(
    kind: EditorKind,
    project: io.github.legendaryredfox.foxhg.core.project.RomProject,
    spec: io.github.legendaryredfox.foxhg.core.data.RecordSpec<T>,
    label: (Int) -> String,
    first: Int,
) = RecordEditorModel(project, spec, label, ::projectChanged, firstIndex = selectionMemory[kind] ?: first, onSelected = { selectionMemory[kind] = it })

@Composable
private fun EmptyState(model: AppModel, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.width(500.dp), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.Start) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Start a project", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "foxhg edits Pokémon HeartGold and SoulSilver (English, .nds). A project keeps your changes in a folder and saves them as you work. Your ROM is never modified: Build ROM writes a new file.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // FlowRow: four buttons are wider than the column, so the last one goes to a second line instead of squeezing its text
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { Actions.newProject(model) }) { Text("New project…") }
                OutlinedButton(onClick = { Actions.openProject(model) }) { Text("Open project…") }
                OutlinedButton(onClick = { Actions.openRom(model) }) { Text("Open ROM only…") }
                OutlinedButton(onClick = { Actions.openSave(model) }, modifier = Modifier.testTag("open-save")) { Text("Open save file…") }
            }
            if (model.recent.isNotEmpty()) {
                SectionCard("Recent") {
                    model.recent.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().clickable { Actions.openRecent(model, entry) }.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Chip(if (entry.kind == RecentKind.PROJECT) "Project" else "ROM", emphasis = entry.kind == RecentKind.PROJECT)
                            Column(Modifier.weight(1f)) {
                                Text(entry.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(entry.path.parent?.toString() ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome(model: AppModel, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(model.header?.game?.displayName ?: "", style = MaterialTheme.typography.headlineSmall)
            Text(if (model.projectDir != null) "Pick an editor on the left. Changes are saved into the project automatically." else "Pick an editor on the left. Without a project, changes stay in memory until you save them as a project or choose Build ROM….", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun createMatrixEditor(project: io.github.legendaryredfox.foxhg.core.project.RomProject, onChanged: () -> Unit, first: Int): MatrixEditorModel {
    val names = project.names
    val headers = (io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable.load(project) as? io.github.legendaryredfox.foxhg.core.FoxResult.Ok)?.value ?: emptyList()
    val headerNames = headers.mapIndexed { i, h -> names?.locations?.getOrNull(h.locationName)?.takeIf { it.isNotBlank() } ?: "Map $i" }
    val usedBy = HashMap<Int, MutableList<String>>()
    headers.forEachIndexed { i, h -> usedBy.getOrPut(h.matrix) { mutableListOf() } += headerNames[i] }
    val holder = arrayOfNulls<RecordEditorModel<io.github.legendaryredfox.foxhg.core.data.matrix.GameMatrix>>(1)
    val records = RecordEditorModel(project, io.github.legendaryredfox.foxhg.core.data.matrix.GameMatrix.SPEC, { i -> "%03d  %s".format(i, holder[0]?.records?.getOrNull(i)?.name ?: "matrix") }, onChanged, firstIndex = first)
    holder[0] = records
    return MatrixEditorModel(records, usedBy, headerNames, MapPictures(project))
}
