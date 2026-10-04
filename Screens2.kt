package com.realtimetv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
private fun CatChips(favLabel: String?, cats: List<Category>, selected: String, locked: (Category) -> Boolean, onSelect: (String) -> Unit, onSelectCat: (Category) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Chip("Todos", selected == "all") { onSelect("all") } }
        if (favLabel != null) item { Chip(favLabel, selected == "fav") { onSelect("fav") } }
        items(cats) { c -> Chip((if (locked(c)) "🔒 " else "") + c.name, selected == c.id) { onSelectCat(c) } }
    }
}

// ======================= TV AO VIVO / GUIA =======================
@Composable
fun ChannelBrowser(vm: AppVM, detailed: Boolean) {
    val present = remember(vm.live) { vm.live.map { it.catId }.toSet() }
    val cats = vm.visibleCats("live", vm.liveCats.filter { it.id in present })
    val sel = if (detailed) vm.guideCat else vm.liveCat
    fun setSel(v: String) { if (detailed) vm.guideCat = v else vm.liveCat = v }
    val list = when (sel) {
        "fav" -> vm.live.filter { vm.isFav("live:${it.id}") }
        "all" -> vm.live.filter { !vm.hiddenCat("live", it.catId) }
        else -> vm.live.filter { it.catId == sel }
    }
    Column(Modifier.fillMaxSize()) {
        SectionTitle(if (detailed) "Guia de programação" else "TV ao vivo")
        CatChips("★ Favoritos", cats, sel, { c -> vm.hiddenCat("live", c.id) }, { setSel(it) }, { c -> vm.requestUnlock("live", c.id) { setSel(c.id) } })
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T(if (sel == "fav") "Marque canais com ★ para vê-los aqui." else "Nenhum canal nesta categoria.", 15.sp, color = Neon.muted) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (detailed) 380.dp else 310.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { ch -> ChannelRow(vm, ch, list, detailed) }
            }
        }
    }
}

@Composable
private fun ChannelRow(vm: AppVM, ch: Channel, list: List<Channel>, detailed: Boolean) {
    val epg by produceState(emptyList<EpgItem>(), ch.id) {
        if (ch.epgId.isNotEmpty()) value = vm.epgFor(ch.id, if (detailed) 4 else 2)
    }
    val now = System.currentTimeMillis() / 1000
    val cur = epg.firstOrNull { it.start <= now && it.stop > now } ?: epg.firstOrNull()
    val key = "live:${ch.id}"
    val shape = RoundedCornerShape(16.dp)
    Focusable(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        onClick = { vm.playLive(list, list.indexOf(ch)) },
        onLongClick = { vm.toggleFav(key) },
    ) { _ ->
        Row(Modifier.fillMaxWidth().glass(shape).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)), contentAlignment = Alignment.Center) {
                if (ch.logo.isNotEmpty()) AsyncImage(model = ch.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                else T("${ch.num}", 16.sp, FontWeight.Bold, Neon.cyan)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("${ch.num}", 12.sp, FontWeight.Bold, Neon.cyan)
                    Spacer(Modifier.width(8.dp))
                    T(ch.name, 15.sp, FontWeight.Bold, maxLines = 1)
                }
                if (cur != null) {
                    Spacer(Modifier.height(3.dp))
                    T("${fmtClock(cur.start)}  ${cur.title}", 12.sp, color = Neon.muted, maxLines = 1)
                    Spacer(Modifier.height(5.dp))
                    Bar(((now - cur.start).toFloat() / (cur.stop - cur.start).coerceAtLeast(1)).coerceIn(0f, 1f), Modifier.fillMaxWidth())
                    if (detailed) for (n in epg.filter { it.start >= cur.stop }.take(2)) {
                        Spacer(Modifier.height(3.dp))
                        T("${fmtClock(n.start)}  ${n.title}", 11.sp, color = Neon.muted, maxLines = 1)
                    }
                } else {
                    Spacer(Modifier.height(3.dp))
                    T("Programação indisponível", 12.sp, color = Neon.muted, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            IconBtn(if (vm.isFav(key)) Icons.Rounded.Star else Icons.Rounded.StarBorder, size = 36.dp, tint = if (vm.isFav(key)) Color(0xFFFFD54A) else Neon.muted) { vm.toggleFav(key) }
        }
    }
}

