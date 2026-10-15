package io.github.romulofer.foxhg.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.romulofer.foxhg.core.data.event.EventFile
import io.github.romulofer.foxhg.core.data.event.EventKind
import io.github.romulofer.foxhg.core.data.map.Bdhc
import io.github.romulofer.foxhg.core.data.map.Collision
import io.github.romulofer.foxhg.core.data.map.MapFile
import io.github.romulofer.foxhg.core.data.map.MapUsage
import io.github.romulofer.foxhg.core.graphics.Render3d

/** An event of the map as a point in a 3D picture: pixel x and y of the picture (not of the canvas). */
internal class Marker(val kind: EventKind, val index: Int, val x: Double, val y: Double)

/** Height above the ground at which a marker's head is drawn, in tiles. */
private const val HEAD = 1.0

/**
 * The events of the map's cell that can be seen in [picture], one marker each. [heights] is the ground height per tile
 * ([io.github.romulofer.foxhg.core.graphics.MapScenes.heightGrid]). Events hidden behind a building or off screen are left out.
 */
internal fun eventMarkers(picture: Render3d, events: EventFile, usage: MapUsage, heights: FloatArray): List<Marker> {
    val out = ArrayList<Marker>()
    fun add(kind: EventKind, index: Int, x: Int, y: Int, width: Int = 1, height: Int = 1) {
        if (x / MapFile.SIZE != usage.x || y / MapFile.SIZE != usage.y) return
        val tx = x % MapFile.SIZE
        val ty = y % MapFile.SIZE
        val ground = heights[ty * MapFile.SIZE + tx].toDouble()
        val wx = tx - 16 + width / 2.0
        val wz = ty - 16 + height / 2.0
        if (!picture.isVisible(wx, ground + 0.3, wz, tolerance = 1.2)) return
        val p = picture.project(wx, ground + if (kind == EventKind.OVERWORLD) HEAD else 0.1, wz) ?: return
        out += Marker(kind, index, p[0], p[1])
    }
    events.overworlds.forEachIndexed { i, o -> add(EventKind.OVERWORLD, i, o.x, o.y) }
    events.warps.forEachIndexed { i, w -> add(EventKind.WARP, i, w.x, w.y) }
    events.triggers.forEachIndexed { i, t -> add(EventKind.TRIGGER, i, t.x, t.y, t.width.coerceAtLeast(1), t.height.coerceAtLeast(1)) }
    events.spawnables.forEachIndexed { i, s -> add(EventKind.SIGN, i, s.x, s.y) }
    return out
}

/** The marker nearest to the picture point ([x], [y]) within [reach] pixels, preferring objects. */
internal fun markerAt(markers: List<Marker>, x: Double, y: Double, reach: Double): Marker? =
    markers.filter { Math.hypot(it.x - x, it.y - y) <= reach }.minByOrNull { Math.hypot(it.x - x, it.y - y) }

private fun color(kind: EventKind, trainer: Boolean) = when (kind) {
    EventKind.OVERWORLD -> if (trainer) Color(0xFFE53E3E) else Color(0xFF2B6CB0)
    EventKind.WARP -> Color(0xFF6B46C1)
    EventKind.TRIGGER -> Color(0xFF2F855A)
    EventKind.SIGN -> Color(0xFF975A16)
}

/** Draws the blocked tiles of [map] as red patches on the ground; [scale] turns picture pixels into canvas pixels. */
internal fun DrawScope.drawCollision(picture: Render3d, map: MapFile, heights: FloatArray, scale: Float) {
    for (ty in 0 until MapFile.SIZE) for (tx in 0 until MapFile.SIZE) {
        if (!Collision.isBlocked(map.collision(tx, ty))) continue
        val h = heights[ty * MapFile.SIZE + tx].toDouble() + 0.04
        val x = tx - 16.0
        val z = ty - 16.0
        if (!picture.isVisible(x + 0.5, h, z + 0.5, tolerance = 0.8)) continue
        val corners = listOf(x to z, x + 1 to z, x + 1 to z + 1, x to z + 1).mapNotNull { (cx, cz) -> picture.project(cx, h, cz) }
        if (corners.size < 4) continue
        val path = Path().apply {
            moveTo((corners[0][0] * scale).toFloat(), (corners[0][1] * scale).toFloat())
            for (c in corners.drop(1)) lineTo((c[0] * scale).toFloat(), (c[1] * scale).toFloat())
            close()
        }
        drawPath(path, Color(0x88C53030))
    }
}

/** Draws the flat plates of the walking surface as green patches at their height; sloped plates are left out (their height formula is not known yet). */
internal fun DrawScope.drawSurface(picture: Render3d, surface: Bdhc, scale: Float) {
    for (plate in surface.plates) {
        val h = surface.flatHeight(plate) ?: continue
        val r = surface.rectangle(plate) ?: continue
        val x0 = minOf(r[0], r[2]).coerceIn(-16.0, 16.0)
        val x1 = maxOf(r[0], r[2]).coerceIn(-16.0, 16.0)
        val z0 = minOf(r[1], r[3]).coerceIn(-16.0, 16.0)
        val z1 = maxOf(r[1], r[3]).coerceIn(-16.0, 16.0)
        if (x1 <= x0 || z1 <= z0) continue
        if (!picture.isVisible((x0 + x1) / 2, h + 0.05, (z0 + z1) / 2, tolerance = 1.0)) continue
        val corners = listOf(x0 to z0, x1 to z0, x1 to z1, x0 to z1).mapNotNull { (cx, cz) -> picture.project(cx, h + 0.05, cz) }
        if (corners.size < 4) continue
        val path = Path().apply {
            moveTo((corners[0][0] * scale).toFloat(), (corners[0][1] * scale).toFloat())
            for (c in corners.drop(1)) lineTo((c[0] * scale).toFloat(), (c[1] * scale).toFloat())
            close()
        }
        drawPath(path, Color(0x4438A169))
        drawPath(path, Color(0xAA2F855A), style = Stroke(1.5f))
    }
}

/** Draws the markers: a pin with a round head for objects, flat patches for warps and triggers, a small dot for signs; the selected one has a yellow ring. */
internal fun DrawScope.drawMarkers(markers: List<Marker>, events: EventFile, selected: Pair<EventKind, Int>?, scale: Float) {
    for (m in markers) {
        val c = Offset((m.x * scale).toFloat(), (m.y * scale).toFloat())
        val trainer = m.kind == EventKind.OVERWORLD && events.overworlds.getOrNull(m.index)?.type == 1
        val tint = color(m.kind, trainer)
        when (m.kind) {
            EventKind.OVERWORLD -> {
                drawLine(tint, c, Offset(c.x, c.y + 14f), 2f)
                drawCircle(tint, 7f, c)
                drawCircle(Color.White, 7f, c, style = Stroke(1.5f))
            }
            EventKind.WARP -> drawCircle(tint, 6f, c)
            EventKind.TRIGGER -> drawCircle(tint, 6f, c, style = Stroke(2.5f))
            EventKind.SIGN -> drawCircle(tint, 4f, c)
        }
        if (selected == m.kind to m.index) drawCircle(Color(0xFFFFC107), 11f, c, style = Stroke(3f))
    }
}
