package io.github.legendaryredfox.foxhg.core.graphics

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.map.MapFile
import io.github.legendaryredfox.foxhg.core.data.map.MapUsages
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.u16
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Top-down pictures of the maps: the terrain model with the tileset of the map's area, and the buildings placed on
 * it, drawn from above with the highest surface on top. Flat shading only (no lighting, shadows, water animation
 * or translucency), so it is a map preview, not the game's own picture. A map not used by any matrix, or whose
 * area's textures are missing, has none.
 */
class MapScenes(private val project: RomProject) {
    private val usages = MapUsages(project)
    private val maps: List<MapFile> = (NarcRecords.load(project, MapFile.SPEC) as? FoxResult.Ok)?.value?.records ?: emptyList()
    private val areaData: Narc? by lazy { narc(project.layout.areaData) }
    private val mapTextures: Narc? by lazy { narc(project.layout.mapTextures) }
    private val buildingModels: Narc? by lazy { narc(project.layout.buildingModels) }

    private val tilesets = HashMap<Int, Nsbtx?>()
    private val buildings = HashMap<Int, Pair<Nsbmd, Nsbtx?>?>()
    private val images = HashMap<Any, RgbaImage?>()
    private val rendered = HashMap<Pair<Int, Int>, RgbaImage?>()
    private val terrain = HashMap<Int, List<Mesh3d>?>()
    private val buildingMeshes = HashMap<Int, List<Mesh3d>>()
    private val heights = HashMap<Int, FloatArray>()

    private fun narc(path: String): Narc? = project.read(path)?.let { (Narc.parse(it) as? FoxResult.Ok)?.value }

    /** The picture of map file [map], `32 * pixelsPerTile` pixels square. */
    fun render(map: Int, pixelsPerTile: Int = 4): RgbaImage? = rendered.getOrPut(map to pixelsPerTile) { draw(map, pixelsPerTile) }

    /** Whether map file [map] has a terrain model and textures to draw. */
    fun hasScene(map: Int): Boolean = terrainMeshes(map) != null

    /** Models in the building list of the ROM; building model ids are below this. */
    val buildingModelCount: Int get() = buildingModels?.files?.size ?: 0

    /**
     * Terrain and [buildings] of map file [map] as textured triangles in tiles around the map's centre (y up), for
     * [Rasterizer3d]. The buildings come from the caller so edits show at once; building `i` is tagged `i`.
     */
    fun scene(map: Int, buildings: List<io.github.legendaryredfox.foxhg.core.data.map.Building> = maps.getOrNull(map)?.buildings ?: emptyList()): List<Mesh3d>? {
        val base = terrainMeshes(map) ?: return null
        val out = ArrayList(base)
        buildings.forEachIndexed { i, b -> buildingMeshesOf(b.modelId.toInt()).mapTo(out) { it.placed(b.x.value, b.y.value, b.z.value, i) } }
        return out
    }

    /** A picture of map file [map] from [camera], [width] x [height] pixels; transparent where there is nothing. Building [highlight] is tinted. */
    fun render3d(
        map: Int, camera: Camera3d, width: Int, height: Int,
        buildings: List<io.github.legendaryredfox.foxhg.core.data.map.Building> = maps.getOrNull(map)?.buildings ?: emptyList(),
        highlight: Int = Render3d.NOTHING,
    ): Render3d? = scene(map, buildings)?.let { Rasterizer3d.render(it, camera, width, height, highlight) }

    /**
     * The height of the ground at the middle of each of the 32 x 32 tiles of map [map] (row by row, tiles from the north
     * west corner), in tiles; the lowest terrain surface there (trees and the like stand on it). 0 where there is no terrain or no scene.
     */
    fun heightGrid(map: Int): FloatArray = heights.getOrPut(map) {
        val grid = FloatArray(MapFile.SIZE * MapFile.SIZE) { Float.POSITIVE_INFINITY }
        for (mesh in terrainMeshes(map).orEmpty()) {
            val v = mesh.vertices
            for (t in 0 until v.size / 15) {
                val o = t * 15
                val x = FloatArray(3) { v[o + it * 5] }
                val y = FloatArray(3) { v[o + it * 5 + 1] }
                val z = FloatArray(3) { v[o + it * 5 + 2] }
                val den = (z[1] - z[2]) * (x[0] - x[2]) + (x[2] - x[1]) * (z[0] - z[2])
                if (kotlin.math.abs(den) < 1e-9f) continue
                val tx0 = max(0, floor(min(x[0], min(x[1], x[2])) + 16 - 0.5f).toInt())
                val tx1 = min(MapFile.SIZE - 1, ceil(max(x[0], max(x[1], x[2])) + 16 - 0.5f).toInt())
                val tz0 = max(0, floor(min(z[0], min(z[1], z[2])) + 16 - 0.5f).toInt())
                val tz1 = min(MapFile.SIZE - 1, ceil(max(z[0], max(z[1], z[2])) + 16 - 0.5f).toInt())
                for (tz in tz0..tz1) for (tx in tx0..tx1) {
                    val px = tx - 16 + 0.5f
                    val pz = tz - 16 + 0.5f
                    val a = ((z[1] - z[2]) * (px - x[2]) + (x[2] - x[1]) * (pz - z[2])) / den
                    val b = ((z[2] - z[0]) * (px - x[2]) + (x[0] - x[2]) * (pz - z[2])) / den
                    val c = 1 - a - b
                    if (a < -1e-4f || b < -1e-4f || c < -1e-4f) continue
                    val h = a * y[0] + b * y[1] + c * y[2]
                    if (h < grid[tz * MapFile.SIZE + tx]) grid[tz * MapFile.SIZE + tx] = h
                }
            }
        }
        FloatArray(grid.size) { if (grid[it] == Float.POSITIVE_INFINITY) 0f else grid[it] }
    }

