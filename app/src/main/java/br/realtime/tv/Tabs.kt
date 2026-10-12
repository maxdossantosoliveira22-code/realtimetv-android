package br.realtime.tv

import android.app.AlertDialog
import android.content.Context
import android.media.MediaCodecList
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** grade responsiva: calcula as colunas pela largura disponível */
fun gridFor(rv: RecyclerView, cellDp: Int): GridLayoutManager {
    val gm = GridLayoutManager(rv.context, 3)
    rv.addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
        val w = r - l
        if (w > 0) {
            val n = (w / Ui.dp(cellDp)).coerceAtLeast(2)
            if (gm.spanCount != n) gm.spanCount = n
        }
    }
    return gm
}

class BrowseTab(private val shell: ShellScreen, private val sec: String) : Tab {
    private val act = shell.act
    override val view = LinearLayout(act)
    private val chipsRow = LinearLayout(act)
    private val msg: TextView
    private val rv = RecyclerView(act)
    private var cats: List<Cat> = emptyList()
    private var sel = -1
    private val chips = ArrayList<TextView>()
    private val cache = HashMap<String, List<Entry>>()
    private var current: List<Entry> = emptyList()
    private var started = false
    private var token = 0

    init {
        view.orientation = LinearLayout.VERTICAL
        val hs = HorizontalScrollView(act)
        hs.isHorizontalScrollBarEnabled = false
        chipsRow.orientation = LinearLayout.HORIZONTAL
        chipsRow.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6))
        hs.addView(chipsRow)
        view.addView(hs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val box = FrameLayout(act)
        rv.clipToPadding = false
        rv.setPadding(Ui.dp(6), Ui.dp(4), Ui.dp(6), Ui.dp(16))
        if (sec == "live") rv.layoutManager = LinearLayoutManager(act) else rv.layoutManager = gridFor(rv, if (Ui.isTv) 150 else 112)
        rv.setHasFixedSize(true)
        box.addView(rv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        msg = act.label("Carregando...", 15f, C.MUTED, Fonts.medium)
        msg.gravity = Gravity.CENTER
        box.addView(msg, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        view.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onShow() {
        if (started) return
        started = true
        msg.text = "Carregando categorias..."
        msg.visibility = View.VISIBLE
        async({ Api.categories(sec) }) { r ->
            if (r == null || r.isEmpty()) {
                msg.text = "Não foi possível carregar. Verifique a internet e tente de novo."
                started = false
            } else {
                cats = r
                buildChips()
                select(0)
            }
        }
    }

    override fun onHide() {}

    private fun buildChips() {
        chipsRow.removeAllViews()
        chips.clear()
        for (i in cats.indices) {
            val t = act.label(cats[i].name, if (Ui.isTv) 15f else 13f, C.MUTED, Fonts.medium, 1)
            t.setPadding(Ui.dp(16), Ui.dp(9), Ui.dp(16), Ui.dp(9))
            t.background = focusBg(C.SURFACE, 20)
            t.tvFocus(1.04f)
            t.setOnClickListener { select(i) }
            chipsRow.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = Ui.dp(8) })
            chips.add(t)
        }
    }

    private fun select(i: Int) {
        if (i == sel) return
        if (sel >= 0 && sel < chips.size) {
            chips[sel].setTextColor(C.MUTED)
            chips[sel].background = focusBg(C.SURFACE, 20)
        }
        sel = i
        chips[i].setTextColor(C.WHITE)
        chips[i].background = focusBg(C.ACC, 20)
        val cat = cats[i]
        val my = ++token
        val cached = cache[cat.id]
        if (cached != null) {
            show(cached)
            return
        }
        rv.adapter = null
        msg.text = "Carregando..."
        msg.visibility = View.VISIBLE
        async({ Api.streams(sec, cat.id)?.map { it.e } }) { r ->
            if (my == token) {
                if (r == null) {
                    msg.text = "Não foi possível carregar esta categoria."
                } else {
                    cache[cat.id] = r
                    show(r)
                }
            }
        }
    }

    private fun show(list: List<Entry>) {
        current = list
        if (list.isEmpty()) {
            rv.adapter = null
            msg.text = "Esta categoria está vazia."
            msg.visibility = View.VISIBLE
            return
        }
        msg.visibility = View.GONE
        if (sec == "live") {
            rv.adapter = ChannelAdapter(list) { pos -> if (pos >= 0) act.openEntry(list[pos], list, pos) }
        } else {
            rv.adapter = PosterAdapter(list) { pos -> if (pos >= 0) act.openEntry(list[pos]) }
        }
    }
}

