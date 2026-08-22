package com.papersnap.app.ui

internal val CATEGORY_LABELS = mapOf(
    "cs.AI" to "人工智能",
    "cs.CV" to "计算机视觉",
    "cs.CL" to "自然语言处理",
    "cs.LG" to "机器学习",
    "cs.RO" to "机器人",
    "cs.SE" to "软件工程",
    "cs.GR" to "计算机图形学"
)

internal fun catLabel(c: String): String = CATEGORY_LABELS[c]?.let { "$c $it" } ?: c
