package com.realtimetv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Neon.text, unfocusedTextColor = Neon.text,
    focusedBorderColor = Neon.cyan, unfocusedBorderColor = Color(0x44FFFFFF),
    cursorColor = Neon.cyan, focusedLabelColor = Neon.cyan, unfocusedLabelColor = Neon.muted,
)

/** Cartaz (filme / série) */
@Composable
fun PosterCard(title: String, image: String, modifier: Modifier = Modifier, fav: Boolean = false, progress: Float = 0f, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Focusable(modifier = modifier, shape = shape, onClick = onClick, onLongClick = onLong) { _ ->
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B2250), Color(0xFF0C1030))))) {
            if (image.isNotEmpty()) AsyncImage(model = image, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Neon.scrim).padding(start = 8.dp, end = 8.dp, top = 26.dp, bottom = if (progress > 0f) 12.dp else 8.dp)) {
                T(title, 12.sp, FontWeight.SemiBold, maxLines = 2)
            }
            if (progress > 0f) Bar(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
            if (fav) Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFD54A), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp))
        }
    }
}

/** Cartão de canal pequeno (linhas horizontais da tela inicial) */
@Composable
fun ChannelCard(ch: Channel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Focusable(modifier = modifier, shape = shape, onClick = onClick) { _ ->
        Column(Modifier.fillMaxSize().glass(shape).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (ch.logo.isNotEmpty()) AsyncImage(model = ch.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Rounded.PlayArrow, null, tint = Neon.cyan, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(8.dp))
            T(ch.name, 12.sp, FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
        }
    }
}

@Composable
fun LoginScreen(vm: AppVM) {
    var u by remember { mutableStateOf(vm.store.user) }
    var p by remember { mutableStateOf("") }
    fun go() { if (u.isNotBlank() && p.isNotBlank() && !vm.busy) vm.login(u, p) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp).glass(RoundedCornerShape(28.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark(34.sp)
            Spacer(Modifier.height(4.dp))
            T("Entre com os dados da sua assinatura", 13.sp, color = Neon.muted)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                value = u, onValueChange = { u = it }, singleLine = true, label = { Text2("Usuário") },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = p, onValueChange = { p = it }, singleLine = true, label = { Text2("Senha") },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { go() }),
            )
            if (vm.loginError.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                T(vm.loginError, 13.sp, color = Neon.bad, align = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            NeonButton(if (vm.busy) "Entrando…" else "Entrar", modifier = Modifier.fillMaxWidth()) { go() }
            Spacer(Modifier.height(12.dp))
            T("Acesso exclusivo · " + Api.host, 11.sp, color = Neon.muted)
        }
    }
}

@Composable
fun Text2(s: String) { T(s, 14.sp, color = Neon.muted) }

@Composable
fun Avatar(p: Profile, size: Dp = 96.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Color(p.color), Color(0xFF8B5CFF)))), contentAlignment = Alignment.Center) {
        T(p.name.take(1).uppercase(), (size.value * 0.42f).sp, FontWeight.Black, Color.Black)
    }
}

