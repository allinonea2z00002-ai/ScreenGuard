package com.example.screenguard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    private lateinit var box: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        setContentView(ScrollView(this).apply { addView(box) })
        val pin = Store.pin(this)
        if (pin == null) build() else askPin(pin)
    }

    // ---------- ছোট সাহায্যকারী ফাংশন ----------
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun again() { box.post { build() } }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 17f
        setPadding(0, 40, 0, 8)
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun info(t: String) = TextView(this).apply { text = t; textSize = 15f }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { f() }
    }

    private fun edit(hint: String, num: Boolean = false) = EditText(this).apply {
        this.hint = hint
        if (num) inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun sw(t: String, on: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
        text = t
        isChecked = on
        setOnCheckedChangeListener { _, v -> f(v) }
    }

    private fun spinner(items: List<String>, sel: Int, f: (Int) -> Unit) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(sel)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { f(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it) }
    }

    private fun itemRow(text: String, onDelete: () -> Unit): View {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 17f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        return row(tv, btn("✕") { onDelete() })
    }

    // ---------- PIN ----------
    private fun askPin(pin: String) {
        val et = EditText(this).apply {
            hint = "PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("PIN দিন").setView(et).setCancelable(false)
            .setPositiveButton("ঠিক আছে") { _, _ ->
                if (et.text.toString() == pin) build() else { toast("ভুল PIN"); finish() }
            }
            .setNegativeButton("বাতিল") { _, _ -> finish() }
            .show()
    }

    // ---------- অ্যাপ বাছাই ----------
    private fun pickApps(key: String, title: String) {
        val pm = packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(i, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val sel = Store.apps(this, key)
        val checked = BooleanArray(apps.size) { sel.contains(apps[it].first) }
        AlertDialog.Builder(this).setTitle(title)
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, which, isC ->
                checked[which] = isC
            }
            .setPositiveButton("সেভ") { _, _ ->
                val s = mutableSetOf<String>()
                apps.forEachIndexed { idx, a -> if (checked[idx]) s.add(a.first) }
                Store.saveApps(this, key, s)
                again()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    // ---------- মূল স্ক্রিন ----------
    private fun build() {
        val c = this
        box.removeAllViews()

        // গণনা ও স্ট্রিক
        box.addView(info("আজ ${Store.todayCount(c)} বার ব্লক • ${Store.streak(c)} দিন পরিষ্কার"))

        // মাস্টার সুইচ (বন্ধ করতে ১৫ মিনিট অপেক্ষা)
        val offAt = Store.offAt(c)
        box.addView(sw("ব্লক চালু", offAt == 0L) { v ->
            if (v) {
                Store.setOn(c)
                toast("ব্লক চালু আছে")
            } else if (Store.requestOff(c)) {
                Store.sms(c, "ScreenGuard বন্ধ করার অনুরোধ হয়েছে, ১৫ মিনিট পর বন্ধ হবে।")
                toast("১৫ মিনিট পর বন্ধ হবে। আবার চালু করলে বাতিল হবে।")
            }
            again()
        })
        if (offAt != 0L) {
            val left = offAt + Store.DELAY_MS - System.currentTimeMillis()
            box.addView(info(if (left > 0) "বন্ধ হবে ${left / 60000 + 1} মিনিট পরে" else "ব্লক এখন বন্ধ আছে"))
        }

        box.addView(btn("Accessibility চালু করুন") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        // ব্লকের ধরন
        box.addView(label("ব্লক করার ধরন"))
        box.addView(
            spinner(
                listOf("ফুল স্ক্রিন পর্দা + হোম", "শুধু হোমে পাঠানো", "ব্যাক বাটন",
                    "স্ক্রিন লক", "শুধু সতর্কতা (ভাইব্রেশন)"),
                Store.mode(c)
            ) { Store.setMode(c, it) }
        )

        // ট্যাগ
        box.addView(label("ট্যাগ (খারাপ শব্দ/সাইট)"))
        val catSp = spinner(Store.CATS.map { it.second }, 0) {}
        val inp = edit("নতুন ট্যাগ লিখুন")
        box.addView(catSp)
        box.addView(inp)
        box.addView(btn("ট্যাগ যোগ করুন") {
            val t = inp.text.toString().trim().replace(Regex("[\t\n]"), " ")
            if (t.isNotEmpty()) {
                val cat = Store.CATS[catSp.selectedItemPosition].first
                val l = Store.tags(c)
                if (!l.contains(cat to t)) {
                    l.add(cat to t)
                    Store.saveTags(c, l)
                }
                again()
            }
        })
        val all = Store.tags(c)
        for ((cat, name) in Store.CATS) {
            box.addView(sw("$name (গ্রুপ চালু)", Store.catOn(c, cat)) { Store.setCatOn(c, cat, it) })
            for (t in all.filter { it.first == cat }) {
                box.addView(itemRow(t.second) {
                    val l = Store.tags(c)
                    l.remove(t)
                    Store.saveTags(c, l)
                    again()
                })
            }
        }

        // ছাড়ের তালিকা
        box.addView(label("ছাড়ের তালিকা (এই শব্দ থাকলে ব্লক হবে না)"))
        val wi = edit("যেমন: essex")
        box.addView(wi)
        box.addView(btn("ছাড় যোগ করুন") {
            val t = wi.text.toString().trim().replace(Regex("[\t\n]"), " ")
            if (t.isNotEmpty()) {
                val l = Store.whitelist(c)
                if (!l.contains(t)) {
                    l.add(t)
                    Store.saveWhite(c, l)
                }
                again()
            }
        })
        for (w in Store.whitelist(c)) {
            box.addView(itemRow(w) {
                val l = Store.whitelist(c)
                l.remove(w)
                Store.saveWhite(c, l)
                again()
            })
        }

        // অ্যাপ ব্লক
        box.addView(label("অ্যাপ ব্লক"))
        box.addView(btn("স্থায়ী ব্লক অ্যাপ বাছুন (${Store.apps(c, "A").size})") {
            pickApps("A", "সবসময় ব্লক হবে")
        })
        box.addView(btn("সীমিত অ্যাপ বাছুন (${Store.apps(c, "B").size})") {
            pickApps("B", "রাতে ও দৈনিক সীমার পর ব্লক")
        })

        // সময়
        box.addView(label("সময়"))
        box.addView(sw("রাতের কড়া মোড (সীমিত অ্যাপ বন্ধ)", Store.nightOn(c)) { Store.setNightOn(c, it) })
        val ns = edit("রাত শুরু (ঘণ্টা ০-২৩)", true).apply { setText(Store.nightStart(c).toString()) }
        val ne = edit("রাত শেষ (ঘণ্টা ০-২৩)", true).apply { setText(Store.nightEnd(c).toString()) }
        val lim = edit("দৈনিক সীমা, মিনিট (০ = বন্ধ)", true).apply { setText(Store.limitMin(c).toString()) }
        box.addView(ns)
        box.addView(ne)
        box.addView(lim)
        box.addView(btn("সময় সেভ করুন") {
            Store.setTimes(
                c,
                ns.text.toString().toIntOrNull() ?: 0,
                ne.text.toString().toIntOrNull() ?: 5,
                lim.text.toString().toIntOrNull() ?: 0
            )
            toast("সেভ হয়েছে")
        })
        box.addView(info("আজ সীমিত অ্যাপে প্রায় ${Store.usedToday(c) / 60000} মিনিট গেছে"))

        // রিলস/শর্টস
        box.addView(label("রিলস/শর্টস"))
        box.addView(sw("YouTube Shorts / Facebook / Instagram Reels ব্লক (পরীক্ষামূলক)", Store.reelOn(c)) {
            Store.setReelOn(c, it)
        })

        // বন্ধুকে খবর
        box.addView(label("বন্ধু/অভিভাবকের নম্বর (ব্লক বন্ধ করতে চাইলে SMS যাবে)"))
        val ph = edit("ফোন নম্বর").apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setText(Store.partner(c) ?: "")
        }
        box.addView(ph)
        box.addView(btn("নম্বর সেভ করুন") {
            Store.setPartner(c, ph.text.toString().trim())
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 1)
        })

        // PIN
        box.addView(label("PIN (অ্যাপ খুলতে লাগবে)"))
        val pn = edit("নতুন PIN (সংখ্যা)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        box.addView(pn)
        box.addView(btn("PIN সেট করুন") {
            if (pn.text.length >= 4) {
                Store.setPin(c, pn.text.toString())
                toast("PIN সেট হয়েছে। এটা ভুললে অ্যাপের ডাটা মুছতে হবে।")
            } else toast("কমপক্ষে ৪ সংখ্যা দিন")
        })
        if (Store.pin(c) != null) {
            box.addView(btn("PIN মুছুন") {
                Store.setPin(c, null)
                toast("PIN মুছে গেছে")
                again()
            })
        }

        // ব্যাকআপ
        box.addView(label("ব্যাকআপ"))
        box.addView(btn("ট্যাগ ব্যাকআপ কপি করুন") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("sg", Store.backup(c)))
            toast("কপি হয়েছে, কোথাও সেভ করে রাখুন")
        })
        box.addView(btn("ক্লিপবোর্ড থেকে রিস্টোর") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (Store.restore(c, t)) {
                toast("রিস্টোর হয়েছে")
                again()
            } else toast("সঠিক ব্যাকআপ পাওয়া যায়নি")
        })
    }
}
