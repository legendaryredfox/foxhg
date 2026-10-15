package io.github.romulofer.foxhg.core.graphics

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Where the viewer stands: it looks at ([targetX], [targetY], [targetZ]) from [distance] away, turned [yaw] radians
 * around the vertical axis (0 = from the south, looking north) and tilted [pitch] radians up from the ground (pi/2 is
 * straight down). Units are map tiles; y is up.
 */
class Camera3d(
    val yaw: Double = 0.0,
    val pitch: Double = 0.9,
    val distance: Double = 38.0,
    val targetX: Double = 0.0,
    val targetY: Double = 0.0,
    val targetZ: Double = 0.0,
    /** Vertical field of view in radians. */
    val fov: Double = 0.7,
) {
    fun with(
        yaw: Double = this.yaw,
        pitch: Double = this.pitch,
        distance: Double = this.distance,
        targetX: Double = this.targetX,
        targetZ: Double = this.targetZ,
    ) = Camera3d(yaw, pitch.coerceIn(0.05, Math_PI_2), distance.coerceIn(2.0, 150.0), targetX, targetY, targetZ, fov)

    /** The camera moved over the ground by ([right], [forward]) tiles, as seen from where it looks now. */
    fun panned(right: Double, forward: Double): Camera3d = with(
        targetX = targetX + right * cos(yaw) - forward * sin(yaw),
        targetZ = targetZ - right * sin(yaw) - forward * cos(yaw),
    )

    private companion object {
        const val Math_PI_2 = 1.5707963267948966
    }
}

/**
 * Triangles that share a texture: per vertex x, y, z (tiles, y up) and s, t (0..1 over the texture, outside it when
 * the texture repeats), 15 floats per triangle.
 */
class Mesh3d(
    val image: RgbaImage,
    val vertices: FloatArray,
    val repeatS: Boolean,
    val repeatT: Boolean,
    val flipS: Boolean,
    val flipT: Boolean,
    /** Added to every vertex, so one model can stand in several places without a copy. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
    /** What the pixels of this mesh stand for in [Render3d.tagAt]: -1 terrain, 0 and up the building with that number. */
    val tag: Int = -1,
) {
    /** The same triangles standing at ([x], [y], [z]) as thing [tag]. */
    fun placed(x: Double, y: Double, z: Double, tag: Int) = Mesh3d(image, vertices, repeatS, repeatT, flipS, flipT, x.toFloat(), y.toFloat(), z.toFloat(), tag)
}

/** A rendered picture and what is at each pixel: see [tagAt] and [pointAt]. */
class Render3d internal constructor(
    val image: RgbaImage,
    private val tags: IntArray,
    private val depth: FloatArray,
    private val basis: DoubleArray, // eye xyz, right xyz, up xyz, forward xyz, focal, cx, cy
) {
    /** The [Mesh3d.tag] drawn at pixel ([x], [y]), or [NOTHING] where nothing was. */
    fun tagAt(x: Int, y: Int): Int = if (x in 0 until image.width && y in 0 until image.height) tags[y * image.width + x] else NOTHING

    /** The place in the world (tiles, y up) that pixel ([x], [y]) shows, or null where nothing was drawn. */
    fun pointAt(x: Int, y: Int): DoubleArray? {
        if (x !in 0 until image.width || y !in 0 until image.height) return null
        val inverse = depth[y * image.width + x].takeIf { it > 0f } ?: return null
        val z = 1.0 / inverse
        val xv = (x + 0.5 - basis[13]) * z / basis[12]
        val yv = -(y + 0.5 - basis[14]) * z / basis[12]
        return DoubleArray(3) { basis[it] + basis[3 + it] * xv + basis[6 + it] * yv + basis[9 + it] * z }
    }

    /**
     * Where the world point ([x], [y], [z]) (tiles, y up) falls in this picture: pixel x, pixel y and the distance along
     * the view direction; null when it is behind the camera.
     */
    fun project(x: Double, y: Double, z: Double): DoubleArray? {
        val dx = x - basis[0]; val dy = y - basis[1]; val dz = z - basis[2]
        val zv = dx * basis[9] + dy * basis[10] + dz * basis[11]
        if (zv < 0.2) return null
        val xv = dx * basis[3] + dy * basis[4] + dz * basis[5]
        val yv = dx * basis[6] + dy * basis[7] + dz * basis[8]
        return doubleArrayOf(basis[13] + basis[12] * xv / zv, basis[14] - basis[12] * yv / zv, zv)
    }

    /** Whether the point is in view and not hidden behind something drawn nearer ([tolerance] tiles of slack). */
    fun isVisible(x: Double, y: Double, z: Double, tolerance: Double = 0.5): Boolean {
        val p = project(x, y, z) ?: return false
        val px = p[0].toInt()
        val py = p[1].toInt()
        if (px !in 0 until image.width || py !in 0 until image.height) return false
        val inverse = depth[py * image.width + px]
        return inverse <= 0f || p[2] <= 1.0 / inverse + tolerance
    }

    companion object {
        const val NOTHING = -2
    }
}

