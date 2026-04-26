package com.flipfate.lbsroc

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF05060A)) {
                    FlipFateApp()
                }
            }
        }
    }
}

private data class LevelDef(
    val id: Int,
    val title: String,
    val subtitle: String,
    val theme: String,
    val duration: Long,
    val colorLight: Color,
    val colorDark: Color,
    val obstacles: List<Obstacle>
)

private data class Obstacle(
    val timeMs: Long,
    val lane: Int
)

private data class LevelStats(
    val attempts: Int = 0,
    val wins: Int = 0,
    val totalMoves: Int = 0
)

private sealed class Screen {
    data object Menu : Screen()
    data object LevelSelect : Screen()
    data object Stats : Screen()
    data object Settings : Screen()
    data object HowToPlay : Screen()
    data class Game(val level: LevelDef) : Screen()
    data class Result(val level: LevelDef, val won: Boolean, val moves: Int) : Screen()
}

@Composable
private fun FlipFateApp() {
    val context = LocalContext.current
    val levels = remember { buildLevels() }
    val stats = remember { mutableStateMapOf<Int, LevelStats>() }
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
    var vibrationEnabled by remember { mutableStateOf(loadVibrationEnabled(context)) }

    when (val s = screen) {
        is Screen.Menu -> {
            BackHandler {}
            MenuScreen(
                onPlay = { screen = Screen.LevelSelect },
                onStats = { screen = Screen.Stats },
                onSettings = { screen = Screen.Settings }
            )
        }
        is Screen.LevelSelect -> {
            BackHandler { screen = Screen.Menu }
            LevelSelectScreen(
                levels = levels,
                stats = stats,
                onSelect = { screen = Screen.Game(it) },
                onBack = { screen = Screen.Menu }
            )
        }
        is Screen.Stats -> {
            BackHandler { screen = Screen.Menu }
            StatsScreen(levels, stats) { screen = Screen.Menu }
        }
        is Screen.Settings -> {
            BackHandler { screen = Screen.Menu }
            SettingsScreen(
                vibrationEnabled = vibrationEnabled,
                onVibrationChange = {
                    vibrationEnabled = it
                    saveVibrationEnabled(context, it)
                    if (it) vibrate(context, 25)
                },
                onHowToPlay = { screen = Screen.HowToPlay },
                onBack = { screen = Screen.Menu }
            )
        }
        is Screen.HowToPlay -> {
            BackHandler { screen = Screen.Settings }
            HowToPlayScreen(onBack = { screen = Screen.Settings })
        }
        is Screen.Game -> {
            GameScreen(
                level = s.level,
                vibrationEnabled = vibrationEnabled,
                onExit = { screen = Screen.LevelSelect },
                onFinish = { won, _, moves ->
                    val prev = stats[s.level.id] ?: LevelStats()
                    stats[s.level.id] = prev.copy(
                        attempts = prev.attempts + 1,
                        wins = prev.wins + if (won) 1 else 0,
                        totalMoves = prev.totalMoves + moves
                    )
                    screen = Screen.Result(s.level, won, moves)
                }
            )
        }
        is Screen.Result -> {
            BackHandler { screen = Screen.LevelSelect }
            ResultScreen(
                level = s.level,
                won = s.won,
                moves = s.moves,
                stats = stats[s.level.id] ?: LevelStats(),
                onRetry = { screen = Screen.Game(s.level) },
                onMenu = { screen = Screen.LevelSelect },
                onNext = {
                    val next = levels.firstOrNull { it.id == s.level.id + 1 }
                    screen = if (next != null) Screen.Game(next) else Screen.LevelSelect
                }
            )
        }
    }
}

