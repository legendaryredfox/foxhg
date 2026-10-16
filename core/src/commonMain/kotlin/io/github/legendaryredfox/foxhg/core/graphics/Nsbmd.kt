package io.github.legendaryredfox.foxhg.core.graphics

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/**
 * NSBMD / BMD0: a Nitro 3D model, read as triangles. Only the first model of the file is read. Each [Shape] is the
 * display list (the DS geometry command stream) of one polygon group, already placed by its node and scaled by
 * the model's position scale, so the coordinates are in model units (16 per map tile for map and building models).
 * Skinning, billboards, animation and lighting are not handled; the textures are in the file's own TEX0 block
 * (see [Nsbtx]) or in a separate texture archive, found through the material names.
 */
class Nsbmd private constructor(val materials: List<Material>, val shapes: List<Shape>) {
    class Material(
        val name: String,
        val textureName: String?,
        val paletteName: String?,
        val repeatS: Boolean,
        val repeatT: Boolean,
        val flipS: Boolean,
        val flipT: Boolean,
        /** Polygon alpha 0..31; 31 is opaque. */
        val alpha: Int,
    )

    /** Triangles of one material: per vertex x, y, z (model units) and s, t (texels), 15 floats per triangle. */
    class Shape(val material: Int, val triangles: FloatArray) {
        val triangleCount: Int get() = triangles.size / 15
    }

    private class Node(val translation: DoubleArray, val scale: DoubleArray, val rotation: DoubleArray)

