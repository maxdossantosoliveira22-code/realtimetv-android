package com.realtimetv.app

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val digitKeys = mapOf(
    Key.Zero to "0", Key.One to "1", Key.Two to "2", Key.Three to "3", Key.Four to "4",
    Key.Five to "5", Key.Six to "6", Key.Seven to "7", Key.Eight to "8", Key.Nine to "9",
)

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%02d:%02d".format(m, ss)
}

fun fmtClock(sec: Long): String {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = sec * 1000
    return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

@Composable
fun PlayerScreen(vm: AppVM, req: PlayReq) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLive = req is PlayReq.Live
    val audioMode = req is PlayReq.Live && req.audio
    val liveList: List<Channel> = if (req is PlayReq.Live) req.list else emptyList()
    val vodItems: List<VodItem> = if (req is PlayReq.Vod) req.items else emptyList()
    val startIndex = when (req) {
        is PlayReq.Live -> req.index
        is PlayReq.Vod -> req.index
    }

    var index by remember { mutableStateOf(startIndex) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var show by remember { mutableStateOf(true) }
    var tick by remember { mutableStateOf(0) }
    var panel by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") }
    var retries by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var optPanel by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(0) }
    var trackVer by remember { mutableStateOf(0) }
    var sleepAt by remember { mutableStateOf(0L) }
    val aspectModes = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)
    val aspectNames = listOf("Ajustar", "Preencher", "Esticar")

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val itemFocus = remember { FocusRequester() }

    // tela cheia + paisagem
    DisposableEffect(Unit) {
        val act = ctx as? Activity
        if (act != null) {
            if (!audioMode) act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val c = WindowCompat.getInsetsController(act.window, act.window.decorView)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (act != null) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(act.window, act.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val exo = remember {
        val lc = if (isLive) DefaultLoadControl.Builder().setBufferDurationsMs(3000, 15000, 1000, 2000).build()
        else DefaultLoadControl.Builder().setBufferDurationsMs(15000, 60000, 2500, 5000).build()
        val p = ExoPlayer.Builder(ctx).setLoadControl(lc).build()
        p.setAudioAttributes(AudioAttributes.DEFAULT, true)
        p
    }

    fun curVod(): VodItem? = vodItems.getOrNull(index)

    fun saveProg() {
        val v = curVod() ?: return
        val d = exo.duration
        val p = exo.currentPosition
        if (d <= 0 || p < 5000) return
        if (p > d * 95 / 100) vm.removeProgress(v.key)
        else vm.saveProgress(Progress(v.key, v.type, v.id, v.title, v.image, v.ext, p, d, System.currentTimeMillis(), v.seriesId, v.season, v.ep))
    }

    fun loadCurrent() {
        error = ""
        if (isLive) {
            val c = liveList.getOrNull(index) ?: return
            val item = MediaItem.Builder().setUri(Api.liveUrl(c.id))
                .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1.04f).setMaxOffsetMs(15000).build())
                .build()
            exo.setMediaItem(item)
        } else {
            val v = curVod() ?: return
            val saved = vm.progressFor(v.key)
            val start = if (saved != null && saved.pos > 10000 && saved.dur > 0 && saved.pos < saved.dur * 95 / 100) saved.pos else 0L
            exo.setMediaItem(MediaItem.fromUri(v.url), start)
        }
        exo.prepare()
        exo.playWhenReady = true
    }

    fun goTo(i: Int) {
        val size = if (isLive) liveList.size else vodItems.size
        if (size == 0) return
        if (!isLive) saveProg()
        retries = 0
        index = ((i % size) + size) % size
    }

    LaunchedEffect(index) {
        loadCurrent()
        show = true
        tick++
    }

    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { trackVer++ }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { retries = 0; error = "" }
                if (state == Player.STATE_ENDED && !isLive) {
                    val v = curVod()
                    if (v != null) vm.removeProgress(v.key)
                    if (index < vodItems.size - 1) index = index + 1 else vm.closeTop()
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                if (retries < 6) {
                    retries++
                    scope.launch {
                        delay(if (isLive) 1500L else 2500L)
                        if (isLive) exo.seekToDefaultPosition()
                        exo.prepare()
                    }
                } else {
                    error = "Sinal indisponível. Verifique sua conexão e tente novamente."
                }
            }
        }
        exo.addListener(l)
        onDispose {
            saveProg()
            exo.removeListener(l)
            exo.release()
        }
    }

    // posição / salvar progresso
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            if (!dragging) pos = exo.currentPosition
            dur = if (exo.duration > 0) exo.duration else 0L
            delay(500)
            n++
            if (!isLive && n % 20 == 0) saveProg()
        }
    }

    // esconder controles
    LaunchedEffect(show, tick, panel, optPanel) {
        if (show && !panel && !optPanel) {
            delay(4500)
            show = false
        }
    }

    // foco: controles quando visíveis, raiz quando ocultos
    LaunchedEffect(show) {
        delay(150)
        try { if (show) playFocus.requestFocus() else rootFocus.requestFocus() } catch (e: Exception) { }
    }
    LaunchedEffect(panel) {
        if (panel) {
            delay(250)
            try { itemFocus.requestFocus() } catch (e: Exception) { }
        } else {
            delay(100)
            try { rootFocus.requestFocus() } catch (e: Exception) { }
        }
    }

    // troca de canal digitando o número
    LaunchedEffect(digits) {
        if (digits.isNotEmpty()) {
            delay(1800)
            val n = digits.toIntOrNull()
            digits = ""
            if (n != null) {
                val i = liveList.indexOfFirst { it.num == n }
                if (i >= 0) goTo(i)
            }
        }
    }

    val epg by produceState(emptyList<EpgItem>(), index) {
        value = emptyList()
        val c = liveList.getOrNull(index)
        if (c != null) value = vm.epgFor(c.id, 3)
    }

    fun toggle() { if (exo.isPlaying) exo.pause() else exo.play() }

    fun trackLabel(type: Int, offText: String): String {
        val v = trackVer
        val groups = exo.currentTracks.groups.filter { it.type == type && it.isSupported() }
        if (groups.isEmpty()) return "indisponível"
        val sel = groups.firstOrNull { it.isSelected() }
        if (sel == null) return offText + " (" + groups.size + ")"
        val lang = sel.getTrackFormat(0).language ?: ""
        return (if (lang.isEmpty()) "faixa" else lang) + " (" + groups.size + ")" + (if (v < 0) "" else "")
    }

    fun cycleTrack(type: Int, allowOff: Boolean) {
        val groups = exo.currentTracks.groups.filter { it.type == type && it.isSupported() }
        if (groups.isEmpty()) return
        val cur = groups.indexOfFirst { it.isSelected() }
        val next = cur + 1
        val b = exo.trackSelectionParameters.buildUpon()
        if (next >= groups.size) {
            if (allowOff) b.setTrackTypeDisabled(type, true)
            else b.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(groups[0].mediaTrackGroup, 0))
        } else {
            b.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(groups[next].mediaTrackGroup, 0))
        }
        exo.trackSelectionParameters = b.build()
    }

    fun cycleSleep() {
        val now = System.currentTimeMillis()
        val left = if (sleepAt > now) (sleepAt - now) / 60000 else 0L
        sleepAt = when {
            left <= 0L -> now + 15 * 60000L
            left <= 15L -> now + 30 * 60000L
            left <= 30L -> now + 60 * 60000L
            else -> 0L
        }
    }

    LaunchedEffect(sleepAt) {
        if (sleepAt > 0L) {
            val ms = sleepAt - System.currentTimeMillis()
            if (ms > 0) delay(ms)
            if (sleepAt > 0L) vm.closeTop()
        }
    }

    fun onKey(ev: KeyEvent): Boolean {
        if (ev.type != KeyEventType.KeyDown) return false
        tick++
        val k = ev.key
        val d = digitKeys[k]
        if (d != null && isLive) {
            digits = (digits + d).takeLast(4)
            return true
        }
        if (k == Key.ChannelUp) { if (isLive) goTo(index + 1); return true }
        if (k == Key.ChannelDown) { if (isLive) goTo(index - 1); return true }
        if (k == Key.MediaPlayPause) { toggle(); return true }
        if (k == Key.MediaPlay) { exo.play(); return true }
        if (k == Key.MediaPause) { exo.pause(); return true }
        if (k == Key.MediaNext || k == Key.MediaStepForward) { goTo(index + 1); return true }
        if (k == Key.MediaPrevious || k == Key.MediaStepBackward) { goTo(index - 1); return true }
        if (!show && !panel) {
            if (k == Key.DirectionUp) { if (isLive) goTo(index - 1) else show = true; return true }
            if (k == Key.DirectionDown) { if (isLive) goTo(index + 1) else show = true; return true }
            if (k == Key.DirectionLeft) { if (isLive) panel = true else exo.seekTo((exo.currentPosition - 10000).coerceAtLeast(0)); return true }
            if (k == Key.DirectionRight) { if (isLive) panel = true else exo.seekTo(exo.currentPosition + 10000); return true }
            if (k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter) { show = true; return true }
        }
        return false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { onKey(it) }
            .pointerInput(Unit) { detectTapGestures(onTap = { show = !show; tick++ }) },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    player = exo
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { pv -> pv.resizeMode = aspectModes[aspect] },
        )

        if (audioMode) RadioBackdrop(liveList.getOrNull(index), playing)

        if (buffering && error.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(54.dp), color = Neon.cyan, strokeWidth = 4.dp)
        }

        if (error.isNotEmpty()) {
            Column(Modifier.align(Alignment.Center).glass().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T(error, 16.sp, FontWeight.SemiBold, align = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                NeonButton("Tentar novamente") { retries = 0; loadCurrent() }
            }
        }

        if (digits.isNotEmpty()) {
            Box(Modifier.align(Alignment.TopEnd).padding(24.dp).glass().padding(horizontal = 22.dp, vertical = 12.dp)) {
                T(digits, 40.sp, FontWeight.Black, Neon.cyan)
            }
        }

        AnimatedVisibility(visible = show, modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Neon.scrimTop).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(Icons.Rounded.Close) { vm.closeTop() }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val ch = liveList.getOrNull(index)
                    T(if (isLive) (ch?.name ?: "") else (curVod()?.title ?: ""), 20.sp, FontWeight.Bold, maxLines = 1)
                    if (isLive) {
                        val now = epg.firstOrNull()
                        val sub = if (now != null) "${fmtClock(now.start)} – ${fmtClock(now.stop)}  ${now.title}" else "Canal ${ch?.num ?: ""}"
                        T(sub, 13.sp, color = Neon.muted, maxLines = 1)
                    }
                }
                if (isLive) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Neon.bad).padding(horizontal = 12.dp, vertical = 5.dp)) {
                        T(if (audioMode) "RÁDIO" else "AO VIVO", 12.sp, FontWeight.Black, Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(visible = show, modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(Neon.scrim).padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (isLive) {
                    val now = epg.firstOrNull()
                    if (now != null) {
                        val t = System.currentTimeMillis() / 1000
                        Bar(((t - now.start).toFloat() / (now.stop - now.start).coerceAtLeast(1)).coerceIn(0f, 1f), Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        val nx = epg.getOrNull(1)
                        if (nx != null) T("A seguir ${fmtClock(nx.start)}  ${nx.title}", 13.sp, color = Neon.muted, maxLines = 1)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(fmtTime(pos), 13.sp, color = Neon.muted)
                        Slider(
                            value = pos.coerceIn(0L, dur.coerceAtLeast(1L)).toFloat(),
                            onValueChange = { dragging = true; pos = it.toLong(); tick++ },
                            onValueChangeFinished = { exo.seekTo(pos); dragging = false },
                            valueRange = 0f..dur.coerceAtLeast(1L).toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Neon.cyan, activeTrackColor = Neon.cyan, inactiveTrackColor = Color(0x44FFFFFF)),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                        T(fmtTime(dur), 13.sp, color = Neon.muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    val many = (if (isLive) liveList.size else vodItems.size) > 1
                    if (many) { IconBtn(Icons.Rounded.SkipPrevious) { goTo(index - 1) }; Spacer(Modifier.width(14.dp)) }
                    if (!isLive) { IconBtn(Icons.Rounded.Replay10) { exo.seekTo((exo.currentPosition - 10000).coerceAtLeast(0)) }; Spacer(Modifier.width(14.dp)) }
                    Box(Modifier.focusRequester(playFocus)) {
                        IconBtn(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, size = 62.dp, tint = Neon.cyan) { toggle() }
                    }
                    if (!isLive) { Spacer(Modifier.width(14.dp)); IconBtn(Icons.Rounded.Forward10) { exo.seekTo(exo.currentPosition + 10000) } }
                    if (many) { Spacer(Modifier.width(14.dp)); IconBtn(Icons.Rounded.SkipNext) { goTo(index + 1) } }
                    Spacer(Modifier.width(14.dp))
                    IconBtn(Icons.Rounded.Settings) { optPanel = true }
                    if (isLive) {
                        val ch = liveList.getOrNull(index)
                        if (ch != null) {
                            val key = (if (audioMode) "radio:" else "live:") + ch.id
                            Spacer(Modifier.width(14.dp))
                            IconBtn(if (vm.isFav(key)) Icons.Rounded.Star else Icons.Rounded.StarBorder, tint = if (vm.isFav(key)) Color(0xFFFFD54A) else Neon.text) { vm.toggleFav(key) }
                        }
                        Spacer(Modifier.width(14.dp))
                        IconBtn(Icons.Rounded.GridView) { panel = true }
                    }
                }
            }
        }

        AnimatedVisibility(visible = optPanel, modifier = Modifier.align(Alignment.CenterEnd)) {
            Column(Modifier.fillMaxHeight().width(340.dp).background(Color(0xE60A1030)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { T("Opções", 20.sp, FontWeight.Bold) }
                    IconBtn(Icons.Rounded.Close, size = 40.dp) { optPanel = false }
                }
                NeonButton("Imagem: " + aspectNames[aspect], primary = false, modifier = Modifier.fillMaxWidth()) { aspect = (aspect + 1) % aspectModes.size }
                NeonButton("Áudio: " + trackLabel(C.TRACK_TYPE_AUDIO, "desligado"), primary = false, modifier = Modifier.fillMaxWidth()) { cycleTrack(C.TRACK_TYPE_AUDIO, false) }
                NeonButton("Legenda: " + trackLabel(C.TRACK_TYPE_TEXT, "desligada"), primary = false, modifier = Modifier.fillMaxWidth()) { cycleTrack(C.TRACK_TYPE_TEXT, true) }
                val leftMin = if (sleepAt > System.currentTimeMillis()) (sleepAt - System.currentTimeMillis()) / 60000 + 1 else 0L
                NeonButton("Dormir: " + (if (leftMin > 0L) "$leftMin min" else "desligado"), primary = false, modifier = Modifier.fillMaxWidth()) { cycleSleep() }
            }
        }

        AnimatedVisibility(visible = panel && isLive, modifier = Modifier.align(Alignment.CenterEnd)) {
            val ls = rememberLazyListState()
            LaunchedEffect(Unit) { ls.scrollToItem((index - 2).coerceAtLeast(0)) }
            Column(Modifier.fillMaxHeight().width(340.dp).background(Color(0xE60A1030)).padding(top = 12.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { T(if (audioMode) "Rádios" else "Canais", 20.sp, FontWeight.Bold) }
                    IconBtn(Icons.Rounded.Close, size = 40.dp) { panel = false }
                }
                LazyColumn(state = ls, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(liveList) { i, c ->
                        val cur = i == index
                        Focusable(
                            modifier = Modifier.fillMaxWidth().then(if (cur) Modifier.focusRequester(itemFocus) else Modifier),
                            shape = RoundedCornerShape(14.dp),
                            onClick = { goTo(i); panel = false },
                        ) { _ ->
                            Row(
                                Modifier.fillMaxWidth().then(if (cur) Modifier.background(Neon.accent) else Modifier.glass(RoundedCornerShape(14.dp))).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                T("${c.num}", 13.sp, FontWeight.Bold, if (cur) Color.Black else Neon.cyan, modifier = Modifier.width(34.dp))
                                T(c.name, 15.sp, FontWeight.SemiBold, if (cur) Color.Black else Neon.text, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioBackdrop(ch: Channel?, playing: Boolean) {
    Box(Modifier.fillMaxSize().background(Neon.bgBrush), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(180.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                if (ch != null && ch.logo.isNotEmpty()) AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.size(130.dp))
                else Icon(Icons.Rounded.PlayArrow, null, tint = Neon.cyan, modifier = Modifier.size(80.dp))
            }
            Spacer(Modifier.height(24.dp))
            Equalizer(playing)
            Spacer(Modifier.height(10.dp))
            T(ch?.name ?: "", 24.sp, FontWeight.Black)
        }
    }
}

@Composable
private fun Equalizer(active: Boolean) {
    val t = rememberInfiniteTransition(label = "eq")
    val fr = (0 until 9).map { i ->
        t.animateFloat(0.15f, 1f, infiniteRepeatable(tween(420 + i * 90), RepeatMode.Reverse), label = "bar$i")
    }
    Row(Modifier.height(54.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 9) {
            val h = if (active) fr[i].value else 0.12f
            Box(Modifier.width(8.dp).fillMaxHeight(h).clip(RoundedCornerShape(4.dp)).background(Neon.accent))
        }
    }
}