@Composable
private fun GameScreen(
    level: LevelDef,
    vibrationEnabled: Boolean,
    onExit: () -> Unit,
    onFinish: (won: Boolean, timeMs: Long, moves: Int) -> Unit
) {
    val context = LocalContext.current
    var elapsedMs by remember(level.id) { mutableLongStateOf(0L) }
    var lane by remember(level.id) { mutableIntStateOf(1) }
    var moveCount by remember(level.id) { mutableIntStateOf(0) }
    var running by remember(level.id) { mutableStateOf(true) }
    var hitObstacleTime by remember(level.id) { mutableLongStateOf(-9999L) }

    val animatedLane by animateFloatAsState(
        lane.toFloat(),
        tween(180),
        label = "lane"
    )

    BackHandler { onExit() }

    LaunchedEffect(level.id) {
        val startTime = System.currentTimeMillis()
        while (running) {
            delay(16)
            elapsedMs = System.currentTimeMillis() - startTime

            val hit = level.obstacles.any { obs ->
                val timeDiff = elapsedMs - obs.timeMs
                timeDiff in -80..80 && obs.lane == lane
            }

            if (hit) {
                hitObstacleTime = elapsedMs
                running = false
                if (vibrationEnabled) vibrate(context, 220)
                onFinish(false, elapsedMs, moveCount)
            }

            if (elapsedMs >= level.duration) {
                running = false
                onFinish(true, elapsedMs, moveCount)
            }
        }
    }

    val progress = (elapsedMs.toFloat() / level.duration).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        level.colorDark,
                        level.colorLight.copy(alpha = 0.4f),
                        level.colorDark
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GameHeader(
                level = level,
                moveCount = moveCount,
                progress = progress,
                onExit = onExit
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .pointerInput(level.id) {
                        detectTapGestures { offset ->
                            if (!running) return@detectTapGestures
                            val isLeftSide = offset.x < size.width / 2
                            val newLane = if (isLeftSide) {
                                (lane - 1).coerceAtLeast(0)
                            } else {
                                (lane + 1).coerceAtMost(2)
                            }
                            if (newLane != lane) {
                                lane = newLane
                                moveCount++
                                if (vibrationEnabled) vibrate(context, 18)
                            }
                        }
                    }
            ) {
                GameField(
                    level = level,
                    elapsedMs = elapsedMs,
                    laneFloat = animatedLane,
                    currentLane = lane
                )
            }
        }
    }
}

