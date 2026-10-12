package br.realtime.tv

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

class IntroScreen(act: MainActivity, private val logged: Boolean) : Screen(act) {
    private val iv = IntroView(act) {
        if (logged) act.reset(ShellScreen(act)) else act.reset(LoginScreen(act))
    }
    override val view: View = iv
    private var started = false

    init {
        iv.setOnClickListener { skip() }
    }

    override fun onShow() {
        if (!started) {
            started = true
            iv.start()
        }
    }

    fun skip() {
        iv.finish()
    }
}

class LoginScreen(act: MainActivity) : Screen(act) {
    override val view = ScrollView(act)
    private val user: EditText
    private val pass: EditText
    private val status: TextView
    private val spinner: ProgressBar
    private val btn: TextView
    private var busy = false

    init {
        view.setBackgroundColor(C.BG)
        view.isFillViewport = true
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(Ui.dp(24), Ui.dp(24), Ui.dp(24), Ui.dp(24))
        view.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        val logo = ImageView(act)
        try {
            act.assets.open("logo_c.png").use { logo.setImageBitmap(BitmapFactory.decodeStream(it)) }
        } catch (e: Exception) {
        }
        logo.adjustViewBounds = true
        col.addView(logo, LinearLayout.LayoutParams(Ui.dp(if (Ui.isTv) 460 else 300), LinearLayout.LayoutParams.WRAP_CONTENT))

        val card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(C.SURFACE.withAlpha(235), 22)
        card.setPadding(Ui.dp(24), Ui.dp(24), Ui.dp(24), Ui.dp(24))
        val cw = Ui.dp(if (Ui.isTv) 520 else 340)
        val clp = LinearLayout.LayoutParams(cw, LinearLayout.LayoutParams.WRAP_CONTENT)
        clp.topMargin = Ui.dp(28)
        col.addView(card, clp)

        card.addView(act.label("Usuário", 14f, C.TEXT, Fonts.medium))
        user = field("Digite seu usuário", false)
        card.addView(user, boxLp(8))
        val l2 = act.label("Senha", 14f, C.TEXT, Fonts.medium)
        card.addView(l2, boxLp(16))
        pass = field("Digite sua senha", true)
        card.addView(pass, boxLp(8))

        btn = act.button("ENTRAR", true)
        btn.textSize = 17f
        btn.setOnClickListener { doLogin() }
        card.addView(btn, boxLp(24))

        status = act.label("", 14f, C.MUTED, Fonts.regular)
        status.gravity = Gravity.CENTER
        card.addView(status, boxLp(14))
        spinner = ProgressBar(act)
        spinner.visibility = View.GONE
        card.addView(spinner, LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(36)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = Ui.dp(8) })

        user.setText(Store.user)
    }

    private fun boxLp(top: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = Ui.dp(top)
        return lp
    }

    private fun field(hint: String, secret: Boolean): EditText {
        val e = EditText(act)
        e.hint = hint
        e.setHintTextColor(C.DIM)
        e.setTextColor(C.WHITE)
        e.typeface = Fonts.medium
        e.textSize = 17f
        e.setSingleLine(true)
        e.background = focusBg(C.BG, 12, C.ACC)
        e.setPadding(Ui.dp(18), Ui.dp(14), Ui.dp(18), Ui.dp(14))
        e.inputType = if (secret) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        e.imeOptions = if (secret) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
        if (secret) {
            e.setOnEditorActionListener { _, _, _ ->
                doLogin()
                true
            }
        }
        return e
    }

    private fun doLogin() {
        if (busy) return
        val u = user.text.toString().trim()
        val p = pass.text.toString().trim()
        if (u.isEmpty() || p.isEmpty()) {
            status.setTextColor(C.RED)
            status.text = "Digite o usuário e a senha."
            return
        }
        busy = true
        status.setTextColor(C.MUTED)
        status.text = "Entrando..."
        spinner.visibility = View.VISIBLE
        val oldUser = Store.user
        Store.user = u
        Store.pass = p
        async({ Api.login() }) { err ->
            busy = false
            spinner.visibility = View.GONE
            if (err != null) {
                Store.pass = ""
                status.setTextColor(C.RED)
                status.text = err
            } else {
                if (oldUser != u) Store.clearUserData()
                act.reset(ShellScreen(act))
            }
        }
    }
}

interface Tab {
    val view: View
    fun onShow()
    fun onHide()
}

class ShellScreen(act: MainActivity) : Screen(act) {
    override val view = LinearLayout(act)
    private val content = FrameLayout(act)
    private val names = listOf("Início", "TV ao Vivo", "Filmes", "Séries", "Buscar", "Favoritos", "Ajustes")
    private val tabViews = ArrayList<TextView>()
    private val tabs = arrayOfNulls<Tab>(7)
    private var cur = -1
    private var shown = false

    init {
        view.orientation = LinearLayout.VERTICAL
        view.setBackgroundColor(C.BG)
        val top = LinearLayout(act)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(8), Ui.dp(4))
        val logo = act.label("Realtime", if (Ui.isTv) 24f else 20f, C.WHITE, Fonts.bold)
        val sp = SpannableString("Realtime")
        sp.setSpan(ForegroundColorSpan(C.CYAN), 4, 8, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        logo.text = sp
        top.addView(logo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = Ui.dp(14) })
        val hs = HorizontalScrollView(act)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        for (i in names.indices) {
            val t = act.label(names[i], if (Ui.isTv) 17f else 15f, C.MUTED, Fonts.medium)
            t.gravity = Gravity.CENTER
            t.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(10))
            t.background = focusBg(0x00000000, 22)
            t.tvFocus(1.04f)
            t.setOnClickListener { select(i) }
            row.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = Ui.dp(4) })
            tabViews.add(t)
        }
        hs.addView(row)
        top.addView(hs, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        view.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        view.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun make(i: Int): Tab = when (i) {
        0 -> HomeTab(this)
        1 -> BrowseTab(this, "live")
        2 -> BrowseTab(this, "vod")
        3 -> BrowseTab(this, "series")
        4 -> SearchTab(this)
        5 -> FavsTab(this)
        else -> SettingsTab(this)
    }

    fun select(i: Int) {
        if (i == cur) return
        if (cur >= 0) {
            tabs[cur]?.onHide()
            tabs[cur]?.let { content.removeView(it.view) }
            tabViews[cur].setTextColor(C.MUTED)
        }
        cur = i
        var t = tabs[i]
        if (t == null) {
            t = make(i)
            tabs[i] = t
        }
        tabViews[i].setTextColor(C.WHITE)
        content.addView(t.view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (shown) t.onShow()
    }

    override fun onShow() {
        shown = true
        if (cur < 0) select(0) else tabs[cur]?.onShow()
    }

    override fun onHide() {
        shown = false
        if (cur >= 0) tabs[cur]?.onHide()
    }

    override fun back(): Boolean {
        if (cur != 0) {
            select(0)
            return true
        }
        return false
    }

    fun logout() {
        Store.pass = ""
        act.reset(LoginScreen(act))
    }

    @Suppress("unused")
    private val unusedHandler = Handler(Looper.getMainLooper())
}
