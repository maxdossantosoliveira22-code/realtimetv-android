package br.realtime.tv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class HomeTab(private val shell: ShellScreen) : Tab {
    private val act = shell.act
    override val view = ScrollView(act)
    private val col = LinearLayout(act)
    private val back = ImageView(act)
    private val info = LinearLayout(act)
    private val tagV: TextView
    private val titleV: TextView
    private val metaV: TextView
    private val plotV: TextView
    private val dots = LinearLayout(act)
    private val rows = LinearLayout(act)
    private val btnPlay: TextView
    private val btnInfo: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val rotate = Runnable { next() }
    private var vod: List<Entry> = emptyList()
    private var series: List<Entry> = emptyList()
    private var slides: List<Entry> = emptyList()
    private var slideSig = ""
    private var rowSig = ""
    private var idx = 0
    private var seq = 0
    private var loaded = false
    private var visible = false

    init {
        view.setBackgroundColor(C.BG)
        view.isVerticalScrollBarEnabled = false
        col.orientation = LinearLayout.VERTICAL
        view.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        val dm = act.resources.displayMetrics
        val land = dm.widthPixels > dm.heightPixels
        val heroH = if (land) (dm.heightPixels * 0.62f).toInt() else (dm.widthPixels * 1.05f).toInt()
        val hero = FrameLayout(act)
        back.scaleType = ImageView.ScaleType.CENTER_CROP
        hero.addView(back, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val shadeBottom = View(act)
        val gb = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x000B0F1A, 0x990B0F1A.toInt(), C.BG))
        shadeBottom.background = gb
        hero.addView(shadeBottom, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (heroH * 0.75f).toInt()).apply { gravity = Gravity.BOTTOM })
        if (land) {
            val shadeLeft = View(act)
            shadeLeft.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xF00B0F1A.toInt(), 0x990B0F1A.toInt(), 0x000B0F1A))
            hero.addView(shadeLeft, FrameLayout.LayoutParams((dm.widthPixels * 0.62f).toInt(), FrameLayout.LayoutParams.MATCH_PARENT))
        }

        info.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(if (Ui.isTv) 40 else 18)
        info.setPadding(pad, 0, pad, Ui.dp(26))
        tagV = act.label("NOVIDADE", 12f, C.CYAN, Fonts.medium)
        titleV = act.label("", if (Ui.isTv) 36f else 26f, C.WHITE, Fonts.bold, 2)
        metaV = act.label("", if (Ui.isTv) 15f else 13f, 0xFFDCE2F0.toInt(), Fonts.medium, 1)
        plotV = act.label("", if (Ui.isTv) 15f else 13f, 0xFFC3CBDF.toInt(), Fonts.regular, 3)
        info.addView(tagV)
        info.addView(titleV, lp(6))
        info.addView(metaV, lp(8))
        info.addView(plotV, lp(8))
        val btns = LinearLayout(act)
        btns.orientation = LinearLayout.HORIZONTAL
        btnPlay = act.button("▶  Assistir", true)
        btnInfo = act.button("Mais informações", false)
        btns.addView(btnPlay)
        btns.addView(btnInfo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = Ui.dp(10) })
        info.addView(btns, lp(14))
        dots.orientation = LinearLayout.HORIZONTAL
        info.addView(dots, lp(14))
        val infoW = if (land) (dm.widthPixels * 0.55f).toInt() else FrameLayout.LayoutParams.MATCH_PARENT
        hero.addView(info, FrameLayout.LayoutParams(infoW, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM or Gravity.START })
        col.addView(hero, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heroH))
        rows.orientation = LinearLayout.VERTICAL
        col.addView(rows, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(View(act), LinearLayout.LayoutParams(1, Ui.dp(40)))

        btnPlay.setOnClickListener {
            val e = cur()
            if (e != null) {
                if (e.t == "movie") {
                    val sv = Store.recentFind("movie", e.id)
                    act.play(listOf(e.copy(pos = sv?.pos ?: 0L, dur = sv?.dur ?: 0L)), 0)
                } else {
                    act.push(DetailScreen(act, e))
                }
            }
        }
        btnInfo.setOnClickListener {
            val e = cur()
            if (e != null) act.push(DetailScreen(act, e))
        }
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(top) }

    private fun cur(): Entry? = slides.getOrNull(idx)

    override fun onShow() {
        visible = true
        if (!loaded) {
            loaded = true
            vod = Store.highlights("vod")
            series = Store.highlights("series")
            render()
            async({ Api.highlights("vod") }) { r ->
                if (r != null) {
                    Store.setHighlights("vod", r)
                    vod = r
                    render()
                }
            }
            async({ Api.highlights("series") }) { r ->
                if (r != null) {
                    Store.setHighlights("series", r)
                    series = r
                    render()
                }
            }
        } else {
            render()
        }
        scheduleRotate()
    }

    override fun onHide() {
        visible = false
        handler.removeCallbacks(rotate)
    }

    private fun scheduleRotate() {
        handler.removeCallbacks(rotate)
        if (visible && slides.size > 1) handler.postDelayed(rotate, 8000)
    }

    private fun next() {
        if (slides.isEmpty()) return
        showSlide((idx + 1) % slides.size)
        scheduleRotate()
    }

    private fun render() {
        val s = ArrayList<Entry>()
        for (i in 0 until 3) {
            if (i < vod.size) s.add(vod[i])
            if (i < series.size) s.add(series[i])
        }
        val sig = s.joinToString("|") { it.key() }
        if (sig != slideSig) {
            slideSig = sig
            slides = s
            dots.removeAllViews()
            for (i in s.indices) {
                val d = View(act)
                dots.addView(d, LinearLayout.LayoutParams(Ui.dp(10), Ui.dp(4)).apply { rightMargin = Ui.dp(5) })
            }
            idx = 0
            if (s.isNotEmpty()) showSlide(0)
            scheduleRotate()
        }
        val rec = Store.recent()
        val fav = Store.favs()
        val rsig = rec.joinToString("|") { it.key() + it.pos } + "#" + fav.joinToString("|") { it.key() } + "#" + vod.size + series.size + vod.firstOrNull()?.id + series.firstOrNull()?.id
        if (rsig != rowSig) {
            rowSig = rsig
            rows.removeAllViews()
            if (rec.isNotEmpty()) addRow("Continuar assistindo", rec, true)
            if (fav.isNotEmpty()) addRow("Meus favoritos", fav, false)
            if (vod.isNotEmpty()) addRow("Novidades: Filmes", vod, false)
            if (series.isNotEmpty()) addRow("Novidades: Séries", series, false)
            if (rows.childCount == 0) {
                val t = act.label("Carregando as novidades...", 14f, C.MUTED, Fonts.regular)
                t.setPadding(Ui.dp(20), Ui.dp(20), Ui.dp(20), Ui.dp(20))
                rows.addView(t)
            }
        }
    }

    private fun addRow(title: String, list: List<Entry>, progress: Boolean) {
        val pad = Ui.dp(if (Ui.isTv) 36 else 14)
        val t = act.label(title, if (Ui.isTv) 20f else 17f, C.WHITE, Fonts.bold)
        t.setPadding(pad, Ui.dp(18), pad, Ui.dp(6))
        rows.addView(t)
        val hs = HorizontalScrollView(act)
        hs.isHorizontalScrollBarEnabled = false
        val r = LinearLayout(act)
        r.orientation = LinearLayout.HORIZONTAL
        r.setPadding(pad - Ui.dp(4), Ui.dp(4), pad, Ui.dp(8))
        val w = posterSize()
        for (i in list.indices) {
            val e = list[i]
            val pv = PosterView(act)
            pv.bind(e, w)
            pv.setOnClickListener { act.openEntry(e) }
            r.addView(pv, LinearLayout.LayoutParams(w + Ui.dp(8), LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = Ui.dp(6) })
        }
        hs.addView(r)
        rows.addView(hs)
    }

    private fun showSlide(i: Int) {
        idx = i
        val e = slides.getOrNull(i) ?: return
        val my = ++seq
        for (k in 0 until dots.childCount) {
            dots.getChildAt(k).background = rounded(if (k == i) C.ACC else 0xFF4A5470.toInt(), 3)
        }
        info.animate().alpha(0f).setDuration(120).withEndAction {
            tagV.text = if (e.t == "series") "NOVIDADE · SÉRIE" else "NOVIDADE · FILME"
            titleV.text = e.name
            metaV.text = if (e.rating.isNotEmpty()) "Nota " + e.rating else ""
            plotV.text = ""
            info.animate().alpha(1f).setDuration(260).start()
        }.start()
        val dm = act.resources.displayMetrics
        Img.load(back, e.icon, dm.widthPixels / 2, dm.heightPixels / 2)
        async({
            if (e.t == "series") Api.seriesInfo(e.id)?.info else Api.vodInfo(e.id)
        }) { inf ->
            if (inf != null && my == seq) {
                val parts = ArrayList<String>()
                if (inf.year.isNotEmpty()) parts.add(inf.year)
                val rt = if (inf.rating.isNotEmpty()) inf.rating else e.rating
                if (rt.isNotEmpty()) parts.add("Nota $rt")
                if (inf.genre.isNotEmpty()) parts.add(inf.genre.split(",")[0].trim())
                if (inf.duration.isNotEmpty() && e.t != "series") parts.add(inf.duration)
                metaV.text = parts.joinToString("  ·  ")
                plotV.text = inf.plot
                if (inf.backdrop.isNotEmpty()) Img.load(back, inf.backdrop, dm.widthPixels / 2, dm.heightPixels / 2)
            }
        }
    }

    @Suppress("unused")
    private val unusedColor = Color.TRANSPARENT
}