/**
 * A software renderer: perspective projection, a depth buffer, perspective correct texture mapping and flat shading from
 * a fixed light (faces turned towards the light are brighter; there is no lighting data in the models we read). Pixels
 * nothing was drawn on stay transparent. Slow compared to a GPU but pure JVM, and fast enough for one map.
 */
object Rasterizer3d {
    private const val NEAR = 0.2f
    private val LIGHT = floatArrayOf(0.35f, 0.85f, 0.4f).let { l -> val n = sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2]); floatArrayOf(l[0] / n, l[1] / n, l[2] / n) }

    /** Draws [meshes]; the pixels of the mesh tagged [highlight] get an orange tint. */
    fun render(meshes: List<Mesh3d>, camera: Camera3d, width: Int, height: Int, highlight: Int = Render3d.NOTHING): Render3d {
        val pixels = IntArray(width * height)
        val tags = IntArray(width * height) { Render3d.NOTHING }
        val depth = FloatArray(width * height) // 1 / distance along the view direction; bigger is nearer, 0 is nothing

        val eyeX = camera.targetX + camera.distance * sin(camera.yaw) * cos(camera.pitch)
        val eyeY = camera.targetY + camera.distance * sin(camera.pitch)
        val eyeZ = camera.targetZ + camera.distance * cos(camera.yaw) * cos(camera.pitch)
        var fx = camera.targetX - eyeX
        var fy = camera.targetY - eyeY
        var fz = camera.targetZ - eyeZ
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fl; fy /= fl; fz /= fl
        // right = forward x up(0,1,0)
        var rx = -fz
        var rz = fx
        val rl = sqrt(rx * rx + rz * rz)
        rx /= rl; rz /= rl
        // up = right x forward
        val ux = -rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy
        val focal = (height / 2.0 / tan(camera.fov / 2)).toFloat()
        val cx = width / 2f
        val cy = height / 2f

        // A clipped vertex: view space x, y, z and texture s, t.
        val poly = FloatArray(5 * 8)
        val clipped = FloatArray(5 * 8)

        for (mesh in meshes) {
            val v = mesh.vertices
            for (t in 0 until v.size / 15) {
                val o = t * 15
                // The face normal in world space, for shading.
                val ax = v[o + 5] - v[o]; val ay = v[o + 6] - v[o + 1]; val az = v[o + 7] - v[o + 2]
                val bx = v[o + 10] - v[o]; val by = v[o + 11] - v[o + 1]; val bz = v[o + 12] - v[o + 2]
                var nx = ay * bz - az * by
                var ny = az * bx - ax * bz
                var nz = ax * by - ay * bx
                val nl = sqrt(nx * nx + ny * ny + nz * nz)
                if (nl < 1e-9f) continue
                nx /= nl; ny /= nl; nz /= nl
                val shade = 0.6f + 0.4f * abs(nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2])

                for (k in 0 until 3) {
                    val dx = v[o + k * 5] + mesh.offsetX - eyeX.toFloat()
                    val dy = v[o + k * 5 + 1] + mesh.offsetY - eyeY.toFloat()
                    val dz = v[o + k * 5 + 2] + mesh.offsetZ - eyeZ.toFloat()
                    poly[k * 5] = dx * rx.toFloat() + dz * rz.toFloat()
                    poly[k * 5 + 1] = dx * ux.toFloat() + dy * uy.toFloat() + dz * uz.toFloat()
                    poly[k * 5 + 2] = dx * fx.toFloat() + dy * fy.toFloat() + dz * fz.toFloat()
                    poly[k * 5 + 3] = v[o + k * 5 + 3]
                    poly[k * 5 + 4] = v[o + k * 5 + 4]
                }
                val count = clipNear(poly, clipped)
                if (count < 3) continue
                for (i in 1 until count - 1) fill(mesh, clipped, 0, i, i + 1, focal, cx, cy, width, height, pixels, depth, tags, shade, mesh.tag == highlight)
            }
        }
        val basis = doubleArrayOf(eyeX, eyeY, eyeZ, rx, 0.0, rz, ux, uy, uz, fx, fy, fz, focal.toDouble(), cx.toDouble(), cy.toDouble())
        return Render3d(RgbaImage(width, height, pixels), tags, depth, basis)
    }

    /** Cuts the triangle in [src] (three vertices of five floats) at the near plane; returns the vertex count of the polygon left in [dst]. */
    private fun clipNear(src: FloatArray, dst: FloatArray): Int {
        var n = 0
        for (i in 0 until 3) {
            val a = i * 5
            val b = ((i + 1) % 3) * 5
            val aIn = src[a + 2] >= NEAR
            val bIn = src[b + 2] >= NEAR
            if (aIn) { for (k in 0 until 5) dst[n * 5 + k] = src[a + k]; n++ }
            if (aIn != bIn) {
                val f = (NEAR - src[a + 2]) / (src[b + 2] - src[a + 2])
                for (k in 0 until 5) dst[n * 5 + k] = src[a + k] + (src[b + k] - src[a + k]) * f
                n++
            }
        }
        return n
    }

    private fun fill(
        mesh: Mesh3d, p: FloatArray, i0: Int, i1: Int, i2: Int, focal: Float, cx: Float, cy: Float, width: Int, height: Int,
        pixels: IntArray, depth: FloatArray, tags: IntArray, shade: Float, highlighted: Boolean,
    ) {
        val idx = intArrayOf(i0 * 5, i1 * 5, i2 * 5)
        val sx = FloatArray(3) { cx + focal * p[idx[it]] / p[idx[it] + 2] }
        val sy = FloatArray(3) { cy - focal * p[idx[it] + 1] / p[idx[it] + 2] }
        val invZ = FloatArray(3) { 1f / p[idx[it] + 2] }
        val us = FloatArray(3) { p[idx[it] + 3] * invZ[it] }
        val vs = FloatArray(3) { p[idx[it] + 4] * invZ[it] }
        val den = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2])
        if (abs(den) < 1e-6f) return
        val x0 = max(0, floor(min(sx[0], min(sx[1], sx[2]))).toInt())
        val x1 = min(width - 1, ceil(max(sx[0], max(sx[1], sx[2]))).toInt())
        val y0 = max(0, floor(min(sy[0], min(sy[1], sy[2]))).toInt())
        val y1 = min(height - 1, ceil(max(sy[0], max(sy[1], sy[2]))).toInt())
        val image = mesh.image
        for (y in y0..y1) for (x in x0..x1) {
            val px = x + 0.5f
            val py = y + 0.5f
            val a = ((sy[1] - sy[2]) * (px - sx[2]) + (sx[2] - sx[1]) * (py - sy[2])) / den
            val b = ((sy[2] - sy[0]) * (px - sx[2]) + (sx[0] - sx[2]) * (py - sy[2])) / den
            val c = 1 - a - b
            if (a < -1e-4f || b < -1e-4f || c < -1e-4f) continue
            val iz = a * invZ[0] + b * invZ[1] + c * invZ[2]
            val at = y * width + x
            if (iz <= depth[at]) continue
            val u = wrap((a * us[0] + b * us[1] + c * us[2]) / iz, mesh.repeatS, mesh.flipS)
            val w = wrap((a * vs[0] + b * vs[1] + c * vs[2]) / iz, mesh.repeatT, mesh.flipT)
            val color = image.argb[min(image.height - 1, (w * image.height).toInt()) * image.width + min(image.width - 1, (u * image.width).toInt())]
            if (color ushr 24 < 0x80) continue
            depth[at] = iz
            tags[at] = mesh.tag
            var r = ((color shr 16 and 0xFF) * shade).toInt()
            var g = ((color shr 8 and 0xFF) * shade).toInt()
            var bl = ((color and 0xFF) * shade).toInt()
            if (highlighted) { r = (r * 6 + 0xFF * 4) / 10; g = (g * 6 + 0xA0 * 4) / 10; bl = (bl * 6 + 0x30 * 4) / 10 }
            pixels[at] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
    }

    private fun wrap(v: Float, repeat: Boolean, flip: Boolean): Float {
        if (!repeat) return v.coerceIn(0f, 0.9999f)
        val whole = floor(v)
        val frac = v - whole
        return if (flip && whole.toInt() % 2 != 0) 1f - frac else frac
    }
}
