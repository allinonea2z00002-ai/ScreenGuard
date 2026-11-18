package com.example.screenguard

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

// সব সেটিং ফোনের ভেতরে সেভ থাকে (ইন্টারনেটে কিছু যায় না)
object Store {
    const val DELAY_MS = 15 * 60 * 1000L   // ব্লক বন্ধ করার অনুরোধের পর অপেক্ষা

    val CATS = listOf(
        "story" to "গল্প/লেখা",
        "site" to "সাইট",
        "gamble" to "জুয়া/বেট",
        "other" to "অন্যান্য"
    )

    private val DEFAULT = listOf(
        "story" to "choti golpo", "story" to "choti", "story" to "চটি",
        "story" to "যৌন গল্প", "story" to "সেক্স",
        "site" to "porn", "site" to "xxx", "site" to "xnxx",
        "site" to "xvideos", "site" to "পর্ন", "site" to "nude",
        "gamble" to "casino", "gamble" to "জুয়া"
    )

    private fun p(c: Context) = c.getSharedPreferences("sg", Context.MODE_PRIVATE)
    private fun today() = LocalDate.now().toString()

    // ---------- ট্যাগ ----------
    fun tags(c: Context): MutableList<Pair<String, String>> {
        val s = p(c).getString("tags", null) ?: return DEFAULT.toMutableList()
        return s.split("\n").filter { it.contains("\t") }
            .map { val a = it.split("\t", limit = 2); a[0] to a[1] }
            .filter { it.second.isNotBlank() }.toMutableList()
    }

    fun saveTags(c: Context, l: List<Pair<String, String>>) {
        p(c).edit().putString("tags", l.joinToString("\n") { it.first + "\t" + it.second }).apply()
    }

    fun catOn(c: Context, cat: String) = p(c).getBoolean("cat_$cat", true)
    fun setCatOn(c: Context, cat: String, v: Boolean) { p(c).edit().putBoolean("cat_$cat", v).apply() }