@Composable
private fun GameHeader(
    level: LevelDef,
    moveCount: Int,
    progress: Float,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(level.subtitle, fontSize = 10.sp, color = Color.White.copy(0.6f), letterSpacing = 1.sp)
                Text(level.title, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBadge(value = "$moveCount", color = Color(0xFF6C4BFF))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(0.3f))
                        .clickable(onClick = onExit)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Exit", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .background(Color(0xFF00E5A0), RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun StatBadge(value: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(0.3f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GameField(
    level: LevelDef,
    elapsedMs: Long,
    laneFloat: Float,
    currentLane: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "field")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
        label = "pulse"
    )
    val wave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "wave"
    )
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "scroll"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val laneWidth = w / 3f
            val playerY = h * 0.78f

            drawRoundRect(
                color = Color.Black.copy(0.35f),
                topLeft = Offset(0f, 0f),
                size = Size(w, h),
                cornerRadius = CornerRadius(40f)
            )

            for (i in 0..2) {
                val laneX = i * laneWidth
                val isActive = i == currentLane
                drawRect(
                    color = if (isActive) {
                        Color(0xFF00E5A0).copy(0.08f)
                    } else {
                        Color.White.copy(0.02f)
                    },
                    topLeft = Offset(laneX, 0f),
                    size = Size(laneWidth, h)
                )

                if (i > 0) {
                    val dashCount = 12
                    val dashLen = h / (dashCount * 2f)
                    for (d in 0 until dashCount) {
                        val yStart = (d * dashLen * 2f + scrollOffset * dashLen * 2f) % h
                        drawRect(
                            color = Color.White.copy(0.15f),
                            topLeft = Offset(laneX - 1f, yStart),
                            size = Size(2f, dashLen)
                        )
                    }
                }
            }

            for (line in 0..6) {
                val yPos = (line * h / 6f + scrollOffset * h / 6f) % h
                drawLine(
                    color = Color.White.copy(0.05f),
                    start = Offset(0f, yPos),
                    end = Offset(w, yPos),
                    strokeWidth = 1f
                )
            }

            val maxObsSize = laneWidth * 0.5f
            val approachDur = 2500f
            val passDur = 700f
            for (obs in level.obstacles) {
                val timeDiff = obs.timeMs - elapsedMs
                if (timeDiff > approachDur || timeDiff < -passDur) continue

                val obsX = obs.lane * laneWidth + laneWidth / 2f
                val baseObsSize = laneWidth * 0.4f

                val approachRatio: Float
                val obsY: Float
                if (timeDiff >= 0) {
                    approachRatio = 1f - timeDiff / approachDur
                    obsY = approachRatio * (playerY + 100f) - 100f
                } else {
                    approachRatio = 1f
                    val passRatio = (-timeDiff / passDur).coerceIn(0f, 1f)
                    obsY = playerY + passRatio * (h - playerY + 200f)
                }

                val obsScale = 0.5f + approachRatio * 0.5f
                val finalSize = (baseObsSize * obsScale).coerceAtMost(maxObsSize)

                val isImminent = timeDiff in 0..400 && obs.lane == currentLane
                val baseColor = if (isImminent) Color(0xFFFF4E6E) else Color(0xFFE85070)
                val pulseSize = if (isImminent) 1f + sin(wave * 2f) * 0.1f else 1f
                val drawSize = (finalSize * pulseSize).coerceAtMost(maxObsSize)

                drawCircle(
                    color = baseColor.copy(0.25f),
                    radius = drawSize * 0.8f,
                    center = Offset(obsX, obsY)
                )

                drawRoundRect(
                    color = baseColor,
                    topLeft = Offset(obsX - drawSize / 2, obsY - drawSize / 2),
                    size = Size(drawSize, drawSize),
                    cornerRadius = CornerRadius(drawSize * 0.2f)
                )

                drawRoundRect(
                    color = Color.White.copy(0.3f),
                    topLeft = Offset(obsX - drawSize / 2, obsY - drawSize / 2),
                    size = Size(drawSize, drawSize * 0.15f),
                    cornerRadius = CornerRadius(drawSize * 0.2f)
                )

                val spikeOffset = drawSize * 0.25f
                drawLine(
                    color = Color.White.copy(0.5f),
                    start = Offset(obsX - spikeOffset, obsY - spikeOffset),
                    end = Offset(obsX + spikeOffset, obsY + spikeOffset),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.White.copy(0.5f),
                    start = Offset(obsX + spikeOffset, obsY - spikeOffset),
                    end = Offset(obsX - spikeOffset, obsY + spikeOffset),
                    strokeWidth = 3f
                )
            }

            val playerX = laneFloat * laneWidth + laneWidth / 2f
            val playerSize = laneWidth * 0.22f * pulse

            drawCircle(
                color = Color(0xFF00E5A0).copy(0.18f),
                radius = playerSize * 1.7f,
                center = Offset(playerX, playerY)
            )
            drawCircle(
                color = Color(0xFF00E5A0).copy(0.35f),
                radius = playerSize * 1.25f,
                center = Offset(playerX, playerY)
            )

            val triPath = Path().apply {
                moveTo(playerX, playerY - playerSize)
                lineTo(playerX + playerSize * 0.85f, playerY + playerSize * 0.7f)
                lineTo(playerX, playerY + playerSize * 0.4f)
                lineTo(playerX - playerSize * 0.85f, playerY + playerSize * 0.7f)
                close()
            }
            drawPath(triPath, Color(0xFF00E5A0))

            val innerTriPath = Path().apply {
                moveTo(playerX, playerY - playerSize * 0.5f)
                lineTo(playerX + playerSize * 0.4f, playerY + playerSize * 0.3f)
                lineTo(playerX - playerSize * 0.4f, playerY + playerSize * 0.3f)
                close()
            }
            drawPath(innerTriPath, Color.White)

            drawLine(
                color = Color(0xFF00E5A0).copy(0.5f),
                start = Offset(0f, playerY - playerSize - 4f),
                end = Offset(w, playerY - playerSize - 4f),
                strokeWidth = 1.5f
            )
            drawLine(
                color = Color(0xFF00E5A0).copy(0.2f),
                start = Offset(0f, playerY - playerSize - 10f),
                end = Offset(w, playerY - playerSize - 10f),
                strokeWidth = 1f
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.0f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(Modifier.weight(1f))
            Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MenuScreen(onPlay: () -> Unit, onStats: () -> Unit, onSettings: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "menu")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0E1A), Color(0xFF151B2E), Color(0xFF1A1F35))))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .size(46.dp)
                .shadow(8.dp, CircleShape, spotColor = Color(0xFF6C4BFF))
                .clip(CircleShape)
                .background(Color(0xFF1E2640))
                .border(1.dp, Color(0xFF2E3650), CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) {
            SettingsIcon(size = 22.dp, color = Color.White)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulse)
                    .background(Brush.radialGradient(listOf(Color(0xFF6C4BFF), Color(0xFF3A1F9E), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("FF", fontSize = 46.sp, fontWeight = FontWeight.Black, color = Color.White)
            }

            Spacer(Modifier.height(28.dp))
            Text("FLIP FATE", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 4.sp)
            Spacer(Modifier.height(12.dp))
            Text("Three lanes of destiny", fontSize = 16.sp, color = Color(0xFFB0B8D4))

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Color(0xFF6C4BFF), Color(0xFF00E5A0), Color(0xFFFF6B9D)).forEach {
                    Box(Modifier.size(8.dp).background(it, CircleShape))
                }
            }

            Spacer(Modifier.height(64.dp))
            GlowButton("PLAY", listOf(Color(0xFF6C4BFF), Color(0xFF9D6FFF)), onPlay, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            GlowButton("STATISTICS", listOf(Color(0xFF2A3654), Color(0xFF3D4A6B)), onStats, Modifier.fillMaxWidth())

            Spacer(Modifier.height(80.dp))
            Text("Tap left or right to dodge", fontSize = 11.sp, color = Color(0xFF5A6380), letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun SettingsIcon(size: androidx.compose.ui.unit.Dp, color: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val outerR = this.size.minDimension * 0.42f
        val innerR = this.size.minDimension * 0.18f
        val toothCount = 8
        val toothLen = this.size.minDimension * 0.12f

        for (i in 0 until toothCount) {
            val angle = i * (6.2832f / toothCount)
            val tx = cx + kotlin.math.cos(angle) * (outerR + toothLen / 2f)
            val ty = cy + kotlin.math.sin(angle) * (outerR + toothLen / 2f)
            drawCircle(
                color = color,
                radius = toothLen,
                center = Offset(tx, ty)
            )
        }
        drawCircle(color = color, radius = outerR, center = Offset(cx, cy))
        drawCircle(color = Color(0xFF1E2640), radius = innerR, center = Offset(cx, cy))
    }
}

@Composable
private fun SettingsScreen(
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    onHowToPlay: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080C16), Color(0xFF101624), Color(0xFF161D30))))
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1E2640)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("<", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(16.dp))
            Text("SETTINGS", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("Game preferences", fontSize = 14.sp, color = Color(0xFF7A859E))
        Spacer(Modifier.height(28.dp))

        SettingsToggleTile(
            icon = "~",
            iconColor = Color(0xFFFF9F43),
            title = "Vibration",
            subtitle = if (vibrationEnabled) "Haptic feedback enabled" else "Haptic feedback disabled",
            checked = vibrationEnabled,
            onCheckedChange = onVibrationChange
        )

        Spacer(Modifier.height(14.dp))

        SettingsTile(
            icon = "?",
            iconColor = Color(0xFF00E5A0),
            title = "How to Play",
            subtitle = "Quick gameplay guide",
            onClick = onHowToPlay
        )
    }
}