@Composable
fun ProfilePicker(vm: AppVM) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
            Wordmark(26.sp)
            Spacer(Modifier.height(18.dp))
            if (!adding) {
                T("Quem está assistindo?", 24.sp, FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    items(vm.profiles) { pf ->
                        Focusable(shape = RoundedCornerShape(20.dp), onClick = { vm.selectProfile(pf) }) { _ ->
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Avatar(pf)
                                Spacer(Modifier.height(8.dp))
                                T(pf.name, 15.sp, FontWeight.SemiBold, maxLines = 1)
                                if (pf.kids) T("Infantil", 11.sp, color = Neon.cyan)
                            }
                        }
                    }
                    if (vm.profiles.size < 6) item {
                        Focusable(shape = RoundedCornerShape(20.dp), onClick = { adding = true }) { _ ->
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(96.dp).glass(CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Neon.cyan, modifier = Modifier.size(40.dp)) }
                                Spacer(Modifier.height(8.dp))
                                T("Adicionar", 15.sp, FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
                NeonButton("Sair da conta", primary = false) { vm.logout() }
            } else {
                Column(Modifier.widthIn(max = 380.dp).glass(RoundedCornerShape(24.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("Novo perfil", 20.sp, FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it.take(14) }, singleLine = true, label = { Text2("Nome") }, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Chip("Adulto", !kids) { kids = false }
                        Spacer(Modifier.width(8.dp))
                        Chip("Infantil", kids) { kids = true }
                    }
                    if (kids) { Spacer(Modifier.height(8.dp)); T("O perfil infantil não vê categorias bloqueadas.", 12.sp, color = Neon.muted, align = TextAlign.Center) }
                    Spacer(Modifier.height(18.dp))
                    Row {
                        NeonButton("Cancelar", primary = false) { adding = false; name = "" }
                        Spacer(Modifier.width(10.dp))
                        NeonButton("Criar") { vm.addProfile(name, kids); adding = false; name = ""; kids = false }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(vm: AppVM) {
    val wide = LocalWide.current
    LaunchedEffect(Unit) { vm.loadHomeExtras() }
    val cont = vm.progressList.sortedByDescending { it.ts }
    val favLive = vm.favs.filter { it.startsWith("live:") }.mapNotNull { k -> vm.live.firstOrNull { "live:${it.id}" == k } }
    val favRadio = vm.favs.filter { it.startsWith("radio:") }.mapNotNull { k -> vm.radios.firstOrNull { "radio:${it.id}" == k } }
    val favMovies = vm.favs.filter { it.startsWith("movie:") }
    val favSeries = vm.favs.filter { it.startsWith("series:") }
    val visibleLive = vm.live.filter { !vm.hiddenCat("live", it.catId) }
    val cardH = if (wide) 210.dp else 170.dp
    val cardW = cardH * 0.67f

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item { Hero(vm, cont.firstOrNull()) }
        if (cont.isNotEmpty()) item {
            SectionTitle("Continuar assistindo")
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(cont) { p ->
                    PosterCard(p.title, p.image, Modifier.size(cardW * 1.5f, cardH * 0.72f), progress = if (p.dur > 0) p.pos.toFloat() / p.dur else 0f, onLong = { vm.removeProgress(p.key) }) { vm.resume(p) }
                }
            }
        }
        if (favLive.isNotEmpty() || favRadio.isNotEmpty()) item {
            SectionTitle("Meus favoritos")
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(favLive) { c -> ChannelCard(c, Modifier.size(120.dp, 120.dp)) { vm.playLive(favLive, favLive.indexOf(c)) } }
                items(favRadio) { c -> ChannelCard(c, Modifier.size(120.dp, 120.dp)) { vm.playLive(favRadio, favRadio.indexOf(c), true) } }
            }
        }
        if (visibleLive.isNotEmpty()) item {
            SectionTitle("TV ao vivo")
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val first = visibleLive.take(40)
                items(first) { c -> ChannelCard(c, Modifier.size(120.dp, 120.dp)) { vm.playLive(first, first.indexOf(c)) } }
            }
        }
        if (vm.radios.isNotEmpty()) item {
            SectionTitle("Rádios da sua região")
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(vm.radios.take(40)) { c -> ChannelCard(c, Modifier.size(120.dp, 120.dp)) { vm.playLive(vm.radios, vm.radios.indexOf(c), true) } }
            }
        }
        val rm = vm.recentMovies.filter { !vm.hiddenCat("movie", it.catId) }
        if (rm.isNotEmpty()) item {
            SectionTitle("Filmes recentes")
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(rm) { m -> PosterCard(m.name, m.poster, Modifier.size(cardW, cardH), fav = vm.isFav("movie:${m.id}"), onLong = { vm.toggleFav("movie:${m.id}") }) { vm.push(Overlay.MovieDet(m)) } }
            }
        }
        val rs = vm.recentSeries.filter { !vm.hiddenCat("series", it.catId) }
        if (rs.isNotEmpty()) item {
            SectionTitle("Séries recentes")
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(rs) { s -> PosterCard(s.name, s.cover, Modifier.size(cardW, cardH), fav = vm.isFav("series:${s.id}"), onLong = { vm.toggleFav("series:${s.id}") }) { vm.push(Overlay.SeriesDet(s)) } }
            }
        }
        if (favMovies.isNotEmpty() || favSeries.isNotEmpty()) item {
            val fm = vm.recentMovies.filter { favMovies.contains("movie:${it.id}") }
            val fs = vm.recentSeries.filter { favSeries.contains("series:${it.id}") }
            if (fm.isNotEmpty() || fs.isNotEmpty()) {
                SectionTitle("Filmes e séries favoritos")
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(fm) { m -> PosterCard(m.name, m.poster, Modifier.size(cardW, cardH), fav = true) { vm.push(Overlay.MovieDet(m)) } }
                    items(fs) { s -> PosterCard(s.name, s.cover, Modifier.size(cardW, cardH), fav = true) { vm.push(Overlay.SeriesDet(s)) } }
                }
            }
        }
    }
}

