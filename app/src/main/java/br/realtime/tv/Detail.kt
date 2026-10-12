package br.realtime.tv

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class DetailScreen(act: MainActivity, private val entry: Entry) : Screen(act) {
    override val view = FrameLayout(act)
    private val scroll = ScrollView(act)
    private val back = ImageView(act)
    private val poster = ImageView(act)
    private val titleV: TextView
    private val metaV: TextView
    private val plotV: TextView
    private val castV: TextView
    private val btnPlay: TextView
    private val btnFav: TextView
    private val seasonsRow = LinearLayout(act)
    private val epsBox = LinearLayout(act)
    private var seriesInfo: SeriesInfo? = null
    private var curSeason = -1
    private var movieExt = ""

    init {
        view.setBackgroundColor(C.BG)
        val dm = act.resources.displayMetrics
        val land = dm.widthPixels > dm.heightPixels
        val heroH = if (land) (dm.heightPixels * 0.5f).toInt() else (dm.widthPixels * 0.62f).toInt()

        back.scaleType = ImageView.ScaleType.CENTER_CROP
        view.addView(back, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, heroH))
        val fade = View(act)
        fade.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x660B0F1A, C.BG))
        view.addView(fade, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, heroH))

        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(if (Ui.isTv) 48 else 16)
        col.setPadding(pad, (heroH * 0.55f).toInt(), pad, Ui.dp(40))

        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        poster.scaleType = ImageView.ScaleType.CENTER_CROP
        poster.background = rounded(C.SURFACE, 12)
        val pw = Ui.dp(if (Ui.isTv) 150 else 100)
        head.addView(poster, LinearLayout.LayoutParams(pw, (pw * 1.5f).toInt()))
        val txt = LinearLayout(act)
        txt.orientation = LinearLayout.VERTICAL
        titleV = act.label(entry.name, if (Ui.isTv) 30f else 22f, C.WHITE, Fonts.bold, 3)
        metaV = act.label("", if (Ui.isTv) 15f else 13f, C.CYAN, Fonts.medium, 2)
        txt.addView(titleV)
        txt.addView(metaV, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(8) })
        val btns = LinearLayout(act)
        btns.orientation = LinearLayout.HORIZONTAL
        btnPlay = act.button("▶  Assistir", true)
        btnFav = act.button("♡  Favoritar", false)
        btns.addView(btnPlay)
        btns.addView(btnFav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = Ui.dp(10) })
        txt.addView(btns, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(14) })
        head.addView(txt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(16); gravity = Gravity.BOTTOM })
        col.addView(head)

        plotV = act.label("", if (Ui.isTv) 15f else 14f, 0xFFC3CBDF.toInt(), Fonts.regular)
        plotV.setLineSpacing(0f, 1.15f)
        col.addView(plotV, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(18) })
        castV = act.label("", 12f, C.MUTED, Fonts.regular)
        col.addView(castV, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(10) })

        if (entry.t == "series") {
            val hs = HorizontalScrollView(act)
            hs.isHorizontalScrollBarEnabled = false
            seasonsRow.orientation = LinearLayout.HORIZONTAL
            hs.addView(seasonsRow)
            col.addView(hs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(20) })
            epsBox.orientation = LinearLayout.VERTICAL
            col.addView(epsBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(10) })
        }
        scroll.addView(col)
        scroll.isVerticalScrollBarEnabled = false
        view.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        Img.load(poster, entry.icon, pw, (pw * 1.5f).toInt())
        refreshFav()
        btnFav.setOnClickListener {
            Store.toggleFav(entry)
            refreshFav()
        }
        btnPlay.setOnClickListener { playMain() }
        load()
    }

    private fun refreshFav() {
        btnFav.text = if (Store.isFav(entry)) "♥  Nos favoritos" else "♡  Favoritar"
    }

    private fun updatePlayLabel() {
        if (entry.t == "series") {
            val r = Store.recentFindSeries(entry.id)
            btnPlay.text = if (r != null) "▶  Continuar" else "▶  Assistir"
        } else {
            val r = Store.recentFind("movie", entry.id)
            btnPlay.text = if (r != null && r.pos > 10000) "▶  Continuar de " + fmtTime(r.pos) else "▶  Assistir"
        }
    }

    override fun onShow() {
        updatePlayLabel()
        refreshFav()
    }

    private fun load() {
        val dm = act.resources.displayMetrics
        if (entry.t == "series") {
            async({ Api.seriesInfo(entry.id) }) { si ->
                if (si == null) {
                    plotV.text = "Não foi possível carregar os detalhes."
                } else {
                    seriesInfo = si
                    fill(si.info)
                    buildSeasons()
                }
            }
        } else {
            async({ Api.vodInfo(entry.id) }) { inf ->
                if (inf != null) {
                    movieExt = inf.ext
                    fill(inf)
                }
            }
        }
        Img.load(back, entry.icon, dm.widthPixels / 2, dm.heightPixels / 2)
    }

    private fun fill(inf: Info) {
        val dm = act.resources.displayMetrics
        val parts = ArrayList<String>()
        if (inf.year.isNotEmpty()) parts.add(inf.year)
        val rt = if (inf.rating.isNotEmpty()) inf.rating else entry.rating
        if (rt.isNotEmpty()) parts.add("Nota $rt")
        if (inf.genre.isNotEmpty()) parts.add(inf.genre)
        if (inf.duration.isNotEmpty() && entry.t != "series") parts.add(inf.duration)
        metaV.text = parts.joinToString("  ·  ")
        plotV.text = inf.plot.ifEmpty { "Sem sinopse disponível." }
        castV.text = if (inf.cast.isNotEmpty()) "Elenco: " + inf.cast else ""
        if (inf.backdrop.isNotEmpty()) Img.load(back, inf.backdrop, dm.widthPixels / 2, dm.heightPixels / 2)
        else if (inf.poster.isNotEmpty()) Img.load(back, inf.poster, dm.widthPixels / 2, dm.heightPixels / 2)
        updatePlayLabel()
    }

    private fun buildSeasons() {
        val si = seriesInfo ?: return
        seasonsRow.removeAllViews()
        for (n in si.seasons.keys) {
            val t = act.label("Temporada $n", if (Ui.isTv) 15f else 13f, C.MUTED, Fonts.medium)
            t.setPadding(Ui.dp(16), Ui.dp(9), Ui.dp(16), Ui.dp(9))
            t.background = focusBg(C.SURFACE, 20)
            t.tvFocus(1.04f)
            t.setOnClickListener { showSeason(n) }
            t.tag = n
            seasonsRow.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = Ui.dp(8) })
        }
        val last = Store.recentFindSeries(entry.id)
        var first = si.seasons.keys.firstOrNull() ?: return
        if (last != null) {
            for ((k, v) in si.seasons) if (v.any { it.id == last.id }) first = k
        }
        showSeason(first)
    }

    private fun showSeason(n: Int) {
        val si = seriesInfo ?: return
        curSeason = n
        for (i in 0 until seasonsRow.childCount) {
            val t = seasonsRow.getChildAt(i) as TextView
            val on = (t.tag as Int) == n
            t.setTextColor(if (on) C.WHITE else C.MUTED)
            t.background = focusBg(if (on) C.ACC else C.SURFACE, 20)
        }
        epsBox.removeAllViews()
        val eps = si.seasons[n] ?: return
        val list = eps.map { epEntry(it, n) }
        for (i in eps.indices) {
            val ep = eps[i]
            val r = LinearLayout(act)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12))
            r.background = focusBg(C.SURFACE, 12)
            r.tvFocus(1.02f)
            val num = act.label(ep.num.toString(), 16f, C.CYAN, Fonts.bold)
            num.gravity = Gravity.CENTER
            r.addView(num, LinearLayout.LayoutParams(Ui.dp(34), LinearLayout.LayoutParams.WRAP_CONTENT))
            val col = LinearLayout(act)
            col.orientation = LinearLayout.VERTICAL
            col.addView(act.label(ep.title.ifEmpty { "Episódio " + ep.num }, if (Ui.isTv) 16f else 14f, C.WHITE, Fonts.medium, 1))
            if (ep.duration.isNotEmpty()) col.addView(act.label(ep.duration, 12f, C.MUTED, Fonts.regular), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(2) })
            r.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(10) })
            r.setOnClickListener { act.play(list, i) }
            epsBox.addView(r, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(6) })
        }
    }

    private fun epEntry(ep: Ep, season: Int): Entry {
        val saved = Store.recentFind("episode", ep.id)
        return Entry(
            "episode", ep.id, entry.name + " · T" + season + " E" + ep.num + (if (ep.title.isNotEmpty()) " - " + ep.title else ""),
            if (ep.image.isNotEmpty()) ep.image else entry.icon, "", ep.ext, entry.id, saved?.pos ?: 0L, saved?.dur ?: 0L
        )
    }

    private fun playMain() {
        if (entry.t == "series") {
            val si = seriesInfo ?: return
            val last = Store.recentFindSeries(entry.id)
            var season = si.seasons.keys.firstOrNull() ?: return
            var idx = 0
            if (last != null) {
                for ((k, v) in si.seasons) {
                    val i = v.indexOfFirst { it.id == last.id }
                    if (i >= 0) { season = k; idx = i }
                }
            }
            val eps = si.seasons[season] ?: return
            act.play(eps.map { epEntry(it, season) }, idx)
        } else {
            val saved = Store.recentFind("movie", entry.id)
            val e = entry.copy(ext = if (movieExt.isNotEmpty()) movieExt else entry.ext, pos = saved?.pos ?: 0L, dur = saved?.dur ?: 0L)
            act.play(listOf(e), 0)
        }
    }
}