    /** Building model [modelId] alone, seen from the south-east and above, for a picker; null if there is no such model. */
    fun renderBuilding(modelId: Int, width: Int, height: Int): RgbaImage? {
        val meshes = buildingMeshesOf(modelId).takeIf { it.isNotEmpty() } ?: return null
        var top = 0f
        var reach = 1f
        for (m in meshes) for (i in 0 until m.vertices.size / 5) {
            top = max(top, m.vertices[i * 5 + 1])
            reach = max(reach, max(kotlin.math.abs(m.vertices[i * 5]), kotlin.math.abs(m.vertices[i * 5 + 2])))
        }
        val camera = Camera3d(yaw = 0.7, pitch = 0.6, distance = (reach + top) * 2.6 + 2, targetY = top / 2.0)
        return Rasterizer3d.render(meshes, camera, width, height).image
    }

    private fun terrainMeshes(map: Int): List<Mesh3d>? = terrain.getOrPut(map) { buildTerrain(map) }

    private fun buildTerrain(map: Int): List<Mesh3d>? {
        val file = maps.getOrNull(map) ?: return null
        val header = usages.of(map).firstOrNull()?.header?.let { usages.headers.getOrNull(it) } ?: return null
        val area = areaData?.files?.getOrNull(header.areaData)?.takeIf { it.size >= 4 } ?: return null
        val tileset = tilesets.getOrPut(area.u16(2)) { mapTextures?.files?.getOrNull(area.u16(2))?.let { (Nsbtx.parse(it) as? FoxResult.Ok)?.value } } ?: return null
        val model = (Nsbmd.parse(file.model) as? FoxResult.Ok)?.value ?: return null
        return ArrayList<Mesh3d>().also { meshesOf(model, tileset, "t${area.u16(2)}", 0.0, 0.0, 0.0, it) }
    }

    private fun buildingMeshesOf(modelId: Int): List<Mesh3d> = buildingMeshes.getOrPut(modelId) {
        val (model, textures) = building(modelId) ?: return@getOrPut emptyList()
        ArrayList<Mesh3d>().also { meshesOf(model, textures, "b$modelId", 0.0, 0.0, 0.0, it) }
    }

    private fun meshesOf(model: Nsbmd, textures: Nsbtx?, source: String, ox: Double, oy: Double, oz: Double, out: MutableList<Mesh3d>) {
        for (shape in model.shapes) {
            val material = model.materials.getOrNull(shape.material) ?: continue
            val texName = material.textureName ?: continue
            if (material.alpha < 31 || texName == "wind" || textures == null) continue
            val image = images.getOrPut("$source/$texName/${material.paletteName}") { textures.image(texName, material.paletteName) } ?: continue
            val v = shape.triangles
            val vertices = FloatArray(v.size)
            for (i in 0 until v.size / 5) {
                vertices[i * 5] = (v[i * 5] / 16.0 + ox).toFloat()
                vertices[i * 5 + 1] = (v[i * 5 + 1] / 16.0 + oy).toFloat()
                vertices[i * 5 + 2] = (v[i * 5 + 2] / 16.0 + oz).toFloat()
                vertices[i * 5 + 3] = v[i * 5 + 3] / image.width
                vertices[i * 5 + 4] = v[i * 5 + 4] / image.height
            }
            out += Mesh3d(image, vertices, material.repeatS, material.repeatT, material.flipS, material.flipT)
        }
    }