    companion object {
        fun parse(bytes: ByteArray): FoxResult<Nsbmd> = try {
            read(bytes)
        } catch (e: IndexOutOfBoundsException) {
            FoxResult.Err("NSBMD is truncated or malformed")
        }

        private fun read(b: ByteArray): FoxResult<Nsbmd> {
            if (b.size < 0x14 || b.decodeToString(0, 4) != "BMD0") return FoxResult.Err("Not an NSBMD file")
            val blocks = b.u16(0xE)
            val mdl0 = (0 until blocks).map { b.u32(0x10 + it * 4).toInt() }.firstOrNull { b.decodeToString(it, it + 4) == "MDL0" }
                ?: return FoxResult.Err("NSBMD has no MDL0 block")
            val d = b.copyOfRange(mdl0, b.size)
            val models = dictionary(d, 8)
            if (models.isEmpty()) return FoxResult.Err("NSBMD holds no model")
            val m = d.u32(models[0].offset).toInt()
            val sbc = m + d.u32(m + 4).toInt()
            val materialSet = m + d.u32(m + 8).toInt()
            val shapeSet = m + d.u32(m + 12).toInt()
            val positionScale = d.s32(m + 0x1C) / 4096.0

            val materials = readMaterials(d, materialSet)
            val nodes = readNodes(d, m + 0x40)
            val parents = IntArray(nodes.size) { -1 }
            val shapeNode = HashMap<Int, Int>()
            val shapeMaterial = HashMap<Int, Int>()
            walkRenderCommands(d, sbc, nodes.size, parents, shapeNode, shapeMaterial)
            val world = arrayOfNulls<Node>(nodes.size)
            fun resolve(n: Int): Node {
                world[n]?.let { return it }
                world[n] = nodes[n] // guards against a cycle
                val parent = parents[n]
                val placed = if (parent < 0 || parent == n) nodes[n] else compose(resolve(parent), nodes[n])
                world[n] = placed
                return placed
            }

            val shapes = dictionary(d, shapeSet).mapIndexed { i, entry ->
                val struct = shapeSet + d.u32(entry.offset).toInt()
                val list = struct + d.u32(struct + 8).toInt()
                val size = d.u32(struct + 12).toInt()
                val node = shapeNode[i]?.takeIf { it in nodes.indices }?.let { resolve(it) }
                Shape(shapeMaterial[i] ?: 0, displayList(d, list, size, positionScale, node))
            }
            return FoxResult.Ok(Nsbmd(materials, shapes))
        }

        private class Entry(val name: String, val offset: Int)

        /** A Nitro dictionary: 8 byte head, a tree of 4 bytes per entry (and one more), the entry size, then entries and names. */
        private fun dictionary(d: ByteArray, at: Int): List<Entry> {
            val count = d[at + 1].toInt() and 0xFF
            val unitAt = at + 8 + 4 * (count + 1)
            val unit = d.u16(unitAt)
            val data = unitAt + 4
            return (0 until count).map { i ->
                val name = d.decodeToString(data + count * unit + i * 16, data + count * unit + i * 16 + 16).substringBefore('\u0000')
                Entry(name, data + i * unit)
            }
        }

        private fun readMaterials(d: ByteArray, set: Int): List<Material> {
            val entries = dictionary(d, set + 4)
            val textureOf = HashMap<Int, String>()
            val paletteOf = HashMap<Int, String>()
            for ((dictAt, target) in listOf(set + d.u16(set) to textureOf, set + d.u16(set + 2) to paletteOf)) {
                for (entry in dictionary(d, dictAt)) {
                    val v = d.u32(entry.offset)
                    if (v and 0x01000000L != 0L) continue
                    val list = set + (v and 0xFFFF).toInt()
                    for (k in 0 until ((v shr 16) and 0xFF).toInt()) target[d[list + k].toInt() and 0xFF] = entry.name
                }
            }
            return entries.mapIndexed { i, entry ->
                val struct = set + d.u32(entry.offset).toInt()
                val polygonAttr = d.u32(struct + 0xC)
                val image = d.u32(struct + 0x14)
                Material(
                    name = entry.name,
                    textureName = textureOf[i],
                    paletteName = paletteOf[i],
                    repeatS = image and (1L shl 16) != 0L,
                    repeatT = image and (1L shl 17) != 0L,
                    flipS = image and (1L shl 18) != 0L,
                    flipT = image and (1L shl 19) != 0L,
                    alpha = ((polygonAttr shr 16) and 31).toInt(),
                )
            }
        }

        private fun readNodes(d: ByteArray, set: Int): List<Node> = dictionary(d, set).map { entry ->
            var p = set + d.u32(entry.offset).toInt()
            val flags = d.u16(p); p += 2
            val r = doubleArrayOf(d.s16(p) / 4096.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0); p += 2
            val t = doubleArrayOf(0.0, 0.0, 0.0)
            val s = doubleArrayOf(1.0, 1.0, 1.0)
            if (flags and 1 == 0) { for (i in 0..2) t[i] = d.s32(p + i * 4) / 4096.0; p += 12 }
            if (flags and 2 == 0) {
                if (flags and 8 != 0) {
                    // Compressed rotation: two values fill the 2x2 minor around a +-1 pivot cell.
                    val a = d.s16(p) / 4096.0
                    val b = d.s16(p + 2) / 4096.0
                    p += 4
                    val c = if (flags and 0x200 != 0) -b else b
                    val dd = if (flags and 0x400 != 0) -a else a
                    val pivot = (flags shr 4) and 0xF
                    if (pivot <= 8) {
                        val row = pivot / 3
                        val col = pivot % 3
                        val values = doubleArrayOf(a, b, c, dd)
                        var k = 0
                        for (i in 0..2) for (j in 0..2) r[i * 3 + j] = if (j == col || i == row) 0.0 else values[k++]
                        r[row * 3 + col] = if (flags and 0x100 != 0) -1.0 else 1.0
                    }
                } else {
                    for (i in 1..8) r[i] = d.s16(p + (i - 1) * 2) / 4096.0
                    p += 16
                }
            }
            if (flags and 4 == 0) for (i in 0..2) s[i] = d.s32(p + i * 4) / 4096.0
            Node(t, s, r)
        }

        /** The node a shape hangs from and the material it uses, from the render command bytecode. */
        private fun walkRenderCommands(d: ByteArray, start: Int, nodeCount: Int, parents: IntArray, shapeNode: MutableMap<Int, Int>, shapeMaterial: MutableMap<Int, Int>) {
            val stack = HashMap<Int, Int>()
            var p = start
            var node = 0
            var material = 0
            while (p < d.size) {
                val op = d[p++].toInt() and 0xFF
                when (op and 0x1F) {
                    0x00 -> {}
                    0x01 -> return
                    0x02 -> { node = d[p].toInt() and 0xFF; p += 2 }
                    0x03 -> node = stack[d[p++].toInt() and 31] ?: 0
                    0x04 -> material = d[p++].toInt() and 0xFF
                    0x05 -> { val shape = d[p++].toInt() and 0xFF; shapeNode[shape] = node; shapeMaterial[shape] = material }
                    0x06 -> {
                        val id = d[p].toInt() and 0xFF
                        val parent = d[p + 1].toInt() and 0xFF
                        p += 3
                        if (id < nodeCount) { parents[id] = if (parent < nodeCount) parent else -1; node = id }
                        if (op and 0x20 != 0) stack[d[p++].toInt() and 31] = id
                        if (op and 0x40 != 0) p++
                    }
                    0x07, 0x08 -> {
                        node = d[p++].toInt() and 0xFF
                        if (op and 0x20 != 0) stack[d[p++].toInt() and 31] = node
                        if (op and 0x40 != 0) p++
                    }
                    0x09 -> p += 2 + (d[p + 1].toInt() and 0xFF) * 3
                    0x0A -> p += 8
                    0x0B -> {}
                    0x0C, 0x0D -> p += 2
                    else -> return // a command we do not know the size of
                }
            }
        }

        // A child's scale multiplies its parent's; its translation is scaled by the parent's scale, rotated into the parent's frame and offset.
        private fun compose(parent: Node, child: Node): Node {
            val scaled = DoubleArray(3) { child.translation[it] * parent.scale[it] }
            val rotated = rowTimes(parent.rotation, scaled)
            return Node(
                DoubleArray(3) { parent.translation[it] + rotated[it] },
                DoubleArray(3) { child.scale[it] * parent.scale[it] },
                DoubleArray(9) { k -> (0..2).sumOf { child.rotation[k / 3 * 3 + it] * parent.rotation[it * 3 + k % 3] } },
            )
        }

        private fun rowTimes(r: DoubleArray, v: DoubleArray) = doubleArrayOf(
            v[0] * r[0] + v[1] * r[3] + v[2] * r[6],
            v[0] * r[1] + v[1] * r[4] + v[2] * r[7],
            v[0] * r[2] + v[1] * r[5] + v[2] * r[8],
        )

        /** Parameter words each geometry command takes (the DS geometry engine's FIFO commands). */
        private val PARAMETERS = IntArray(0x80) { -1 }.also { t ->
            mapOf(
                0x00 to 0, 0x10 to 1, 0x11 to 0, 0x12 to 1, 0x13 to 1, 0x14 to 1, 0x15 to 0, 0x16 to 16, 0x17 to 12, 0x18 to 16, 0x19 to 12,
                0x1A to 9, 0x1B to 3, 0x1C to 3, 0x20 to 1, 0x21 to 1, 0x22 to 1, 0x23 to 2, 0x24 to 1, 0x25 to 1, 0x26 to 1, 0x27 to 1,
                0x28 to 1, 0x29 to 1, 0x2A to 1, 0x2B to 1, 0x30 to 1, 0x31 to 1, 0x32 to 1, 0x33 to 1, 0x34 to 32, 0x40 to 1, 0x41 to 0,
                0x50 to 1, 0x60 to 1, 0x70 to 3, 0x71 to 2, 0x72 to 1,
            ).forEach { (op, n) -> t[op] = n }
        }

        private fun signed10(v: Long): Int = ((v and 0x3FF).toInt()).let { if (it and 0x200 != 0) it - 0x400 else it }

        private fun displayList(d: ByteArray, start: Int, size: Int, positionScale: Double, node: Node?): FloatArray {
            val out = ArrayList<Float>()
            val vertex = doubleArrayOf(0.0, 0.0, 0.0)
            var s = 0.0
            var t = 0.0
            var primitive = -1
            val strip = ArrayList<DoubleArray>()

            fun place(): DoubleArray {
                var x = vertex[0]
                var y = vertex[1]
                var z = vertex[2]
                var tx = 0.0
                var ty = 0.0
                var tz = 0.0
                if (node != null) {
                    x *= node.scale[0]; y *= node.scale[1]; z *= node.scale[2]
                    val r = node.rotation
                    val rx = x * r[0] + y * r[3] + z * r[6]
                    val ry = x * r[1] + y * r[4] + z * r[7]
                    val rz = x * r[2] + y * r[5] + z * r[8]
                    x = rx; y = ry; z = rz
                    tx = node.translation[0]; ty = node.translation[1]; tz = node.translation[2]
                }
                return doubleArrayOf(x * positionScale + tx, y * positionScale + ty, z * positionScale + tz, s, t)
            }

            fun triangle(a: DoubleArray, b: DoubleArray, c: DoubleArray) {
                for (v in arrayOf(a, b, c)) for (k in 0 until 5) out += v[k].toFloat()
            }

            fun flush() {
                val v = strip
                when (primitive) {
                    0 -> for (i in 0 until v.size - 2 step 3) triangle(v[i], v[i + 1], v[i + 2])
                    1 -> for (i in 0 until v.size - 3 step 4) { triangle(v[i], v[i + 1], v[i + 2]); triangle(v[i], v[i + 2], v[i + 3]) }
                    2 -> for (i in 0 until v.size - 2) if (i % 2 == 0) triangle(v[i], v[i + 1], v[i + 2]) else triangle(v[i + 1], v[i], v[i + 2])
                    3 -> for (i in 0 until v.size - 3 step 2) { triangle(v[i], v[i + 1], v[i + 3]); triangle(v[i], v[i + 3], v[i + 2]) }
                }
                strip.clear()
            }

            var p = start
            val end = start + size
            while (p + 4 <= end) {
                val ops = IntArray(4) { d[p + it].toInt() and 0xFF }
                p += 4
                for (op in ops) {
                    when (op) {
                        0x00 -> {}
                        0x22 -> { s = d.s16(p) / 16.0; t = d.s16(p + 2) / 16.0; p += 4 }
                        0x23 -> { vertex[0] = d.s16(p) / 4096.0; vertex[1] = d.s16(p + 2) / 4096.0; vertex[2] = d.s16(p + 4) / 4096.0; p += 8; strip += place() }
                        0x24 -> { val w = d.u32(p); p += 4; vertex[0] = signed10(w) / 64.0; vertex[1] = signed10(w shr 10) / 64.0; vertex[2] = signed10(w shr 20) / 64.0; strip += place() }
                        0x25 -> { vertex[0] = d.s16(p) / 4096.0; vertex[1] = d.s16(p + 2) / 4096.0; p += 4; strip += place() }
                        0x26 -> { vertex[0] = d.s16(p) / 4096.0; vertex[2] = d.s16(p + 2) / 4096.0; p += 4; strip += place() }
                        0x27 -> { vertex[1] = d.s16(p) / 4096.0; vertex[2] = d.s16(p + 2) / 4096.0; p += 4; strip += place() }
                        0x28 -> {
                            val w = d.u32(p); p += 4
                            vertex[0] += signed10(w) / 4096.0; vertex[1] += signed10(w shr 10) / 4096.0; vertex[2] += signed10(w shr 20) / 4096.0
                            strip += place()
                        }
                        0x40 -> { flush(); primitive = (d.u32(p) and 3).toInt(); p += 4 }
                        else -> {
                            val n = PARAMETERS.getOrElse(op) { -1 }
                            if (n < 0) return out.toFloatArray() // an unknown command: its parameters cannot be skipped
                            p += n * 4
                        }
                    }
                }
            }
            flush()
            return out.toFloatArray()
        }

        private fun ByteArray.s16(at: Int): Int = u16(at).toShort().toInt()
        private fun ByteArray.s32(at: Int): Int = u32(at).toInt()
    }
}