class SearchTab(private val shell: ShellScreen) : Tab {
    private val act = shell.act
    override val view = LinearLayout(act)
    private val input = EditText(act)
    private val msg: TextView
    private val rv = RecyclerView(act)
    private val handler = Handler(Looper.getMainLooper())
    private var names: List<String> = emptyList()
    private var entries: List<Entry> = emptyList()
    private var ready = false
    private var loading = false
    private val run = Runnable { search() }

    init {
        view.orientation = LinearLayout.VERTICAL
        input.hint = "Buscar canais, filmes e séries"
        input.setHintTextColor(C.DIM)
        input.setTextColor(C.WHITE)
        input.typeface = Fonts.medium
        input.textSize = 17f
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.background = focusBg(C.SURFACE, 14, C.ACC)
        input.setPadding(Ui.dp(18), Ui.dp(14), Ui.dp(18), Ui.dp(14))
        view.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8))
        })
        val box = FrameLayout(act)
        rv.layoutManager = gridFor(rv, if (Ui.isTv) 150 else 112)
        rv.clipToPadding = false
        rv.setPadding(Ui.dp(6), Ui.dp(4), Ui.dp(6), Ui.dp(16))
        box.addView(rv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        msg = act.label("Digite para buscar", 15f, C.MUTED, Fonts.medium)
        msg.gravity = Gravity.CENTER
        box.addView(msg, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        view.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                handler.removeCallbacks(run)
                handler.postDelayed(run, 250)
            }
        })
    }

    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    override fun onShow() {
        if (ready || loading) return
        loading = true
        msg.text = "Preparando a busca..."
        async({
            val all = ArrayList<Entry>()
            for (sec in listOf("live", "vod", "series")) {
                val adult = Api.adultIds(sec)
                val l = Api.streams(sec, null, 43200)
                if (l != null) {
                    for (r in l) if (!adult.contains(r.cat)) all.add(r.e)
                }
            }
            all
        }) { r ->
            loading = false
            if (r == null || r.isEmpty()) {
                msg.text = "Não foi possível preparar a busca."
            } else {
                entries = r
                names = r.map { fold(it.name) }
                ready = true
                msg.text = "Digite para buscar"
                search()
            }
        }
    }

    override fun onHide() {}

    private fun search() {
        if (!ready) return
        val q = fold(input.text.toString().trim())
        if (q.isEmpty()) {
            rv.adapter = null
            msg.text = "Digite para buscar"
            msg.visibility = View.VISIBLE
            return
        }
        val words = q.split(" ").filter { it.isNotEmpty() }
        val out = ArrayList<Entry>()
        for (i in names.indices) {
            val n = names[i]
            if (words.all { n.contains(it) }) {
                out.add(entries[i])
                if (out.size >= 150) break
            }
        }
        if (out.isEmpty()) {
            rv.adapter = null
            msg.text = "Nada encontrado."
            msg.visibility = View.VISIBLE
        } else {
            msg.visibility = View.GONE
            rv.adapter = PosterAdapter(out) { pos -> if (pos >= 0) act.openEntry(out[pos], out, pos) }
        }
    }
}

class FavsTab(private val shell: ShellScreen) : Tab {
    private val act = shell.act
    override val view = FrameLayout(act)
    private val rv = RecyclerView(act)
    private val msg: TextView
    private var sig = ""

