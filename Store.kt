package com.example.screenguard

import android.content.Context

// ট্যাগ লিস্ট ও অন/অফ সেটিং ফোনের ভেতরে সেভ রাখে
object Store {
    private val DEFAULT = listOf(
        "choti golpo", "choti", "porn", "xxx", "xnxx", "xvideos", "nude",
        "চটি", "পর্ন", "যৌন গল্প", "সেক্স"
    )

    private fun p(c: Context) = c.getSharedPreferences("sg", Context.MODE_PRIVATE)

    fun words(c: Context): MutableList<String> {
        val s = p(c).getString("words", null) ?: return DEFAULT.toMutableList()
        return s.split("\n").filter { it.isNotBlank() }.toMutableList()
    }

    fun save(c: Context, list: List<String>) {
        p(c).edit().putString("words", list.joinToString("\n")).apply()
    }

    fun enabled(c: Context) = p(c).getBoolean("on", true)

    fun setEnabled(c: Context, v: Boolean) {
        p(c).edit().putBoolean("on", v).apply()
    }
}