@Composable
private fun SettingsTile(
    icon: String,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1520))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconColor.copy(0.15f), CircleShape)
                    .border(1.dp, iconColor.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = iconColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF6A7590))
            }
            if (onClick != null) {
                Text(">", color = Color(0xFF5A6380), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsToggleTile(
    icon: String,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1520))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconColor.copy(0.15f), CircleShape)
                    .border(1.dp, iconColor.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = iconColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF6A7590))
            }
            ToggleSwitch(checked = checked, activeColor = iconColor, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ToggleSwitch(
    checked: Boolean,
    activeColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) activeColor.copy(0.7f) else Color(0xFF2A3450),
        animationSpec = tween(200),
        label = "track"
    )
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(200),
        label = "thumb"
    )
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .padding(start = (3 + thumbOffset * 22).dp, top = 3.dp)
                .size(22.dp)
                .background(Color.White, CircleShape)
                .shadow(2.dp, CircleShape)
        )
    }
}

@Composable
private fun HowToPlayScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080C16), Color(0xFF101624), Color(0xFF161D30))))
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1E2640)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("<", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(16.dp))
            Text("HOW TO PLAY", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("Master the three lanes", fontSize = 14.sp, color = Color(0xFF7A859E))
        Spacer(Modifier.height(28.dp))

        HowToStep(
            number = "1",
            color = Color(0xFF6C4BFF),
            title = "Auto-runner",
            text = "Your runner moves forward automatically. You only control the lane."
        )
        Spacer(Modifier.height(14.dp))
        HowToStep(
            number = "2",
            color = Color(0xFF00E5A0),
            title = "Tap left side",
            text = "Tap the left half of the screen to dodge to the lane on your left."
        )
        Spacer(Modifier.height(14.dp))
        HowToStep(
            number = "3",
            color = Color(0xFFFF6B9D),
            title = "Tap right side",
            text = "Tap the right half of the screen to dodge to the lane on your right."
        )
        Spacer(Modifier.height(14.dp))
        HowToStep(
            number = "4",
            color = Color(0xFFE29B42),
            title = "Avoid the spikes",
            text = "Red blocks fall from the top in one of three lanes. Switch lanes to avoid them."
        )
        Spacer(Modifier.height(14.dp))
        HowToStep(
            number = "5",
            color = Color(0xFF4A9FD4),
            title = "Survive to win",
            text = "Reach the end of the level without getting hit. Each level grows faster and denser."
        )

        Spacer(Modifier.height(28.dp))
        GlowButton("GOT IT", listOf(Color(0xFF6C4BFF), Color(0xFF9D6FFF)), onBack, Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HowToStep(number: String, color: Color, title: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1520))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(0.15f), CircleShape)
                    .border(1.dp, color.copy(0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(number, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(text, fontSize = 13.sp, color = Color(0xFFB0B8D4), lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun GlowButton(text: String, gradient: List<Color>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(56.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp), spotColor = gradient.first().copy(0.4f))
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(gradient))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
    }
}

@Composable
private fun LevelSelectScreen(
    levels: List<LevelDef>,
    stats: Map<Int, LevelStats>,
    onSelect: (LevelDef) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080C16), Color(0xFF101624), Color(0xFF161D30))))
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1E2640)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("<", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(16.dp))
            Text("SELECT LEVEL", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("Choose your challenge", fontSize = 14.sp, color = Color(0xFF7A859E))
        Spacer(Modifier.height(24.dp))

        levels.forEach { level ->
            val st = stats[level.id] ?: LevelStats()
            LevelCard(level, st, { onSelect(level) })
            Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LevelCard(level: LevelDef, stats: LevelStats, onClick: () -> Unit) {
    val completed = stats.wins > 0
    val successRate = if (stats.attempts > 0) stats.wins * 100 / stats.attempts else 0
    Card(
        modifier = Modifier.fillMaxWidth().height(150.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = level.colorLight.copy(0.3f))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(level.colorDark, level.colorLight)))) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.5f)))))
            if (completed) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(32.dp).background(Color(0xFF00E5A0), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("\u2713", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(level.subtitle, fontSize = 12.sp, color = Color.White.copy(0.7f), letterSpacing = 2.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(level.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(level.theme, fontSize = 12.sp, color = Color.White.copy(0.8f))
                    if (stats.attempts > 0) {
                        Box(Modifier.background(Color.Black.copy(0.4f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("$successRate% success", fontSize = 12.sp, color = Color(0xFF00E5A0), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultScreen(
    level: LevelDef,
    won: Boolean,
    moves: Int,
    stats: LevelStats,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
    onNext: () -> Unit
) {
    val accent = if (won) Color(0xFF00E5A0) else Color(0xFFFF6B9D)
    val bg = if (won) listOf(Color(0xFF0A1A15), Color(0xFF0F2A1F)) else listOf(Color(0xFF1A0A0F), Color(0xFF2A0F15))
    val successRate = if (stats.attempts > 0) stats.wins * 100 / stats.attempts else 0

    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(bg)).padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(50.dp))
        Box(Modifier.size(100.dp).background(accent.copy(0.15f), CircleShape).border(3.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (won) "\u2713" else "\u2717", fontSize = 48.sp, color = accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text(if (won) "LEVEL COMPLETE" else "RUN FAILED", fontSize = 26.sp, fontWeight = FontWeight.Black, color = accent, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))
        Text(level.title, fontSize = 16.sp, color = Color(0xFF8A94B0))
        Spacer(Modifier.height(32.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1520))) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("RUN STATS", fontSize = 11.sp, color = Color(0xFF5A6380), letterSpacing = 2.sp)
                ResultRow("Moves this run", "$moves", Color(0xFF6C4BFF))
                ResultRow("Attempts", "${stats.attempts}")
                ResultRow("Wins", "${stats.wins}", Color(0xFF00E5A0))
                ResultRow("Success rate", "$successRate%", Color(0xFF00E5A0))
                ResultRow("Total moves", "${stats.totalMoves}")
            }
        }

        Spacer(Modifier.height(32.dp))
        GlowButton("RETRY", listOf(accent.copy(0.8f), accent), onRetry, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        if (won) {
            GlowButton("NEXT LEVEL", listOf(Color(0xFF6C4BFF), Color(0xFF9D6FFF)), onNext, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        GlowButton("LEVELS", listOf(Color(0xFF2A3654), Color(0xFF3D4A6B)), onMenu, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: Color = Color.White) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF6A7590), fontSize = 14.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatsScreen(levels: List<LevelDef>, stats: Map<Int, LevelStats>, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080C16), Color(0xFF101624))))
            .padding(20.dp).verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1E2640)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("<", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Text("STATISTICS", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(24.dp))

        val total = stats.values.sumOf { it.attempts }
        val wins = stats.values.sumOf { it.wins }
        val moves = stats.values.sumOf { it.totalMoves }
        val successRate = if (total > 0) wins * 100 / total else 0

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1520))) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBlock("Attempts", "$total")
                    StatBlock("Wins", "$wins", Color(0xFF00E5A0))
                    StatBlock("Moves", "$moves", Color(0xFF6C4BFF))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.06f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("$successRate%", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E5A0))
                    Text("SUCCESS RATE", fontSize = 10.sp, color = Color(0xFF6A7590), letterSpacing = 2.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("BY LEVEL", fontSize = 12.sp, color = Color(0xFF5A6380), letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))

        levels.forEach { level ->
            val st = stats[level.id] ?: LevelStats()
            StatsLevelCard(level, st)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = Color(0xFF6A7590))
    }
}

