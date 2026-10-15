package io.github.romulofer.foxhg.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.NarcRecords
import io.github.romulofer.foxhg.core.data.map.Collision
import io.github.romulofer.foxhg.core.data.map.MapFile
import io.github.romulofer.foxhg.core.data.map.MapUsages
import io.github.romulofer.foxhg.core.data.matrix.GameMatrix
import io.github.romulofer.foxhg.core.data.map.Building
import io.github.romulofer.foxhg.core.graphics.Camera3d
import io.github.romulofer.foxhg.core.graphics.Render3d
import io.github.romulofer.foxhg.core.graphics.MapScenes
import io.github.romulofer.foxhg.core.project.RomProject
import java.awt.image.BufferedImage

/**
 * Pictures of maps for the lists: a top-down render of the map's 3D scene ([MapScenes]), or when a map has no scene a
 * schematic with one pixel per tile (tile type colours, blocked tiles darkened). A matrix is the mosaic of its cells,
 * schematic at first and rendered in the background. Built once per map and matrix; safe to call from several threads.
 */
class MapPictures(project: RomProject) {
    private val usages = MapUsages(project)
    private val maps: List<MapFile> = (NarcRecords.load(project, MapFile.SPEC) as? FoxResult.Ok)?.value?.records ?: emptyList()
    private val scenes = MapScenes(project)
    private val lock = Any()
    private val cache = HashMap<Any, ImageBitmap>()

    /** Map file [index] as a render of its scene, or its tile schematic; null for an unknown map. */
    fun ofMap(index: Int): ImageBitmap? = synchronized(lock) {
        cache.getOrPut("scene$index") {
            val scene = scenes.render(index, 4)
            if (scene != null) toBitmap(scene.width, scene.height, scene.argb)
            else maps.getOrNull(index)?.let { schematic(1, 1) { _, _ -> it } } ?: return null
        }
    }

    /** The ground height per tile of map file [index], for placing markers in 3D; all zero when it has no scene. */
    fun heights(index: Int): FloatArray = synchronized(lock) { scenes.heightGrid(index) }

    /** Whether map file [index] has a 3D scene (terrain model and textures). */
    fun hasScene(index: Int): Boolean = synchronized(lock) { scenes.hasScene(index) }

    /** The map a header shows, or null if no matrix cell uses the header. */
    fun ofHeader(header: Int): ImageBitmap? = usages.mapOf(header)?.let(::ofMap)

    /** The tile schematic of the whole matrix, quick. */
    fun ofMatrix(matrix: GameMatrix): ImageBitmap = synchronized(lock) {
        cache.getOrPut(matrix) {
            schematic(matrix.width, matrix.height) { x, y -> matrix.map(x, y).takeIf { it != GameMatrix.EMPTY }?.let { maps.getOrNull(it) } }
        }
    }

    /** The matrix as rendered scenes, 2 pixels per tile; slow for a big matrix (all of the overworld takes seconds), so call it off the UI thread. */
    fun sceneOfMatrix(matrix: GameMatrix, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImageBitmap = synchronized(lock) {
        cache.getOrPut(Pair(matrix, "scenes")) {
            val tile = MapFile.SIZE * 2
            val argb = IntArray(matrix.width * tile * matrix.height * tile)
            val stride = matrix.width * tile
            val total = matrix.width * matrix.height
            for (cy in 0 until matrix.height) for (cx in 0 until matrix.width) {
                onProgress(cy * matrix.width + cx, total)
                val map = matrix.map(cx, cy).takeIf { it != GameMatrix.EMPTY } ?: continue
                val picture = scenes.render(map, 2) ?: continue
                for (y in 0 until tile) picture.argb.copyInto(argb, (cy * tile + y) * stride + cx * tile, y * tile, (y + 1) * tile)
            }
            toBitmap(stride, matrix.height * tile, argb)
        }
    }

    /** The scene of map file [index] from [camera] as a bitmap with a sky behind it; null when the map has no scene. Rendering takes tens of milliseconds, so call it off the UI thread. */
    fun render3d(index: Int, camera: Camera3d, width: Int, height: Int, buildings: List<Building>, highlight: Int): Render3dBitmap? {
        val picture = synchronized(lock) { scenes.render3d(index, camera, width, height, buildings, highlight) } ?: return null
        val sky = 0xFF8EC9E8.toInt()
        val argb = picture.image.argb
        return Render3dBitmap(toBitmap(width, height, IntArray(argb.size) { if (argb[it] ushr 24 != 0) argb[it] else sky }), picture)
    }

    /** A rendered view with the data to find what is under a pixel. */
    class Render3dBitmap(val bitmap: ImageBitmap, val picture: Render3d)

    /** Building model [modelId] alone as a small picture, or null when the ROM has no such model. */
    fun buildingPreview(modelId: Int): ImageBitmap? = synchronized(lock) {
        cache.getOrPut("building$modelId") {
            scenes.renderBuilding(modelId, 96, 96)?.let { toBitmap(it.width, it.height, it.argb) } ?: return null
        }
    }

    /** How many building models the ROM has. */
    val buildingModelCount: Int get() = synchronized(lock) { scenes.buildingModelCount }

    private fun toBitmap(width: Int, height: Int, argb: IntArray): ImageBitmap =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { it.setRGB(0, 0, width, height, argb, 0, width) }.toComposeImageBitmap()

    private fun schematic(columns: Int, rows: Int, mapAt: (Int, Int) -> MapFile?): ImageBitmap {
        val size = MapFile.SIZE
        val image = BufferedImage(columns * size, rows * size, BufferedImage.TYPE_INT_ARGB)
        for (cy in 0 until rows) for (cx in 0 until columns) {
            val map = mapAt(cx, cy) ?: continue
            for (y in 0 until size) for (x in 0 until size) image.setRGB(cx * size + x, cy * size + y, tileColor(map, x, y).toArgb())
        }
        return image.toComposeImageBitmap()
    }

    private fun tileColor(map: MapFile, x: Int, y: Int): Color {
        val base = typeColor(map.type(x, y))
        return if (Collision.isBlocked(map.collision(x, y))) lerp(base, Color(0xFF3B1F1F), 0.7f) else base
    }
}

/** A map picture that is made in the background; blank until it is ready, so scrolling a list of maps never waits for a render. */
@Composable
fun AsyncMapPicture(key: Any, width: Dp = 26.dp, height: Dp = width, load: () -> ImageBitmap?) {
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, key) { value = withContext(Dispatchers.Default) { load() } }
    MapPicture(bitmap, width, height)
}

/** [bitmap] scaled to fit a [width] x [height] box, keeping its proportions; nothing if there is no picture. */
@Composable
fun MapPicture(bitmap: ImageBitmap?, width: Dp = 26.dp, height: Dp = width) {
    Canvas(Modifier.size(width, height).testTag("map-thumbnail")) {
        if (bitmap == null) return@Canvas
        val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        drawImage(bitmap, IntOffset.Zero, IntSize(bitmap.width, bitmap.height), IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()), IntSize(w, h), filterQuality = FilterQuality.Medium)
    }
}