// ======================= FILMES =======================
@Composable
fun MoviesScreen(vm: AppVM) {
    val wide = LocalWide.current
    val cats = vm.visibleCats("movie", vm.vodCats)
    val sel = vm.movieCat
    var items by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf("") }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(sel, retry) {
        loading = true; err = ""
        try {
            items = if (sel == "fav") { vm.ensureAllVod(); vm.allMovies.filter { vm.isFav("movie:${it.id}") } } else vm.movies(sel)
        } catch (e: Exception) { err = "Não foi possível carregar os filmes."; items = emptyList() }
        loading = false
    }
    val shown = (if (sel == "all") items.filter { !vm.hiddenCat("movie", it.catId) } else items).take(600)
    Column(Modifier.fillMaxSize()) {
        SectionTitle("Filmes")
        CatChips("★ Favoritos", cats, sel, { c -> vm.hiddenCat("movie", c.id) }, { vm.movieCat = it }, { c -> vm.requestUnlock("movie", c.id) { vm.movieCat = c.id } })
        Body(loading, err, shown.isEmpty(), "Nenhum filme aqui.", { retry++ }) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (wide) 150.dp else 112.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { m ->
                    PosterCard(m.name, m.poster, Modifier.fillMaxWidth().aspectRatio(0.68f), fav = vm.isFav("movie:${m.id}"), onLong = { vm.toggleFav("movie:${m.id}") }) { vm.push(Overlay.MovieDet(m)) }
                }
            }
        }
    }
}

@Composable
private fun Body(loading: Boolean, err: String, empty: Boolean, emptyText: String, onRetry: () -> Unit, content: @Composable () -> Unit) {
    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Neon.cyan) }; return }
    if (err.isNotEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { T(err, 15.sp, color = Neon.muted); Spacer(Modifier.height(12.dp)); NeonButton("Tentar de novo") { onRetry() } }
        }
        return
    }
    if (empty) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T(emptyText, 15.sp, color = Neon.muted) }; return }
    content()
}

// ======================= SÉRIES =======================
@Composable
fun SeriesScreen(vm: AppVM) {
    val wide = LocalWide.current
    val cats = vm.visibleCats("series", vm.seriesCats)
    val sel = vm.seriesCat
    var items by remember { mutableStateOf<List<Series>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf("") }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(sel, retry) {
        loading = true; err = ""
        try {
            items = if (sel == "fav") { vm.ensureAllVod(); vm.allSeries.filter { vm.isFav("series:${it.id}") } } else vm.seriesIn(sel)
        } catch (e: Exception) { err = "Não foi possível carregar as séries."; items = emptyList() }
        loading = false
    }
    val shown = (if (sel == "all") items.filter { !vm.hiddenCat("series", it.catId) } else items).take(600)
    Column(Modifier.fillMaxSize()) {
        SectionTitle("Séries")
        CatChips("★ Favoritas", cats, sel, { c -> vm.hiddenCat("series", c.id) }, { vm.seriesCat = it }, { c -> vm.requestUnlock("series", c.id) { vm.seriesCat = c.id } })
        Body(loading, err, shown.isEmpty(), "Nenhuma série aqui.", { retry++ }) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (wide) 150.dp else 112.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { s ->
                    PosterCard(s.name, s.cover, Modifier.fillMaxWidth().aspectRatio(0.68f), fav = vm.isFav("series:${s.id}"), onLong = { vm.toggleFav("series:${s.id}") }) { vm.push(Overlay.SeriesDet(s)) }
                }
            }
        }
    }
}

// ======================= RÁDIO =======================
@Composable
fun RadioScreen(vm: AppVM) {
    val wide = LocalWide.current
    var onlyFav by remember { mutableStateOf(false) }
    val list = if (onlyFav) vm.radios.filter { vm.isFav("radio:${it.id}") } else vm.radios
    Column(Modifier.fillMaxSize()) {
        SectionTitle("Rádios")
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Chip("Todas", !onlyFav) { onlyFav = false }
            Spacer(Modifier.width(8.dp))
            Chip("★ Favoritas", onlyFav) { onlyFav = true }
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                T(if (onlyFav) "Segure uma rádio para favoritar." else "Nenhuma rádio na sua lista ainda.", 15.sp, color = Neon.muted, align = TextAlign.Center)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (wide) 150.dp else 112.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { c ->
                    val shape = RoundedCornerShape(20.dp)
                    Focusable(modifier = Modifier.fillMaxWidth().aspectRatio(0.9f), shape = shape, onClick = { vm.playLive(list, list.indexOf(c), true) }, onLongClick = { vm.toggleFav("radio:${c.id}") }) { _ ->
                        Column(Modifier.fillMaxSize().glass(shape).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box(Modifier.size(if (wide) 84.dp else 66.dp).clip(CircleShape).background(Color(0x1AFFFFFF)), contentAlignment = Alignment.Center) {
                                if (c.logo.isNotEmpty()) AsyncImage(model = c.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(10.dp))
                                else Icon(Icons.Rounded.PlayArrow, null, tint = Neon.cyan, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            T(c.name, 12.sp, FontWeight.SemiBold, maxLines = 2, align = TextAlign.Center)
                            if (vm.isFav("radio:${c.id}")) Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFD54A), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