    // ---------- ছাড়ের তালিকা (whitelist) ----------
    fun whitelist(c: Context): MutableList<String> =
        (p(c).getString("white", "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()

    fun saveWhite(c: Context, l: List<String>) {
        p(c).edit().putString("white", l.joinToString("\n")).apply()
    }

    // ---------- অন/অফ (বন্ধ করতে ১৫ মিনিট দেরি) ----------
    fun offAt(c: Context) = p(c).getLong("off_at", 0L)

    fun enabled(c: Context): Boolean {
        val o = offAt(c)
        return o == 0L || System.currentTimeMillis() < o + DELAY_MS
    }

    // নতুন অনুরোধ হলে true
    fun requestOff(c: Context): Boolean {
        if (offAt(c) != 0L) return false
        p(c).edit().putLong("off_at", System.currentTimeMillis()).apply()
        return true
    }

    fun setOn(c: Context) { p(c).edit().putLong("off_at", 0L).apply() }

    // ---------- ব্লক করার ধরন ----------
    // 0 ফুল স্ক্রিন পর্দা, 1 হোম, 2 ব্যাক, 3 স্ক্রিন লক, 4 শুধু সতর্কতা
    fun mode(c: Context) = p(c).getInt("mode", 0)
    fun setMode(c: Context, v: Int) { p(c).edit().putInt("mode", v).apply() }

    // ---------- অ্যাপ লিস্ট (A = স্থায়ী ব্লক, B = সীমিত) ----------
    fun apps(c: Context, key: String): MutableSet<String> =
        p(c).getStringSet("apps_$key", null)?.toMutableSet() ?: mutableSetOf()

    fun saveApps(c: Context, key: String, s: Set<String>) {
        p(c).edit().putStringSet("apps_$key", HashSet(s)).apply()
    }

    // ---------- সময় ----------
    fun nightOn(c: Context) = p(c).getBoolean("night", false)
    fun setNightOn(c: Context, v: Boolean) { p(c).edit().putBoolean("night", v).apply() }
    fun nightStart(c: Context) = p(c).getInt("n_start", 0)
    fun nightEnd(c: Context) = p(c).getInt("n_end", 5)
    fun limitMin(c: Context) = p(c).getInt("limit", 0)

    fun setTimes(c: Context, s: Int, e: Int, lim: Int) {
        p(c).edit().putInt("n_start", s.coerceIn(0, 23)).putInt("n_end", e.coerceIn(0, 23))
            .putInt("limit", lim.coerceAtLeast(0)).apply()
    }

    fun nightNow(c: Context): Boolean {
        if (!nightOn(c)) return false
        val h = LocalTime.now().hour
        val s = nightStart(c)
        val e = nightEnd(c)
        return if (s <= e) h in s until e else (h >= s || h < e)
    }

    fun usedToday(c: Context) = p(c).getLong("use_" + today(), 0L)

    fun addUsage(c: Context, ms: Long) {
        val k = "use_" + today()
        p(c).edit().putLong(k, p(c).getLong(k, 0L) + ms).apply()
    }

    fun overLimit(c: Context): Boolean {
        val l = limitMin(c)
        return l > 0 && usedToday(c) >= l * 60000L
    }

    // ---------- রিলস/শর্টস ----------
    fun reelOn(c: Context) = p(c).getBoolean("reel", false)
    fun setReelOn(c: Context, v: Boolean) { p(c).edit().putBoolean("reel", v).apply() }

    // ---------- গণনা ও স্ট্রিক ----------
    fun addBlock(c: Context) {
        val k = "cnt_" + today()
        p(c).edit().putInt(k, p(c).getInt(k, 0) + 1).putString("last_block", today()).apply()
    }

    fun todayCount(c: Context) = p(c).getInt("cnt_" + today(), 0)

    fun streak(c: Context): Long {
        var base = p(c).getString("last_block", null)
        if (base == null) {
            base = p(c).getString("inst", null)
            if (base == null) {
                base = today()
                p(c).edit().putString("inst", base).apply()
            }
        }
        return ChronoUnit.DAYS.between(LocalDate.parse(base), LocalDate.now())
    }

    // ---------- PIN ----------
    fun pin(c: Context): String? = p(c).getString("pin", null)
    fun setPin(c: Context, v: String?) {
        if (v == null) p(c).edit().remove("pin").apply() else p(c).edit().putString("pin", v).apply()
    }

    // ---------- বন্ধুর নম্বর ও SMS ----------
    fun partner(c: Context): String? = p(c).getString("partner", null)
    fun setPartner(c: Context, v: String) {
        if (v.isBlank()) p(c).edit().remove("partner").apply() else p(c).edit().putString("partner", v).apply()
    }

    fun sms(c: Context, msg: String) {
        val n = partner(c) ?: return
        try {
            val sm = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java)
            else SmsManager.getDefault()
            sm.sendTextMessage(n, null, msg, null, null)
        } catch (e: Exception) { /* অনুমতি বা সিম না থাকলে চুপচাপ বাদ */ }
    }

    // ---------- ব্যাকআপ ----------
    fun backup(c: Context): String =
        tags(c).joinToString("\n") { "T\t" + it.first + "\t" + it.second } + "\n" +
            whitelist(c).joinToString("\n") { "W\t$it" }

    fun restore(c: Context, s: String): Boolean {
        val t = mutableListOf<Pair<String, String>>()
        val w = mutableListOf<String>()
        for (line in s.lines()) {
            val a = line.split("\t")
            if (a[0] == "T" && a.size >= 3 && a[2].isNotBlank()) t.add(a[1] to a[2])
            else if (a[0] == "W" && a.size >= 2 && a[1].isNotBlank()) w.add(a[1])
        }
        if (t.isEmpty() && w.isEmpty()) return false
        saveTags(c, t)
        saveWhite(c, w)
        return true
    }
}
