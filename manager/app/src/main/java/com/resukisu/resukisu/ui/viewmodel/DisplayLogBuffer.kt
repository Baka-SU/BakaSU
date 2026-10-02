package com.resukisu.resukisu.ui.viewmodel

/** A bounded display tail; the separately saved action log remains complete. */
internal class DisplayLogBuffer(private val limit: Int) {
    private val text = StringBuilder()
    var truncated = false
        private set

    fun clear() {
        text.clear()
        truncated = false
    }

    fun append(line: String) {
        text.append(line)
        if (text.length > limit) {
            val excess = text.length - limit
            val nextLine = text.indexOf("\n", excess)
            text.delete(0, if (nextLine >= 0 && nextLine < text.lastIndex) nextLine + 1 else excess)
            truncated = true
        }
    }

    fun snapshot(): String = text.toString()
}
