package br.realtime.tv

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** capa de filme/série/canal: imagem, nota, título e barra de progresso */
class PosterView(ctx: Context) : LinearLayout(ctx) {
    private val frame = Aspect(ctx, 1.5f)
    private val img = ImageView(ctx)
    private val badge: TextView
    private val bar = View(ctx)
    private val title: TextView
    var entry: Entry? = null

    init {
        orientation = VERTICAL
        val p = Ui.dp(4)
        setPadding(p, p, p, p)
        background = focusBg(Color.TRANSPARENT, 12)
        tvFocus(1.05f)
        frame.background = rounded(C.SURFACE, 10)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(img, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        badge = ctx.label("", 11f, 0xFFFFD35A.toInt(), Fonts.bold)
        badge.background = rounded(0xCC000000.toInt(), 6)
        badge.setPadding(Ui.dp(6), Ui.dp(3), Ui.dp(6), Ui.dp(3))
        badge.visibility = View.GONE
        frame.addView(badge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = Ui.dp(6)
            rightMargin = Ui.dp(6)
        })
        bar.setBackgroundColor(C.CYAN)
        bar.visibility = View.GONE
        frame.addView(bar, FrameLayout.LayoutParams(0, Ui.dp(4)).apply { gravity = Gravity.BOTTOM or Gravity.START })
        addView(frame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        title = ctx.label("", if (Ui.isTv) 14f else 12f, C.TEXT, Fonts.medium, 2)
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(6) })
    }

    fun bind(e: Entry, cardW: Int) {
        entry = e
        title.text = e.name
        val live = e.t == "live"
        img.scaleType = if (live) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        val pad = if (live) Ui.dp(18) else 0
        img.setPadding(pad, pad, pad, pad)
        val w = if (cardW > 0) cardW else Ui.dp(140)
        Img.load(img, e.icon, w, (w * 1.5f).toInt())
        if (e.rating.isNotEmpty() && !live) {
            badge.text = e.rating
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
        if (e.dur > 0 && e.pos > 0 && cardW > 0) {
            val pct = (e.pos.toFloat() / e.dur.toFloat()).coerceIn(0f, 1f)
            val lp = bar.layoutParams as FrameLayout.LayoutParams
            lp.width = (cardW * pct).toInt()
            bar.layoutParams = lp
            bar.visibility = View.VISIBLE
        } else {
            bar.visibility = View.GONE
        }
    }
}

fun posterSize(): Int = Ui.dp(if (Ui.isTv) 150 else 118)

class PosterAdapter(var items: List<Entry>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<PosterAdapter.VH>() {
    class VH(val pv: PosterView) : RecyclerView.ViewHolder(pv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val pv = PosterView(parent.context)
        pv.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
        return VH(pv)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.pv.bind(items[position], 0)
        holder.pv.setOnClickListener { onClick(holder.bindingAdapterPosition) }
    }
}

/** linha de canal ao vivo: logo, número, nome e programa no ar */
class ChannelRow(ctx: Context) : LinearLayout(ctx) {
    private val logo = ImageView(ctx)
    private val name: TextView
    private val prog: TextView
    private val bar = View(ctx)
    private val barBg = FrameLayout(ctx)
    var epgFor: String = ""

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8))
        background = focusBg(C.SURFACE, 14)
        tvFocus(1.02f)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        logo.background = rounded(C.BG, 10)
        logo.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6))
        addView(logo, LayoutParams(Ui.dp(72), Ui.dp(54)))
        val col = LinearLayout(ctx)
        col.orientation = VERTICAL
        name = ctx.label("", if (Ui.isTv) 17f else 15f, C.WHITE, Fonts.bold, 1)
        prog = ctx.label("", if (Ui.isTv) 14f else 12f, C.MUTED, Fonts.regular, 1)
        col.addView(name)
        col.addView(prog, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(3) })
        bar.setBackgroundColor(C.ACC)
        barBg.setBackgroundColor(C.BG)
        barBg.addView(bar, FrameLayout.LayoutParams(0, Ui.dp(3)))
        col.addView(barBg, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(3)).apply { topMargin = Ui.dp(6) })
        addView(col, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(14) })
    }

    fun bind(e: Entry) {
        name.text = (if (e.num.isNotEmpty()) e.num + "  " else "") + e.name
        Img.load(logo, e.icon, Ui.dp(72), Ui.dp(54))
        epgFor = e.id
        setEpg(null)
    }

    fun setEpg(list: List<EpgItem>?) {
        val now = System.currentTimeMillis() / 1000
        val cur = list?.firstOrNull { it.start <= now && it.stop > now } ?: list?.firstOrNull()
        if (cur == null) {
            prog.text = ""
            bar.layoutParams = (bar.layoutParams as FrameLayout.LayoutParams).apply { width = 0 }
            return
        }
        prog.text = cur.title
        barBg.post {
            val w = barBg.width
            val span = (cur.stop - cur.start).coerceAtLeast(1)
            val pct = ((now - cur.start).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            bar.layoutParams = (bar.layoutParams as FrameLayout.LayoutParams).apply { width = (w * pct).toInt() }
        }
    }
}

object EpgCache {
    private val map = HashMap<String, Pair<Long, List<EpgItem>>>()
    fun get(id: String): List<EpgItem>? {
        val p = map[id] ?: return null
        if (System.currentTimeMillis() - p.first > 120000) return null
        return p.second
    }
    fun put(id: String, l: List<EpgItem>) {
        if (map.size > 400) map.clear()
        map[id] = Pair(System.currentTimeMillis(), l)
    }
}

class ChannelAdapter(var items: List<Entry>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<ChannelAdapter.VH>() {
    class VH(val row: ChannelRow) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val r = ChannelRow(parent.context)
        r.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(4)
            bottomMargin = Ui.dp(4)
            leftMargin = Ui.dp(10)
            rightMargin = Ui.dp(10)
        }
        return VH(r)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val row = holder.row
        row.bind(e)
        row.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        val cached = EpgCache.get(e.id)
        if (cached != null) {
            row.setEpg(cached)
        } else {
            val id = e.id
            async({ Api.epg(id) }) { r ->
                if (r != null) {
                    EpgCache.put(id, r)
                    if (row.epgFor == id) row.setEpg(r)
                }
            }
        }
    }
}