private class Slide(val label: String, val title: String, val image: String, val button: String, val action: () -> Unit)

@Composable
private fun Hero(vm: AppVM, last: Progress?) {
    val wide = LocalWide.current
    val h = if (wide) 300.dp else 210.dp
    val slides = ArrayList<Slide>()
    if (last != null) slides.add(Slide("CONTINUE DE ONDE PAROU", last.title, last.image, "Continuar") { vm.resume(last) })
    for (m in vm.recentMovies.filter { !vm.hiddenCat("movie", it.catId) }.take(3)) slides.add(Slide("LANÇAMENTO", m.name, m.poster, "Ver agora") { vm.push(Overlay.MovieDet(m)) })
    for (sr in vm.recentSeries.filter { !vm.hiddenCat("series", it.catId) }.take(2)) slides.add(Slide("SÉRIE EM DESTAQUE", sr.name, sr.cover, "Ver episódios") { vm.push(Overlay.SeriesDet(sr)) })
    if (slides.isEmpty()) slides.add(Slide("BEM-VINDO, " + (vm.profile?.name ?: "").uppercase(), "Tudo em um só lugar: TV, filmes, séries e rádios.", "", "Abrir TV ao vivo") { vm.tab = Tab.LIVE })
    var idx by remember { mutableStateOf(0) }
    LaunchedEffect(slides.size) {
        while (slides.size > 1) {
            kotlinx.coroutines.delay(6500)
            idx = (idx + 1) % slides.size
        }
    }
    Box(Modifier.fillMaxWidth().height(h).padding(16.dp).clip(RoundedCornerShape(26.dp)).glass(RoundedCornerShape(26.dp))) {
        androidx.compose.animation.Crossfade(targetState = if (idx < slides.size) idx else 0, label = "hero") { i ->
            val sl = slides[if (i < slides.size) i else 0]
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF12307A), Color(0xFF3A1B7A), Color(0xFF7A1B6B)))))
                if (sl.image.isNotEmpty()) AsyncImage(model = sl.image, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.55f, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xEE04060F), Color.Transparent))))
                Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 24.dp).widthIn(max = 520.dp)) {
                    T(sl.label, 12.sp, FontWeight.Bold, Neon.cyan)
                    Spacer(Modifier.height(6.dp))
                    T(sl.title, if (wide) 30.sp else 22.sp, FontWeight.Black, maxLines = 2)
                    Spacer(Modifier.height(14.dp))
                    NeonButton(sl.button, Icons.Rounded.PlayArrow) { sl.action() }
                }
            }
        }
        if (slides.size > 1) {
            Row(Modifier.align(Alignment.BottomEnd).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in slides.indices) {
                    Box(Modifier.size(width = if (i == idx) 22.dp else 8.dp, height = 8.dp).clip(CircleShape).background(if (i == idx) Neon.cyan else Color(0x55FFFFFF)))
                }
            }
        }
    }
}
