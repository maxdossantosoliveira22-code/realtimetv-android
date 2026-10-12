package br.realtime.tv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** abertura animada com o logo Realtime (anel + play + letras caindo) */
class IntroView(ctx: Context, private val onDone: () -> Unit) : View(ctx) {
    private val text = "REALTIME"
    private val ring: Bitmap? = load("logo_ring.png")
    private val play: Bitmap? = load("logo_play.png")
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pTag = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pFx = Paint(Paint.ANTI_ALIAS_FLAG)
    private val adv = FloatArray(text.length)
    private var wordW = 0f
    private var capH = 0f
    private var t = 0f
    private var finished = false

    private val total = 3.3f
    private val fade = 0.4f
    private val t0 = 0.30f
    private val stagger = 0.10f
    private val drop = 0.9f
    private val wave = 0.9f
    private val burst = t0 + stagger * 7 + 0.2f

    private class Spark(val dx: Float, val dy: Float, val delay: Float, val color: Int)
    private val sparks = ArrayList<Spark>()
    private val ringStarts = floatArrayOf(0.2f, burst, burst + 0.25f)

    private val anim = ValueAnimator.ofFloat(0f, total + fade)

    init {
        setBackgroundColor(C.BG)
        pText.typeface = Fonts.bold
        pText.textSize = 150f
        pTag.typeface = Fonts.medium
        pTag.textSize = 34f
        pTag.color = 0xFF96A0BE.toInt()
        for (i in text.indices) {
            adv[i] = Math.ceil(pText.measureText(text[i].toString()).toDouble()).toFloat() + 6f
            wordW += adv[i]
        }
        val b = android.graphics.Rect()
        pText.getTextBounds("E", 0, 1, b)
        capH = b.height().toFloat()
        val cols = intArrayOf(C.WHITE, C.CYAN, 0xFFAA8CFF.toInt())
        for (k in 0 until 12) {
            val ang = Math.toRadians((k * 30 + (k * 7 % 11) - 5).toDouble())
            val r = 300f + (k * 53 % 260)
            sparks.add(Spark((r * cos(ang)).toFloat(), (0.65 * r * sin(ang)).toFloat(), burst + (if (k % 2 == 0) 0f else 0.15f), cols[k % 3]))
        }
        anim.duration = ((total + fade) * 1000).toLong()
        anim.interpolator = LinearInterpolator()
        anim.addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finish()
            }
        })
    }

    private fun load(name: String): Bitmap? = try {
        context.assets.open(name).use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    fun start() {
        anim.start()
    }

    fun finish() {
        if (finished) return
        finished = true
        anim.cancel()
        onDone()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    private fun dropY(u: Float): Float = -520f * exp(-5f * u) * cos(7.5f * u)
    private fun dropSx(u: Float): Float = 1f - 0.7f * exp(-6f * u) * cos(8f * u) + 0.25f * exp(-6f * u) * sin(8f * u)
    private fun dropSy(u: Float): Float = 1f - 0.7f * exp(-6f * u) * cos(8f * u) - 0.25f * exp(-6f * u) * sin(8f * u)
    private fun dropRot(u: Float, i: Int): Float = (if (i % 2 == 0) 0.6f else -0.6f) * exp(-6f * u) * cos(7f * u)
    private fun waveH(u: Float): Float = sin(PI.toFloat() * u)
    private fun ringS(u: Float): Float = 1f - exp(-5f * u) * cos(7f * u)
    private fun popS(u: Float): Float = 1f - exp(-6f * u) * cos(9f * u)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val ringD = 250f
        val gap = 44f
        val totalW = ringD + gap + wordW
        val s = min(w / (totalW + 600f), h / 900f)
        c.save()
        c.translate(w / 2f, h / 2f)
        c.scale(s, s)

        val left = -totalW / 2f
        val rcx = left + ringD / 2f
        val x0 = left + ringD + gap

        // brilho de fundo
        val ge = if (t < 1f) 1f - (1f - t) * (1f - t) else 1f
        val ga = if (t < 1f) 0.9f * ge else 0.6f + 0.35f * sin(PI.toFloat() * (((t - 1f) % 1.4f) / 1.4f))
        val gs = if (t < 1f) 0.6f + 0.4f * ge else 1f
        pFx.shader = RadialGradient(0f, 0f, 480f * gs, intArrayOf(C.ACC.withAlpha((110 * ga).toInt().coerceIn(0, 255)), C.ACC.withAlpha(0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, 480f * gs, pFx)
        pFx.shader = null

        // anéis de luz
        pFx.style = Paint.Style.STROKE
        pFx.strokeWidth = 5f
        for (d0 in ringStarts) {
            val u = (t - d0) / 1.5f
            if (u > 0f && u < 1f) {
                val e = 1f - (1f - u) * (1f - u) * (1f - u)
                val r = 256f * (0.25f + 2.1f * e)
                val a = if (u < 0.15f) 0.8f * u / 0.15f else 0.8f * (1f - (u - 0.15f) / 0.85f)
                pFx.color = 0xFFBEAFFF.toInt().withAlpha((255 * a).toInt().coerceIn(0, 255))
                c.drawCircle(0f, 0f, r, pFx)
            }
        }
        pFx.style = Paint.Style.FILL

        // faíscas
        for (sp in sparks) {
            val u = (t - sp.delay) / 1.2f
            if (u > 0f && u < 1f) {
                val e = 1f - (1f - u) * (1f - u)
                val a = if (u < 0.1f) u / 0.1f else 1f - (u - 0.1f) / 0.9f
                val rad = 14f * (1.6f + (0.2f - 1.6f) * u)
                pFx.color = sp.color.withAlpha((255 * a).toInt().coerceIn(0, 255))
                c.drawCircle(sp.dx * e, sp.dy * e, rad, pFx)
            }
        }

        // ícone: anel gira e cresce
        val ui = (t - 0.1f) / 1.0f
        if (ui > 0f && ring != null) {
            val u = min(1f, ui)
            val e = 1f - (1f - u) * (1f - u) * (1f - u)
            var sc = ringS(e)
            if (t >= burst && t <= burst + 0.5f) sc = 1f + 0.07f * waveH((t - burst) / 0.5f)
            val rot = -3.4f * (1f - e) * (1f - e)
            if (sc > 0.02f) {
                c.save()
                c.translate(rcx, 0f)
                c.rotate(Math.toDegrees(rot.toDouble()).toFloat())
                c.scale(sc, sc)
                pBmp.alpha = (255 * min(1f, e / 0.15f)).toInt().coerceIn(0, 255)
                c.drawBitmap(ring, null, android.graphics.RectF(-ringD / 2, -ringD / 2, ringD / 2, ringD / 2), pBmp)
                c.restore()
            }
        }
        // play salta para dentro
        val up = (t - 0.65f) / 0.7f
        if (up > 0f && play != null) {
            val u = min(1f, up)
            val sc = popS(u)
            if (sc > 0.02f) {
                c.save()
                c.translate(rcx + 5f, 0f)
                c.scale(sc, sc)
                pBmp.alpha = (255 * min(1f, u / 0.12f)).toInt().coerceIn(0, 255)
                c.drawBitmap(play, null, android.graphics.RectF(-100f, -100f, 100f, 100f), pBmp)
                c.restore()
            }
        }

        // letras caindo com quique
        var x = x0
        for (i in text.indices) {
            val start = t0 + stagger * i
            val tl = t - start
            val cx = x + adv[i] / 2f
            x += adv[i]
            if (tl < 0f) continue
            var oy = 0f
            var sx = 1f
            var sy = 1f
            var rot = 0f
            var a = 1f
            if (tl <= drop) {
                val u = tl / drop
                oy = dropY(u); sx = dropSx(u); sy = dropSy(u); rot = dropRot(u, i); a = min(1f, u / 0.12f)
            } else {
                val tw = t - (start + drop + 0.05f)
                if (tw in 0f..wave) {
                    val hh = waveH(tw / wave)
                    oy = -26f * hh; sx = 1f + 0.10f * hh; sy = sx
                }
            }
            pText.color = if (i < 4) C.WHITE else C.CYAN
            pText.alpha = (255 * a).toInt().coerceIn(0, 255)
            c.save()
            c.translate(cx, oy)
            c.rotate(Math.toDegrees(rot.toDouble()).toFloat())
            c.scale(sx, sy)
            val cw = pText.measureText(text[i].toString())
            c.drawText(text[i].toString(), -cw / 2f, capH / 2f, pText)
            c.restore()
        }

        // brilho que atravessa o nome
        val sw = (t - (burst + 0.1f)) / 0.8f
        if (sw > 0f && sw < 1f) {
            val e = 0.5f - 0.5f * cos(PI.toFloat() * sw)
            val sxp = x0 - 150f + (wordW + 300f) * e
            pFx.color = 0x33FFFFFF
            val path = android.graphics.Path()
            path.moveTo(sxp, -110f); path.lineTo(sxp + 70f, -110f); path.lineTo(sxp + 20f, 110f); path.lineTo(sxp - 50f, 110f); path.close()
            c.drawPath(path, pFx)
        }

        // frase
        val tu = (t - (burst + 0.2f)) / 0.6f
        if (tu > 0f) {
            val u = min(1f, tu)
            val e = 1f - (1f - u) * (1f - u) * (1f - u)
            pTag.alpha = (255 * e).toInt().coerceIn(0, 255)
            c.drawText("TV  AO  VIVO", x0 + 6f - 40f * (1f - e), 108f + 12f, pTag)
        }
        c.restore()

        // saída suave
        if (t > total) {
            val a = ((t - total) / fade).coerceIn(0f, 1f)
            c.drawColor(C.BG.withAlpha((255 * a).toInt()))
        }
    }
}
