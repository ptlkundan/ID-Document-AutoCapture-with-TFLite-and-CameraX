// Step 1: Define and integrate Quadrilateral class
// File: Quadrilateral.kt
package com.surendramaran.yolov8tflite

import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect

class Quadrilateral {
    var points: Array<Point> = Array(4) { Point() }

    constructor() {}

    constructor(quad: Quadrilateral?) {
        points = Array(4) { Point() }
        quad?.points?.forEachIndexed { i, p ->
            points[i] = Point(p)
        }
    }

    constructor(p1: Point, p2: Point, p3: Point, p4: Point) {
        points = arrayOf(p1, p2, p3, p4)
    }

    fun getBoundingRect(): Rect {
        val xs = points.map { it.x }
        val ys = points.map { it.y }
        return Rect(xs.minOrNull() ?: 0, ys.minOrNull() ?: 0, xs.maxOrNull() ?: 0, ys.maxOrNull() ?: 0)
    }

    override fun toString(): String {
        return "Quadrilateral(points=${points.joinToString()})"
    }

    fun transform(matrix: Matrix): Quadrilateral {
        val floatPoints = FloatArray(8)
        points.forEachIndexed { i, p ->
            floatPoints[i * 2] = p.x.toFloat()
            floatPoints[i * 2 + 1] = p.y.toFloat()
        }
        matrix.mapPoints(floatPoints)
        return Quadrilateral(
            Point(floatPoints[0].toInt(), floatPoints[1].toInt()),
            Point(floatPoints[2].toInt(), floatPoints[3].toInt()),
            Point(floatPoints[4].toInt(), floatPoints[5].toInt()),
            Point(floatPoints[6].toInt(), floatPoints[7].toInt())
        )
    }
}
