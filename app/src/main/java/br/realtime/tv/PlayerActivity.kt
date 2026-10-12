package br.realtime.tv

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var root: FrameLayout
    private lateinit var surface: SurfaceView
    private lateinit var spinner: ProgressBar
    private lateinit var msg: TextView
    private lateinit var overlay: FrameLayout
    private lateinit var titleV: TextView
    private lateinit var subV: TextView
    private lateinit var seekRow: LinearLayout
    private lateinit var seek: SeekBar
    private lateinit var curT: TextView
    private lateinit var totT: TextView
    private lateinit var btnPlay: TextView
    private lateinit var btnPrev: TextView
    private lateinit var btnNext: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnFwd: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var list: List<Entry> = emptyList()
    private var index = 0
    private var entry = Entry("movie", "", "")
    private var cands: List<String> = emptyList()
    private var attempt = 0
    private var liveRetry = 0
    private var overlayOn = false
    private var userSeeking = false
    private var epgSeq = 0

    private val hideRun = Runnable { hideOverlay() }
    private val saveRun = object : Runnable {
        override fun run() {
            save()
            handler.postDelayed(this, 10000)
        }
    }
    private val tickRun = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 500)
        }
    }
    private val watchdog = Runnable { fail(null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        list = PlayerHolder.list
        index = PlayerHolder.index
        if (list.isEmpty()) {
            finish()
            return
        }
        buildUi()
    }

    private fun buildUi() {
        root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())
        root.isFocusable = true
        surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        spinner = ProgressBar(this)
        root.addView(spinner, FrameLayout.LayoutParams(Ui.dp(52), Ui.dp(52), Gravity.CENTER))
        msg = label("", 16f, C.WHITE, Fonts.medium)
        msg.gravity = Gravity.CENTER
        msg.setPadding(Ui.dp(30), 0, Ui.dp(30), 0)
        msg.visibility = View.GONE
        root.addView(msg, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        overlay = FrameLayout(this)
        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.setPadding(Ui.dp(24), Ui.dp(18), Ui.dp(24), Ui.dp(30))
        top.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xCC000000.toInt(), 0x00000000))
        titleV = label("", 20f, C.WHITE, Fonts.bold, 1)
        subV = label("", 14f, C.MUTED, Fonts.regular, 1)
        top.addView(titleV)
        top.addView(subV, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(4) })
        overlay.addView(top, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.setPadding(Ui.dp(24), Ui.dp(30), Ui.dp(24), Ui.dp(18))
        bottom.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0xDD000000.toInt()))
        seekRow = LinearLayout(this)
        seekRow.orientation = LinearLayout.HORIZONTAL
        seekRow.gravity = Gravity.CENTER_VERTICAL
        curT = label("00:00", 13f, C.WHITE, Fonts.medium)
        totT = label("00:00", 13f, C.WHITE, Fonts.medium)
        seek = SeekBar(this)
        seek.keyProgressIncrement = 10000
        seekRow.addView(curT)
        seekRow.addView(seek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(8); rightMargin = Ui.dp(8) })
        seekRow.addView(totT)
        bottom.addView(seekRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.gravity = Gravity.CENTER
        btnPrev = button("‹ Anterior", false)
        btnBack = button("-10s", false)
        btnPlay = button("Pausar", true)
        btnFwd = button("+10s", false)
        btnNext = button("Próximo ›", false)
        for (b in listOf(btnPrev, btnBack, btnPlay, btnFwd, btnNext)) {
            btns.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = Ui.dp(5); rightMargin = Ui.dp(5) })
        }
        bottom.addView(btns, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(10) })
        overlay.addView(bottom, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        root.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        root.setOnClickListener { if (overlayOn) hideOverlay() else showOverlay() }
        btnPlay.setOnClickListener { togglePlay(); showOverlay() }
        btnBack.setOnClickListener { seekBy(-10000); showOverlay() }
        btnFwd.setOnClickListener { seekBy(10000); showOverlay() }
        btnPrev.setOnClickListener { step(-1) }
        btnNext.setOnClickListener { step(1) }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.seekTo(p.toLong())
                    curT.text = fmtTime(p.toLong())
                }
            }
            override fun onStartTrackingTouch(s: SeekBar) {
                userSeeking = true
            }
            override fun onStopTrackingTouch(s: SeekBar) {
                userSeeking = false
                showOverlay()
            }
        })
        hideOverlay()
    }

    override fun onStart() {
        super.onStart()
        if (list.isEmpty()) return
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Realtime/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        val rf = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        val lc = DefaultLoadControl.Builder().setBufferDurationsMs(15000, 50000, 1500, 3000).build()
        val p = ExoPlayer.Builder(this, rf)
            .setLoadControl(lc)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        p.setVideoSurfaceView(surface)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> spinner.visibility = View.VISIBLE
                    Player.STATE_READY -> {
                        handler.removeCallbacks(watchdog)
                        spinner.visibility = View.GONE
                        msg.visibility = View.GONE
                        liveRetry = 0
                    }
                    Player.STATE_ENDED -> onEnded()
                    else -> {}
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlay.text = if (isPlaying) "Pausar" else "Continuar"
            }
            override fun onPlayerError(error: PlaybackException) {
                fail(error)
            }
        })
        player = p
        startEntry(index)
        handler.postDelayed(saveRun, 10000)
        handler.post(tickRun)
    }

    override fun onStop() {
        handler.removeCallbacks(saveRun)
        handler.removeCallbacks(tickRun)
        handler.removeCallbacks(watchdog)
        handler.removeCallbacks(hideRun)
        save()
        player?.release()
        player = null
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    // ---------- reprodução ----------
    private fun isLive(): Boolean = entry.t == "live"

    private fun startEntry(i: Int) {
        index = i
        entry = list[i]
        cands = Api.candidates(entry)
        attempt = 0
        liveRetry = 0
        titleV.text = (if (isLive() && entry.num.isNotEmpty()) entry.num + "  " else "") + entry.name
        subV.text = ""
        seekRow.visibility = if (isLive()) View.GONE else View.VISIBLE
        btnBack.visibility = if (isLive()) View.GONE else View.VISIBLE
        btnFwd.visibility = if (isLive()) View.GONE else View.VISIBLE
        btnPrev.visibility = if (list.size > 1) View.VISIBLE else View.GONE
        btnNext.visibility = if (list.size > 1) View.VISIBLE else View.GONE
        btnPrev.text = if (isLive()) "‹ Canal" else "‹ Anterior"
        btnNext.text = if (isLive()) "Canal ›" else "Próximo ›"
        playCandidate()
        if (isLive()) loadEpg()
        showOverlay()
    }

    private fun playCandidate() {
        val p = player ?: return
        val url = cands[attempt]
        val b = MediaItem.Builder().setUri(url)
        if (url.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        var start = 0L
        if (!isLive() && attempt == 0) {
            val saved = Store.recentFind(entry.t, entry.id)
            val pos = saved?.pos ?: entry.pos
            val dur = saved?.dur ?: entry.dur
            if (pos > 10000 && (dur <= 0 || pos < dur - 15000)) start = pos
        }
        spinner.visibility = View.VISIBLE
        msg.visibility = View.GONE
        p.setMediaItem(b.build(), start)
        p.prepare()
        p.playWhenReady = true
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, if (isLive()) 20000 else 40000)
    }

    private fun fail(error: PlaybackException?) {
        handler.removeCallbacks(watchdog)
        attempt++
        if (attempt < cands.size) {
            playCandidate()
            return
        }
        spinner.visibility = View.GONE
        val dec = error != null && (
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
            )
        msg.text = if (dec) {
            "Este aparelho não consegue reproduzir este vídeo (formato H.265/HEVC ou áudio incompatível)."
        } else {
            "Não foi possível reproduzir agora. Verifique a internet ou tente outro conteúdo."
        }
        msg.visibility = View.VISIBLE
        showOverlay()
    }

    private fun onEnded() {
        if (isLive()) {
            if (liveRetry < 3) {
                liveRetry++
                handler.postDelayed({ if (player != null) playCandidate() }, 1000)
            }
            return
        }
        Store.recentRemove(entry.t, entry.id)
        if (entry.t == "episode" && index + 1 < list.size) {
            startEntry(index + 1)
        } else {
            finish()
        }
    }

    private fun step(d: Int) {
        val n = index + d
        if (n < 0 || n >= list.size) return
        save()
        startEntry(n)
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    private fun seekBy(ms: Long) {
        val p = player ?: return
        if (isLive()) return
        val dur = p.duration
        var t = p.currentPosition + ms
        if (t < 0) t = 0
        if (dur > 0 && t > dur - 1000) t = dur - 1000
        p.seekTo(t)
    }

    private fun save() {
        val p = player ?: return
        if (isLive() || entry.id.isEmpty()) return
        val pos = p.currentPosition
        val dur = p.duration
        if (dur <= 0 || pos < 5000) return
        if (pos > dur * 0.95) Store.recentRemove(entry.t, entry.id) else Store.recentUpsert(entry.copy(pos = pos, dur = dur))
    }

    private fun tick() {
        val p = player ?: return
        if (!overlayOn || isLive() || userSeeking) return
        val dur = p.duration
        if (dur > 0) {
            seek.max = dur.toInt()
            seek.progress = p.currentPosition.toInt()
            totT.text = fmtTime(dur)
        }
        curT.text = fmtTime(p.currentPosition)
    }

    private fun loadEpg() {
        val my = ++epgSeq
        val id = entry.id
        async({ Api.epg(id) }) { r ->
            if (r != null && my == epgSeq) {
                val now = System.currentTimeMillis() / 1000
                val cur = r.firstOrNull { it.start <= now && it.stop > now } ?: r.firstOrNull()
                val nxt = r.firstOrNull { it.start >= (cur?.stop ?: 0L) && it !== cur }
                var s = cur?.title ?: ""
                if (nxt != null) s += "   ·   a seguir: " + nxt.title
                subV.text = s
            }
        }
    }

    // ---------- controles ----------
    private fun showOverlay() {
        overlayOn = true
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideRun)
        handler.postDelayed(hideRun, 5000)
        if (!btnPlay.hasFocus() && !btnPrev.hasFocus() && !btnNext.hasFocus() && !btnBack.hasFocus() && !btnFwd.hasFocus() && !seek.hasFocus()) {
            btnPlay.requestFocus()
        }
    }

    private fun hideOverlay() {
        overlayOn = false
        overlay.visibility = View.GONE
        handler.removeCallbacks(hideRun)
        root.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val k = event.keyCode
        when (k) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> { togglePlay(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30000); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-30000); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> { step(1); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); return true }
        }
        if (!overlayOn) {
            when (k) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { showOverlay(); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { if (isLive()) step(1) else showOverlay(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { if (isLive()) step(-1) else showOverlay(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { if (isLive()) showOverlay() else { seekBy(-10000); showOverlay() }; return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (isLive()) showOverlay() else { seekBy(10000); showOverlay() }; return true }
            }
        } else {
            handler.removeCallbacks(hideRun)
            handler.postDelayed(hideRun, 5000)
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (overlayOn && Ui.isTv) {
            hideOverlay()
            return
        }
        super.onBackPressed()
    }
}
