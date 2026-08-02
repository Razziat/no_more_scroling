package com.antiscroll.mobile.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.antiscroll.mobile.detection.UiNodeSnapshot

object UiTreeReader {
    fun capture(root: AccessibilityNodeInfo): UiNodeSnapshot {
        val budget = NodeBudget(MAX_NODES)
        return captureNode(root, depth = 0, budget = budget) ?: UiNodeSnapshot()
    }

    private fun captureNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        budget: NodeBudget,
    ): UiNodeSnapshot? {
        if (!budget.consume()) return null

        val children = if (depth < MAX_DEPTH) {
            buildList {
                val childCount = safely { node.childCount } ?: 0
                for (index in 0 until childCount) {
                    val child = safely { node.getChild(index) } ?: continue
                    captureNode(child, depth + 1, budget)?.let(::add)
                    if (budget.isExhausted) break
                }
            }
        } else {
            emptyList()
        }

        return UiNodeSnapshot(
            resourceId = safely { node.viewIdResourceName },
            text = safely { node.text?.toString() },
            contentDescription = safely { node.contentDescription?.toString() },
            className = safely { node.className?.toString() },
            selected = safely { node.isSelected } ?: false,
            clickable = safely { node.isClickable } ?: false,
            scrollable = safely { node.isScrollable } ?: false,
            visibleToUser = safely { node.isVisibleToUser } ?: false,
            children = children,
        )
    }

    private inline fun <T> safely(block: () -> T): T? =
        runCatching(block).getOrNull()

    private class NodeBudget(private var remaining: Int) {
        val isExhausted: Boolean
            get() = remaining <= 0

        fun consume(): Boolean {
            if (remaining <= 0) return false
            remaining -= 1
            return true
        }
    }

    private const val MAX_DEPTH = 18
    private const val MAX_NODES = 350
}