    init {
        rv.layoutManager = gridFor(rv, if (Ui.isTv) 150 else 112)
        rv.clipToPadding = false
        rv.setPadding(Ui.dp(6), Ui.dp(10), Ui.dp(6), Ui.dp(16))
        view.addView(rv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        msg = act.label("Nenhum favorito ainda.\nAbra um filme, série ou canal e toque em ♥ Favoritar.", 15f, C.MUTED, Fonts.medium)
        msg.gravity = Gravity.CENTER
        view.addView(msg, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
    }

    override fun onShow() {
        val l = Store.favs()
        val s = l.joinToString("|") { it.key() }
        if (s == sig) return
        sig = s
        if (l.isEmpty()) {
            rv.adapter = null
            msg.visibility = View.VISIBLE
        } else {
            msg.visibility = View.GONE
            rv.adapter = PosterAdapter(l) { pos -> if (pos >= 0) act.openEntry(l[pos], l, pos) }
        }
    }

    override fun onHide() {}
}

class SettingsTab(private val shell: ShellScreen) : Tab {
    private val act = shell.act
    override val view = ScrollView(act)
    private val col = LinearLayout(act)
    private lateinit var fmtV: TextView
    private lateinit var adultV: TextView
    private lateinit var accV: TextView

    init {
        col.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(if (Ui.isTv) 60 else 14)
        col.setPadding(pad, Ui.dp(12), pad, Ui.dp(30))
        view.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        fmtV = row("Formato dos canais ao vivo", fmtText()) {
            Store.liveFormat = when (Store.liveFormat) { "auto" -> "hls"; "hls" -> "ts"; else -> "auto" }
            fmtV.text = fmtText()
        }
        adultV = row("Ocultar conteúdo adulto", if (Store.adultFilter) "Sim" else "Não") {
            Store.adultFilter = !Store.adultFilter
            adultV.text = if (Store.adultFilter) "Sim" else "Não"
            Api.clearCache()
            Store.setHighlights("vod", emptyList())
            Store.setHighlights("series", emptyList())
        }
        row("Limpar cache (listas e imagens)", "Limpar") {
            Api.clearCache()
            android.widget.Toast.makeText(act, "Cache limpo", android.widget.Toast.LENGTH_SHORT).show()
        }
        row("Vídeo H.265 (HEVC) neste aparelho", if (hevc()) "Compatível" else "Não compatível") {}
        accV = row("Conta", accText()) {}
        row("Sair da conta", "Sair") { shell.logout() }
        row("Versão", Config.VERSION) {}
    }

    private fun fmtText(): String = when (Store.liveFormat) {
        "hls" -> "HLS (.m3u8)"
        "ts" -> "TS (.ts)"
        else -> "Automático"
    }

    private fun hevc(): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { ci ->
            !ci.isEncoder && ci.supportedTypes.any { it.equals("video/hevc", true) }
        }
    } catch (e: Exception) {
        false
    }

    private fun accText(): String {
        val ui = Api.userInfo
        var s = Store.user
        if (ui != null) {
            val exp = ui.optString("exp_date").toLongOrNull()
            if (exp != null && exp > 0) s += "  ·  vence " + SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(exp * 1000))
        }
        return s
    }

    private fun row(title: String, value: String, onClick: () -> Unit): TextView {
        val r = LinearLayout(act)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(16))
        r.background = focusBg(C.SURFACE, 14)
        r.tvFocus(1.02f)
        r.setOnClickListener { onClick() }
        val t = act.label(title, if (Ui.isTv) 17f else 15f, C.TEXT, Fonts.medium)
        r.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val v = act.label(value, if (Ui.isTv) 15f else 13f, C.CYAN, Fonts.medium)
        v.gravity = Gravity.END
        r.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(r, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(8) })
        return v
    }

    override fun onShow() {
        accV.text = accText()
    }

    override fun onHide() {}

    @Suppress("unused")
    private fun unused(c: Context, d: AlertDialog.Builder?) {}
}
