package com.telehex.bridge

import com.telehex.layout.GridPoint
import com.telehex.layout.TouchCell
import com.telehex.layout.TouchCells
import com.telehex.layout.TouchModel

/** Nhận chạm và học độ lệch chạm cho iOS theo [TouchModel] (quy tắc chung với Android). */
object TouchBridge {
    fun grid(cells: List<TouchCell>): TouchCells = TouchCells(cells)

    fun keyAt(grid: TouchCells, x: Float, y: Float, keyWidth: Float, offsets: Map<String, GridPoint>, validNext: ((String) -> Boolean)?): Int? =
        TouchModel.keyAt(grid, x, y, keyWidth, offsets, validNext)

    fun learn(old: GridPoint?, x: Float, y: Float, center: GridPoint, keyWidth: Float): GridPoint =
        TouchModel.learn(old, x, y, center, keyWidth)
}
