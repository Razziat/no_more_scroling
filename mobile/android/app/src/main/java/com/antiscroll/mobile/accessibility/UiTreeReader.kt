package com.antiscroll.mobile.accessibility

import android.os.Build
import android.os.Trace
import android.view.accessibility.AccessibilityNodeInfo
import com.antiscroll.mobile.detection.UiNodeSnapshot
import java.util.ArrayDeque

object UiTreeReader {
    /**
     * Builds a breadth-first, flat search snapshot. Detectors only search visible
     * nodes, so retaining the original hierarchy would add allocations without
     * improving a decision. Breadth-first traversal also reaches navigation bars
     * and top-level screen markers before large feed subtrees exhaust the budget.
     */
    fun capture(root: AccessibilityNodeInfo): UiNodeSnapshot {
        Trace.beginSection("AntiScroll.captureUiTree")
        return try {
            captureSnapshot(root)
        } finally {
            Trace.endSection()
        }
    }

    private fun captureSnapshot(root: AccessibilityNodeInfo): UiNodeSnapshot {
        val budget = NodeBudget(MAX_NODES)
        val rootSnapshot = snapshotNode(root, budget) ?: return UiNodeSnapshot()
        if (budget.isExhausted) return rootSnapshot

        val queue = ArrayDeque<QueuedNode>()
        enqueueCandidates(root, depth = 1, queue = queue, budget = budget)
        val descendants = mutableListOf<UiNodeSnapshot>()

        try {
            while (queue.isNotEmpty() && !budget.isExhausted) {
                val queued = queue.removeFirst()
                try {
                    val snapshot = snapshotNode(queued.node, budget) ?: continue
                    descendants += snapshot
                    if (queued.depth < MAX_DEPTH) {
                        enqueueCandidates(
                            node = queued.node,
                            depth = queued.depth + 1,
                            queue = queue,
                            budget = budget,
                        )
                    }
                } finally {
                    recycleChildNode(queued.node)
                }
            }
        } finally {
            while (queue.isNotEmpty()) recycleChildNode(queue.removeFirst().node)
        }

        return rootSnapshot.copy(children = descendants)
    }

    private fun snapshotNode(
        node: AccessibilityNodeInfo,
        budget: NodeBudget,
    ): UiNodeSnapshot? {
        val visibleToUser = safely { node.isVisibleToUser } ?: false
        if (!visibleToUser || !budget.consume()) return null

        return UiNodeSnapshot(
            resourceId = safely { node.viewIdResourceName },
            text = safely { node.text?.toString() },
            contentDescription = safely { node.contentDescription?.toString() },
            className = safely { node.className?.toString() },
            selected = safely { node.isSelected } ?: false,
            clickable = safely { node.isClickable } ?: false,
            scrollable = safely { node.isScrollable } ?: false,
            visibleToUser = true,
        )
    }

    private fun enqueueCandidates(
        node: AccessibilityNodeInfo,
        depth: Int,
        queue: ArrayDeque<QueuedNode>,
        budget: NodeBudget,
    ) {
        val childCount = safely { node.childCount } ?: 0
        for (index in 0 until childCount) {
            if (queue.size >= budget.remainingSlots) break
            val child = safely { node.getChild(index) } ?: continue
            queue.addLast(QueuedNode(child, depth))
        }
    }

    @Suppress("DEPRECATION")
    private fun recycleChildNode(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runCatching { node.recycle() }
        }
    }

    private inline fun <T> safely(block: () -> T): T? =
        runCatching(block).getOrNull()

    private data class QueuedNode(
        val node: AccessibilityNodeInfo,
        val depth: Int,
    )

    private class NodeBudget(private var remaining: Int) {
        val isExhausted: Boolean
            get() = remaining <= 0

        val remainingSlots: Int
            get() = remaining.coerceAtLeast(0)

        fun consume(): Boolean {
            if (remaining <= 0) return false
            remaining -= 1
            return true
        }
    }

    private const val MAX_DEPTH = 18
    private const val MAX_NODES = 180
}
