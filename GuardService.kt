package com.example.screenguard

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class GuardService : AccessibilityService() {

    private var last = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.packageName == packageName) return
        if (!Store.enabled(this)) return
        val now = System.currentTimeMillis()
        if (now - last < 2000) return

        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collect(root, sb)
        val text = norm(sb.toString())

        // আপনার নিজের ট্যাগ লিস্ট থেকে শব্দ নেওয়া হয়
        val tags = Store.words(this).map { norm(it) }.filter { it.isNotEmpty() }

        if (tags.any { text.contains(it) }) {
            last = now
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, "এটা ব্লক করা হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (n == null) return
        n.text?.let { sb.append(it).append(' ') }
        n.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until n.childCount) collect(n.getChild(i), sb)
    }

    private fun norm(s: String) = s.lowercase()
        .replace('0', 'o').replace('1', 'i').replace('3', 'e')
        .replace('@', 'a').replace('$', 's')
        .replace(Regex("[^\\p{L}\\p{M}\\s]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    override fun onInterrupt() {}
}