@Composable
private fun StatsLevelCard(level: LevelDef, stats: LevelStats) {
    val successRate = if (stats.attempts > 0) stats.wins * 100 / stats.attempts else 0
    val rateColor = when {
        stats.attempts == 0 -> Color(0xFF5A6380)
        successRate >= 70 -> Color(0xFF00E5A0)
        successRate >= 40 -> Color(0xFFFF9F43)
        else -> Color(0xFFFF6B9D)
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1520))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(level.colorDark, level.colorLight))), contentAlignment = Alignment.Center) {
                    Text("${level.id}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(level.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(level.theme, fontSize = 11.sp, color = Color(0xFF6A7590))
                }
                Text(if (stats.attempts > 0) "$successRate%" else "\u2014", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = rateColor)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatPill(label = "Attempts", value = "${stats.attempts}")
                StatPill(label = "Wins", value = "${stats.wins}", color = Color(0xFF00E5A0))
                StatPill(label = "Moves", value = "${stats.totalMoves}", color = Color(0xFF6C4BFF))
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = Color(0xFF6A7590), letterSpacing = 1.sp)
    }
}

private fun buildLevels(): List<LevelDef> {
    return listOf(
        LevelDef(
            id = 1, title = "Emerald Corridor", subtitle = "LEVEL 1",
            theme = "Three lane warm-up", duration = 12000L,
            colorLight = Color(0xFF1F5B4A), colorDark = Color(0xFF0E2030),
            obstacles = listOf(
                Obstacle(2000, 0), Obstacle(3500, 2), Obstacle(5000, 1),
                Obstacle(6500, 0), Obstacle(8000, 2), Obstacle(9500, 1), Obstacle(11000, 0)
            )
        ),
        LevelDef(
            id = 2, title = "Neon Archive", subtitle = "LEVEL 2",
            theme = "Faster reactions", duration = 14000L,
            colorLight = Color(0xFF7A3FFF), colorDark = Color(0xFF120B2D),
            obstacles = listOf(
                Obstacle(1500, 1), Obstacle(2700, 0), Obstacle(3900, 2),
                Obstacle(5000, 1), Obstacle(6000, 0), Obstacle(7000, 2),
                Obstacle(8000, 1), Obstacle(9000, 0), Obstacle(10000, 2),
                Obstacle(11000, 1), Obstacle(12000, 0), Obstacle(13000, 2)
            )
        ),
        LevelDef(
            id = 3, title = "Sunken Sandline", subtitle = "LEVEL 3",
            theme = "Pattern shifts", duration = 16000L,
            colorLight = Color(0xFFE29B42), colorDark = Color(0xFF2E1C10),
            obstacles = listOf(
                Obstacle(1200, 0), Obstacle(2000, 1), Obstacle(2800, 2),
                Obstacle(3700, 0), Obstacle(4500, 1), Obstacle(5300, 2),
                Obstacle(6200, 1), Obstacle(7000, 2), Obstacle(7800, 0),
                Obstacle(8700, 1), Obstacle(9500, 0), Obstacle(10300, 2),
                Obstacle(11200, 1), Obstacle(12000, 2), Obstacle(12800, 0),
                Obstacle(13700, 1), Obstacle(14500, 2), Obstacle(15300, 0)
            )
        ),
        LevelDef(
            id = 4, title = "Frozen Echo", subtitle = "LEVEL 4",
            theme = "Quick swaps", duration = 18000L,
            colorLight = Color(0xFF4A9FD4), colorDark = Color(0xFF0A1525),
            obstacles = listOf(
                Obstacle(1000, 1), Obstacle(1700, 0), Obstacle(2400, 2),
                Obstacle(3000, 1), Obstacle(3700, 0), Obstacle(4400, 1),
                Obstacle(5100, 2), Obstacle(5800, 0), Obstacle(6500, 1),
                Obstacle(7200, 2), Obstacle(7900, 1), Obstacle(8600, 0),
                Obstacle(9300, 2), Obstacle(10000, 1), Obstacle(10700, 0),
                Obstacle(11400, 2), Obstacle(12100, 1), Obstacle(12800, 0),
                Obstacle(13500, 2), Obstacle(14200, 1), Obstacle(14900, 0),
                Obstacle(15600, 2), Obstacle(16300, 1), Obstacle(17000, 0)
            )
        ),
        LevelDef(
            id = 5, title = "Crimson Void", subtitle = "LEVEL 5",
            theme = "Ultimate challenge", duration = 20000L,
            colorLight = Color(0xFFD43A3A), colorDark = Color(0xFF1A0808),
            obstacles = listOf(
                Obstacle(900, 1), Obstacle(1500, 0), Obstacle(2100, 2),
                Obstacle(2700, 1), Obstacle(3300, 2), Obstacle(3900, 0),
                Obstacle(4500, 1), Obstacle(5100, 2), Obstacle(5700, 0),
                Obstacle(6300, 1), Obstacle(6900, 2), Obstacle(7500, 1),
                Obstacle(8100, 0), Obstacle(8700, 2), Obstacle(9300, 1),
                Obstacle(9900, 0), Obstacle(10500, 2), Obstacle(11100, 1),
                Obstacle(11700, 0), Obstacle(12300, 2), Obstacle(12900, 1),
                Obstacle(13500, 0), Obstacle(14100, 1), Obstacle(14700, 2),
                Obstacle(15300, 1), Obstacle(15900, 0), Obstacle(16500, 2),
                Obstacle(17100, 1), Obstacle(17700, 0), Obstacle(18300, 2),
                Obstacle(18900, 1), Obstacle(19500, 0)
            )
        )
    )
}

private const val PREFS_NAME = "flip_fate_prefs"
private const val KEY_VIBRATION = "vibration_enabled"

private fun loadVibrationEnabled(context: Context): Boolean {
    return context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_VIBRATION, true)
}

private fun saveVibrationEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_VIBRATION, enabled)
        .apply()
}

private fun systemVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

private fun vibrate(context: Context, durationMs: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
    val vibrator = systemVibrator(context) ?: return
    if (!vibrator.hasVibrator()) return
    val effect = VibrationEffect.createOneShot(durationMs, amplitude)
    vibrator.vibrate(effect)
}
