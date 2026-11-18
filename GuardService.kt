package com.example.screenguard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast

class GuardService : AccessibilityService() {

    private var last = 0L
    private var lastPkg = ""
    private var lastTs = 0L
    private var reel = false
    private val hits = mutableListOf<Long>()
    private val cool = mutableMapOf<String, Long>()
    private var overlay: TextView? = null
    private val h = Handler(Looper.getMainLooper())

    // রিলস/শর্টস ধরার চেষ্টা (পরীক্ষামূলক, অ্যাপ আপডেটে বদলে যেতে পারে)
    private val reelPkgs = setOf(
        "com.google.android.youtube", "com.facebook.katana", "com.instagram.android"
    )
    private val reelIds = listOf("reel_player", "shorts_player", "reels_viewer", "clips_viewer")

    private val quotes = listOf(
        "একটা গভীর শ্বাস নাও। এই মুহূর্তটা পেরিয়ে গেলেই সহজ হয়ে যাবে।",
        "তুমি নিজের কাছে কথা দিয়েছিলে। কথা রাখো।",
        "আজকের জয়টাই কালকের শক্তি।",
        "উঠে হাঁটো, পানি খাও, অন্য কিছু করো।"
    )

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!Store.enabled(this)) return
        val now = System.currentTimeMillis()

        trackUsage(pkg, now)
        if (now - last < 2500) return

        var reason: String? = null
        if (Store.apps(this, "A").contains(pkg)) {
            reason = "app"
        } else if (Store.apps(this, "B").contains(pkg) &&
            (Store.nightNow(this) || Store.overLimit(this))) {
            reason = "limit"
        } else if ((cool[pkg] ?: 0L) > now) {
            reason = "cool"
        } else {
            val root = rootInActiveWindow
            if (root != null) {
                reel = false
                val sb = StringBuilder()
                collect(root, sb, root.packageName?.toString() ?: pkg)
                if (Store.reelOn(this) && reel) {
                    reason = "reel"
                } else {
                    var text = norm(sb.toString())
                    // ছাড়ের শব্দ আগে বাদ দিয়ে নেওয়া
                    for (w in Store.whitelist(this).map { norm(it) }.filter { it.isNotEmpty() }) {
                        text = text.replace(w, " ")
                    }
                    val tags = Store.tags(this)
                        .filter { Store.catOn(this, it.first) }
                        .map { norm(it.second) }.filter { it.isNotEmpty() }
                    if (tags.any { text.contains(it) }) reason = "text"
                }
            }
        }
        if (reason != null) block(pkg, reason, now)
    }

    // সীমিত অ্যাপে কত সময় কাটছে (আনুমানিক)
    private fun trackUsage(pkg: String, now: Long) {
        if (pkg == lastPkg && now - lastTs in 1..30000 && Store.apps(this, "B").contains(pkg)) {
            Store.addUsage(this, now - lastTs)
        }
        lastPkg = pkg
        lastTs = now
    }

    private fun block(pkg: String, reason: String, now: Long) {
        last = now
        Store.addBlock(this)
        vibrate()

        val textual = reason == "text" || reason == "reel"
        if (textual) {
            // ১০ মিনিটে ৩ বার ধরা পড়লে ওই অ্যাপ ১০ মিনিটের জন্য বন্ধ
            hits.add(now)
            hits.removeAll { now - it > 600000L }
            if (hits.size >= 3) {
                cool[pkg] = now + 600000L
                hits.clear()
                toast("৩ বার ধরা পড়েছে, এই অ্যাপ ১০ মিনিট বন্ধ")
            }
        }

        var mode = Store.mode(this)
        if (!textual && (mode == 2 || mode == 4)) mode = 1

        when (mode) {
            0 -> { showOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) }
            1 -> performGlobalAction(GLOBAL_ACTION_HOME)
            2 -> performGlobalAction(GLOBAL_ACTION_BACK)
            3 -> {
                if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                else performGlobalAction(GLOBAL_ACTION_HOME)
            }
            else -> toast("সতর্কতা: এটা দেখা ঠিক হচ্ছে না")
        }

        if (mode != 0 && mode != 4) {
            toast(
                when (reason) {
                    "app" -> "এই অ্যাপ ব্লক করা আছে"
                    "limit" -> "রাতের মোড বা আজকের সময় শেষ"
                    "cool" -> "কুলডাউন চলছে"
                    else -> "এটা ব্লক করা হয়েছে"
                }
            )
        }
    }

    // ফুল স্ক্রিন পর্দা (অতিরিক্ত permission লাগে না)
    private fun showOverlay() {
        if (overlay != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#102030"))
            setPadding(60, 60, 60, 60)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        try { wm.addView(tv, lp) } catch (ex: Exception) { return }
        overlay = tv

        val msg = quotes.random()
        var n = 5
        val tick = object : Runnable {
            override fun run() {
                if (n <= 0) {
                    try { wm.removeView(tv) } catch (ex: Exception) {}
                    overlay = null
                    return
                }
                tv.text = "এটা এখন ব্লক করা হয়েছে\n\n$msg\n\n$n"
                n--
                h.postDelayed(this, 1000)
            }
        }
        h.post(tick)
    }

    private fun vibrate() {
        try {
            val v = if (Build.VERSION.SDK_INT >= 31)
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (ex: Exception) {}
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder, pkg: String) {
        if (n == null) return
        n.text?.let { sb.append(it).append(' ') }
        n.contentDescription?.let { sb.append(it).append(' ') }
        if (reelPkgs.contains(pkg)) {
            val id = n.viewIdResourceName
            if (id != null && reelIds.any { id.contains(it, true) }) reel = true
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), sb, pkg)
    }

    private fun norm(s: String) = s.lowercase()
        .replace('0', 'o').replace('1', 'i').replace('3', 'e')
        .replace('@', 'a').replace('$', 's')
        .replace(Regex("[^\\p{L}\\p{M}\\s]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        h.removeCallbacksAndMessages(null)
        val o = overlay
        if (o != null) {
            try { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(o) } catch (ex: Exception) {}
            overlay = null
        }
    }
}
