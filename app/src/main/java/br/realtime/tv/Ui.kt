package br.realtime.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.util.concurrent.Executors

object Ui {
    var density = 1f
    var isTv = false
    fun dp(v: Int): Int = (v * density + 0.5f).toInt()
}

object C {
    const val BG = 0xFF0B0F1A.toInt()
    const val SURFACE = 0xFF1A2136.toInt()
    const val SURFACE2 = 0xFF252F4F.toInt()
    const val ACC = 0xFF7C5CFF.toInt()
    const val CYAN = 0xFF00D4FF.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val TEXT = 0xFFE6EAF5.toInt()
    const val MUTED = 0xFF8A94B0.toInt()
    const val DIM = 0xFF66718F.toInt()
    const val RED = 0xFFFF6B7D.toInt()
}

object Fonts {
    val bold: Typeface by lazy { load("Bold") }
    val medium: Typeface by lazy { load("Medium") }
    val regular: Typeface by lazy { load("Regular") }
    private fun load(w: String): Typeface = try {
        Typeface.createFromAsset(App.ctx.assets, "fonts/Poppins-$w.ttf")
    } catch (e: Exception) {
        Typeface.DEFAULT
    }
}

object Bg {
    private val pool = Executors.newFixedThreadPool(4)
    private val handler = Handler(Looper.getMainLooper())
    fun run(f: () -> Unit) {
        pool.execute {
            try {
                f()
            } catch (e: Throwable) {
            }
        }
    }
    fun ui(f: () -> Unit) {
        handler.post(f)
    }
}

/** roda [work] em segundo plano e entrega o resultado na tela (null se der erro) */
fun <T> async(work: () -> T?, done: (T?) -> Unit) {
    Bg.run {
        val r: T? = try {
            work()
        } catch (e: Throwable) {
            null
        }
        Bg.ui { done(r) }
    }
}

fun rounded(color: Int, rDp: Int): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = Ui.dp(rDp).toFloat()
    return g
}

fun focusBg(normal: Int, rDp: Int, stroke: Int = C.WHITE): StateListDrawable {
    val f = rounded(normal, rDp)
    f.setStroke(Ui.dp(3), stroke)
    val sl = StateListDrawable()
    sl.addState(intArrayOf(android.R.attr.state_focused), f)
    sl.addState(intArrayOf(android.R.attr.state_pressed), f)
    sl.addState(intArrayOf(), rounded(normal, rDp))
    return sl
}

fun View.tvFocus(scale: Float = 1.05f) {
    isFocusable = true
    isClickable = true
    setOnFocusChangeListener { v, has ->
        v.animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f).setDuration(120).start()
    }
}

fun Context.label(text: String, sp: Float, color: Int = C.TEXT, face: Typeface = Fonts.regular, lines: Int = 0): TextView {
    val t = TextView(this)
    t.text = text
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    t.setTextColor(color)
    t.typeface = face
    t.includeFontPadding = false
    if (lines > 0) {
        t.maxLines = lines
        t.ellipsize = TextUtils.TruncateAt.END
    }
    return t
}

fun Context.button(text: String, primary: Boolean): TextView {
    val t = label(text, if (Ui.isTv) 18f else 15f, C.WHITE, Fonts.bold)
    t.gravity = Gravity.CENTER
    t.setPadding(Ui.dp(22), Ui.dp(12), Ui.dp(22), Ui.dp(12))
    t.background = focusBg(if (primary) C.ACC else C.SURFACE2, 12)
    t.tvFocus(1.04f)
    return t
}

fun Context.fillLp(): FrameLayout.LayoutParams =
    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

fun Int.withAlpha(a: Int): Int = Color.argb(a, Color.red(this), Color.green(this), Color.blue(this))

/** FrameLayout com proporção fixa (altura = largura * ratio) */
class Aspect(ctx: Context, private val ratio: Float) : FrameLayout(ctx) {
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((w * ratio).toInt(), MeasureSpec.EXACTLY)
        )
    }
}

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
}
