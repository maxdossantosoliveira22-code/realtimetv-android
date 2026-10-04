package com.realtimetv.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalWide = compositionLocalOf { false }

object Neon {
    val bg0 = Color(0xFF04060F)
    val bg1 = Color(0xFF0A1030)
    val cyan = Color(0xFF00E5FF)
    val violet = Color(0xFF8B5CFF)
    val magenta = Color(0xFFFF3DCB)
    val text = Color(0xFFEAF2FF)
    val muted = Color(0xFF8FA3C7)
    val ok = Color(0xFF2BFF9A)
    val bad = Color(0xFFFF5470)
    val accent = Brush.horizontalGradient(listOf(cyan, violet))
    val accent2 = Brush.linearGradient(listOf(cyan, violet, magenta))
    val bgBrush = Brush.verticalGradient(listOf(bg1, bg0))
    val scrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))
    val scrimTop = Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
}

@Composable
fun RtvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon.cyan, secondary = Neon.violet, tertiary = Neon.magenta,
            background = Neon.bg0, surface = Neon.bg1, onSurface = Neon.text, onBackground = Neon.text,
            onPrimary = Color.Black,
        ),
        content = content,
    )
}

@Composable
fun T(
    text: String, size: TextUnit = 14.sp, weight: FontWeight = FontWeight.Normal, color: Color = Neon.text,
    modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null,
) {
    Text(text, modifier = modifier, color = color, fontSize = size, fontWeight = weight, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, textAlign = align)
}

@Composable
fun Wordmark(size: TextUnit = 28.sp) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("REALTIME", style = TextStyle(brush = Neon.accent2, fontSize = size, fontWeight = FontWeight.Black, letterSpacing = 3.sp))
        Spacer(Modifier.width(6.dp))
        Text("TV", style = TextStyle(brush = Brush.horizontalGradient(listOf(Neon.violet, Neon.magenta)), fontSize = size * 0.6f, fontWeight = FontWeight.Black, letterSpacing = 2.sp))
    }
}

fun Modifier.glass(shape: Shape = RoundedCornerShape(18.dp)): Modifier =
    this.clip(shape)
        .background(Brush.linearGradient(listOf(Color(0x2AFFFFFF), Color(0x0EFFFFFF))))
        .border(1.dp, Color(0x26FFFFFF), shape)

/** Elemento navegável por toque e por controle remoto (D-pad): cresce e brilha quando focado. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Focusable(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (focused) 1.06f else 1f, label = "focusScale")
    Box(
        modifier
            .scale(sc)
            .shadow(if (focused) 18.dp else 0.dp, shape, ambientColor = Neon.cyan, spotColor = Neon.violet)
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 2.dp else 0.dp, if (focused) Neon.accent2 else SolidColor(Color.Transparent), shape)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) { content(focused) }
}

@Composable
fun NeonButton(text: String, icon: ImageVector? = null, primary: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Focusable(modifier = modifier, shape = shape, onClick = onClick) { f ->
        val bg = if (primary) Modifier.background(Neon.accent) else Modifier.glass(shape)
        Row(
            Modifier.then(bg).padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { Icon(icon, null, tint = if (primary) Color.Black else Neon.text, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            T(text, 15.sp, FontWeight.Bold, if (primary) Color.Black else Neon.text, maxLines = 1)
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, size: Dp = 46.dp, tint: Color = Neon.text, onClick: () -> Unit) {
    Focusable(shape = CircleShape, onClick = onClick) { _ ->
        Box(Modifier.size(size).glass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f))
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Focusable(shape = shape, onClick = onClick) { _ ->
        Box(
            Modifier.then(if (selected) Modifier.background(Neon.accent) else Modifier.glass(shape)).padding(horizontal = 16.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) { T(text, 14.sp, FontWeight.SemiBold, if (selected) Color.Black else Neon.text, maxLines = 1) }
    }
}

@Composable
fun Bar(frac: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(Neon.accent))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).clip(CircleShape).background(Neon.accent2))
        Spacer(Modifier.width(10.dp))
        T(text, 20.sp, FontWeight.Bold)
    }
}


/** Fundo animado em aurora (só nos menus; some quando o vídeo está tocando para não gastar a GPU). */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0f, infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Box(
        modifier.fillMaxSize().background(Neon.bg0).drawBehind {
            val w = size.width
            val h = size.height
            val r = (if (w > h) w else h) * 0.62f
            drawRect(Brush.radialGradient(listOf(Color(0x4D00E5FF), Color.Transparent), center = Offset(w * (0.05f + 0.55f * a), h * (0.10f + 0.35f * b)), radius = r))
            drawRect(Brush.radialGradient(listOf(Color(0x468B5CFF), Color.Transparent), center = Offset(w * (0.95f - 0.55f * a), h * (0.85f - 0.40f * b)), radius = r))
            drawRect(Brush.radialGradient(listOf(Color(0x30FF3DCB), Color.Transparent), center = Offset(w * (0.50f + 0.30f * b), h * (0.50f - 0.30f * a)), radius = r * 0.7f))
        },
    )
}
