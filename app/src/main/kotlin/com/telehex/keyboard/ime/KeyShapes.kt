package com.telehex.keyboard.ime

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.telehex.layout.GridPoint
import com.telehex.layout.KeyShape

/** Hình phím của module layout thành Path để vẽ. */
fun KeyShape.toPath(): Path = Path().apply {
    when (val s = this@toPath) {
        is KeyShape.Polygon -> {
            s.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            close()
        }
        is KeyShape.RoundRect -> addRoundRect(RectF(s.left, s.top, s.right, s.bottom), s.radius, s.radius, Path.Direction.CW)
    }
}

fun GridPoint.toPointF(): PointF = PointF(x, y)
