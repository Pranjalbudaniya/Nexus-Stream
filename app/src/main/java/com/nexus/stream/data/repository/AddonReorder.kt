package com.nexus.stream.data.repository

object AddonReorder {
    fun reorder(
        urls: List<String>,
        url: String,
        toPosition: Int,
    ): List<String> {
        val currentIndex = urls.indexOf(url)
        val targetPosition = if (urls.isNotEmpty()) toPosition.coerceIn(0, urls.lastIndex) else -1
        if (currentIndex == -1 || currentIndex == targetPosition) {
            return urls
        }
        val mutable = urls.toMutableList()
        mutable.removeAt(currentIndex)
        mutable.add(targetPosition, url)
        return mutable
    }
}

fun reorder(
    urls: List<String>,
    url: String,
    toPosition: Int,
): List<String> = AddonReorder.reorder(urls, url, toPosition)
