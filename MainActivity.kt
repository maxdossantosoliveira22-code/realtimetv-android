package com.realtimetv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RealtimeApp(vm) }
    }
}

@Composable
fun RealtimeApp(vm: AppVM) {
    RtvTheme {
        Box(Modifier.fillMaxSize().background(Neon.bg0)) {
            if (vm.overlays.lastOrNull() is Overlay.Player) Box(Modifier.fillMaxSize().background(Color.Black)) else AuroraBackground()
            BackHandler(enabled = vm.overlays.isNotEmpty() || (vm.account != null && vm.profile != null && vm.tab != Tab.HOME)) {
                if (vm.overlays.isNotEmpty()) vm.closeTop() else vm.tab = Tab.HOME
            }
            if (vm.booting) Splash()
            else if (vm.account == null) LoginScreen(vm)
            else if (vm.profile == null) ProfilePicker(vm)
            else MainShell(vm)
            val prompt = vm.pinPrompt
            if (prompt != null) PinDialog(vm, prompt)
        }
    }
}

@Composable
fun Splash() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "splash")
    val pulse by t.animateFloat(0.92f, 1.08f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.scale(pulse)) { Wordmark(40.sp) }
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = Neon.cyan, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
fun MainShell(vm: AppVM) {
    val top = vm.overlays.lastOrNull()
    if (top != null) {
        when (top) {
            is Overlay.Player -> PlayerScreen(vm, top.req)
            is Overlay.MovieDet -> MovieDetailScreen(vm, top.movie)
            is Overlay.SeriesDet -> SeriesDetailScreen(vm, top.series)
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        CompositionLocalProvider(LocalWide provides wide) {
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    SideRail(vm)
                    Box(Modifier.weight(1f).fillMaxHeight()) { TabContent(vm) }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    PhoneTop(vm)
                    Box(Modifier.weight(1f).fillMaxWidth()) { TabContent(vm) }
                    BottomBar(vm)
                }
            }
        }
    }
}

private fun tabIcon(t: Tab): ImageVector = when (t) {
    Tab.HOME -> Icons.Rounded.Home
    Tab.SEARCH -> Icons.Rounded.Search
    Tab.LIVE -> Icons.Rounded.LiveTv
    Tab.GUIDE -> Icons.Rounded.CalendarMonth
    Tab.MOVIES -> Icons.Rounded.Movie
    Tab.SERIES -> Icons.Rounded.VideoLibrary
    Tab.RADIO -> Icons.Rounded.Radio
    Tab.SETTINGS -> Icons.Rounded.Settings
}

@Composable
fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(modifier = modifier, shape = RoundedCornerShape(16.dp), onClick = onClick) { _ ->
        Column(
            Modifier.fillMaxWidth().then(if (selected) Modifier.background(Color(0x2600E5FF)) else Modifier).padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = if (selected) Neon.cyan else Neon.muted, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(3.dp))
            T(label, 11.sp, if (selected) FontWeight.Bold else FontWeight.Normal, if (selected) Neon.text else Neon.muted, maxLines = 1)
        }
    }
}

@Composable
fun SideRail(vm: AppVM) {
    Column(
        Modifier.width(96.dp).fillMaxHeight().background(Color(0x22000000)).verticalScroll(rememberScrollState()).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Wordmark(14.sp)
        Spacer(Modifier.height(10.dp))
        for (t in Tab.values()) NavItem(tabIcon(t), t.label, vm.tab == t) { vm.tab = t }
    }
}

@Composable
fun PhoneTop(vm: AppVM) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Wordmark(22.sp) }
        IconBtn(Icons.Rounded.Search, size = 40.dp) { vm.tab = Tab.SEARCH }
        Spacer(Modifier.width(8.dp))
        IconBtn(Icons.Rounded.CalendarMonth, size = 40.dp) { vm.tab = Tab.GUIDE }
        Spacer(Modifier.width(8.dp))
        IconBtn(Icons.Rounded.Settings, size = 40.dp) { vm.tab = Tab.SETTINGS }
    }
}

@Composable
fun BottomBar(vm: AppVM) {
    Row(Modifier.fillMaxWidth().background(Color(0x66000000)).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (t in listOf(Tab.HOME, Tab.LIVE, Tab.MOVIES, Tab.SERIES, Tab.RADIO)) NavItem(tabIcon(t), t.label, vm.tab == t, Modifier.weight(1f)) { vm.tab = t }
    }
}

@Composable
fun TabContent(vm: AppVM) {
    val needsData = vm.tab == Tab.HOME || vm.tab == Tab.LIVE || vm.tab == Tab.GUIDE || vm.tab == Tab.RADIO
    if (needsData && vm.live.isEmpty() && vm.radios.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (vm.dataLoading) CircularProgressIndicator(color = Neon.cyan)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(if (vm.dataError.isNotEmpty()) vm.dataError else "Nenhum canal disponível nesta conta.", 16.sp, color = Neon.muted)
                Spacer(Modifier.height(14.dp))
                NeonButton("Recarregar") { vm.reload() }
            }
        }
        return
    }
    when (vm.tab) {
        Tab.HOME -> HomeScreen(vm)
        Tab.SEARCH -> SearchScreen(vm)
        Tab.LIVE -> ChannelBrowser(vm, false)
        Tab.GUIDE -> ChannelBrowser(vm, true)
        Tab.MOVIES -> MoviesScreen(vm)
        Tab.SERIES -> SeriesScreen(vm)
        Tab.RADIO -> RadioScreen(vm)
        Tab.SETTINGS -> SettingsScreen(vm)
    }
}

@Composable
fun PinDialog(vm: AppVM, action: () -> Unit) {
    val creating = vm.store.pin.isEmpty()
    var v by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    fun confirm() {
        if (creating) {
            if (v.length == 4) { vm.store.pin = v; vm.pinPrompt = null; action() } else err = "Use 4 dígitos"
        } else {
            if (v == vm.store.pin) { vm.pinPrompt = null; action() } else { err = "PIN incorreto"; v = "" }
        }
    }
    AlertDialog(
        onDismissRequest = { vm.pinPrompt = null },
        containerColor = Neon.bg1,
        title = { T(if (creating) "Crie um PIN de 4 dígitos" else "Digite o PIN", 18.sp, FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = v,
                    onValueChange = { s -> if (s.length <= 4 && s.all { c -> c.isDigit() }) v = s },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = fieldColors(),
                )
                if (err.isNotEmpty()) { Spacer(Modifier.height(8.dp)); T(err, 13.sp, color = Neon.bad) }
            }
        },
        confirmButton = { NeonButton("OK") { confirm() } },
        dismissButton = { NeonButton("Cancelar", primary = false) { vm.pinPrompt = null } },
    )
}
