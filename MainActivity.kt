package com.example.screenguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    private lateinit var list: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val ctx = this

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val sw = Switch(ctx).apply {
            text = "ব্লক চালু"
            isChecked = Store.enabled(ctx)
            setOnCheckedChangeListener { _, v -> Store.setEnabled(ctx, v) }
        }

        val acc = Button(ctx).apply {
            text = "Accessibility চালু করুন"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val input = EditText(ctx).apply { hint = "নতুন ট্যাগ লিখুন" }

        val add = Button(ctx).apply {
            text = "ট্যাগ যোগ করুন"
            setOnClickListener {
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    val l = Store.words(ctx)
                    if (!l.contains(t)) {
                        l.add(t)
                        Store.save(ctx, l)
                    }
                    input.setText("")
                    refresh()
                }
            }
        }

        list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        root.addView(sw)
        root.addView(acc)
        root.addView(input)
        root.addView(add)
        root.addView(list)
        setContentView(ScrollView(ctx).apply { addView(root) })
        refresh()
    }

    // ট্যাগ লিস্ট স্ক্রিনে দেখানো, ✕ চাপলে মুছে যাবে
    private fun refresh() {
        val ctx = this
        list.removeAllViews()
        for (w in Store.words(ctx)) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val tv = TextView(ctx).apply {
                text = w
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val del = Button(ctx).apply {
                text = "✕"
                setOnClickListener {
                    val l = Store.words(ctx)
                    l.remove(w)
                    Store.save(ctx, l)
                    refresh()
                }
            }
            row.addView(tv)
            row.addView(del)
            list.addView(row)
        }
    }
}