    private fun draw(map: Int, ppt: Int): RgbaImage? {
        val file = maps.getOrNull(map) ?: return null
        val header = usages.of(map).firstOrNull()?.header?.let { usages.headers.getOrNull(it) } ?: return null
        val area = areaData?.files?.getOrNull(header.areaData)?.takeIf { it.size >= 4 } ?: return null
        val tileset = tilesets.getOrPut(area.u16(2)) { mapTextures?.files?.getOrNull(area.u16(2))?.let { (Nsbtx.parse(it) as? FoxResult.Ok)?.value } } ?: return null
        val terrain = (Nsbmd.parse(file.model) as? FoxResult.Ok)?.value ?: return null
        val size = MapFile.SIZE * ppt
        val canvas = Canvas(size)
        canvas.draw(terrain, tileset, "t${area.u16(2)}", 0.0, 0.0, 0.0, ppt)
        for (b in file.buildings) {
            val (model, textures) = building(b.modelId.toInt()) ?: continue
            canvas.draw(model, textures, "b${b.modelId}", b.x.value, b.y.value, b.z.value, ppt)
        }
        return RgbaImage(size, size, canvas.pixels)
    }

    private fun building(id: Int): Pair<Nsbmd, Nsbtx?>? = buildings.getOrPut(id) {
        val bytes = buildingModels?.files?.getOrNull(id) ?: return@getOrPut null
        val model = (Nsbmd.parse(bytes) as? FoxResult.Ok)?.value ?: return@getOrPut null
        model to (Nsbtx.parse(bytes) as? FoxResult.Ok)?.value
    }

    private inner class Canvas(val size: Int) {
        val pixels = IntArray(size * size)
        private val depth = FloatArray(size * size) { Float.NEGATIVE_INFINITY }

        /** Draws [model] with its origin at ([ox], [oy] up, [oz]) tiles from the map's centre. */
        fun draw(model: Nsbmd, textures: Nsbtx?, source: String, ox: Double, oy: Double, oz: Double, ppt: Int) {
            for (shape in model.shapes) {
                val material = model.materials.getOrNull(shape.material) ?: continue
                val texName = material.textureName ?: continue
                // Translucent faces (shadows) and the thin "wind" streak models are skipped.
                if (material.alpha < 31 || texName == "wind" || textures == null) continue
                val image = images.getOrPut("$source/$texName/${material.paletteName}") { textures.image(texName, material.paletteName) } ?: continue
                val v = shape.triangles
                for (t in 0 until shape.triangleCount) {
                    val o = t * 15
                    fillTriangle(
                        FloatArray(3) { ((v[o + it * 5] / 16.0 + 16 + ox) * ppt).toFloat() },
                        FloatArray(3) { ((v[o + it * 5 + 2] / 16.0 + 16 + oz) * ppt).toFloat() },
                        FloatArray(3) { (v[o + it * 5 + 1] / 16.0 + oy).toFloat() },
                        FloatArray(3) { v[o + it * 5 + 3] / image.width },
                        FloatArray(3) { v[o + it * 5 + 4] / image.height },
                        image, material,
                    )
                }
            }
        }

        private fun fillTriangle(x: FloatArray, y: FloatArray, h: FloatArray, s: FloatArray, t: FloatArray, image: RgbaImage, m: Nsbmd.Material) {
            val denominator = (y[1] - y[2]) * (x[0] - x[2]) + (x[2] - x[1]) * (y[0] - y[2])
            if (kotlin.math.abs(denominator) < 1e-9f) return
            val x0 = max(0, floor(min(x[0], min(x[1], x[2]))).toInt())
            val x1 = min(size - 1, ceil(max(x[0], max(x[1], x[2]))).toInt())
            val y0 = max(0, floor(min(y[0], min(y[1], y[2]))).toInt())
            val y1 = min(size - 1, ceil(max(y[0], max(y[1], y[2]))).toInt())
            for (py in y0..y1) for (px in x0..x1) {
                val cx = px + 0.5f
                val cy = py + 0.5f
                val a = ((y[1] - y[2]) * (cx - x[2]) + (x[2] - x[1]) * (cy - y[2])) / denominator
                val b = ((y[2] - y[0]) * (cx - x[2]) + (x[0] - x[2]) * (cy - y[2])) / denominator
                val c = 1 - a - b
                if (a < -1e-4f || b < -1e-4f || c < -1e-4f) continue
                val height = a * h[0] + b * h[1] + c * h[2]
                val at = py * size + px
                if (height < depth[at]) continue
                val u = wrap(a * s[0] + b * s[1] + c * s[2], m.repeatS, m.flipS)
                val w = wrap(a * t[0] + b * t[1] + c * t[2], m.repeatT, m.flipT)
                val color = image.argb[min(image.height - 1, (w * image.height).toInt()) * image.width + min(image.width - 1, (u * image.width).toInt())]
                if (color ushr 24 < 0x80) continue
                depth[at] = height
                pixels[at] = color or (0xFF shl 24)
            }
        }

        private fun wrap(v: Float, repeat: Boolean, flip: Boolean): Float {
            if (!repeat) return v.coerceIn(0f, 1f)
            val whole = floor(v)
            val frac = v - whole
            return if (flip && whole.toInt() % 2 != 0) 1f - frac else frac
        }
    }
}
