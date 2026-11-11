package com.telehex.platform

/** Binary min-heap; retains only the requested number of English completions. */
internal class PriorityQueue<T>(private val comparator: Comparator<T>) {
    private val heap = ArrayList<T>()
    val size: Int get() = heap.size
    fun peek(): T = heap.first()
    fun add(value: T) {
        heap.add(value)
        var child = heap.lastIndex
        while (child > 0) {
            val parent = (child - 1) / 2
            if (comparator.compare(heap[parent], heap[child]) <= 0) break
            val old = heap[parent]; heap[parent] = heap[child]; heap[child] = old
            child = parent
        }
    }
    fun poll(): T? {
        if (heap.isEmpty()) return null
        val first = heap.first()
        val last = heap.removeAt(heap.lastIndex)
        if (heap.isEmpty()) return first
        heap[0] = last
        var parent = 0
        while (parent * 2 + 1 < heap.size) {
            var child = parent * 2 + 1
            if (child + 1 < heap.size && comparator.compare(heap[child + 1], heap[child]) < 0) child++
            if (comparator.compare(heap[parent], heap[child]) <= 0) break
            val old = heap[parent]; heap[parent] = heap[child]; heap[child] = old
            parent = child
        }
        return first
    }
    fun sortedWith(order: Comparator<T>): List<T> = heap.sortedWith(order)
}
