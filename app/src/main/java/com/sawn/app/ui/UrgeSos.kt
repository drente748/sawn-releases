package com.sawn.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sawn.app.R
import kotlinx.coroutines.delay

/** مرحلة التنفّس 4 – 7 – 8. أرقامٌ قصيرة يكفي أن تتبعها العين. */
private enum class BreathPhase(val seconds: Int) {
    INHALE(4), HOLD(7), EXHALE(8);

    fun next(): BreathPhase = when (this) {
        INHALE -> HOLD
        HOLD -> EXHALE
        EXHALE -> INHALE
    }
}

private const val SOS_TOTAL_SECONDS = 60
private const val URGE_DELAY_SECONDS = 10 * 60
private enum class UrgeTool { BREATHE, GROUND, DELAY }

/**
 * «درع الرغبة»: دقيقة تنفّسٍ موجّهة تُفتح عند ذروة الرغبة.
 *
 * لا شيء يُخزَّن ولا يُرسَل؛ الشاشة كلها في الذاكرة وتُغلق مع التطبيق. الهدف هو كسر
 * حلقة «الفتح التلقائي» بدقيقةٍ واحدةٍ محسوبة، لا منع المستخدم من أي شيء.
 */
@Composable
fun UrgeSosScreen(
    onSessionCompleted: () -> Unit = {},
    onBack: () -> Unit,
) {
    var tool by remember { mutableStateOf(UrgeTool.BREATHE) }
    var round by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(SOS_TOTAL_SECONDS) }
    var phase by remember { mutableStateOf(BreathPhase.INHALE) }
    var phaseLeft by remember { mutableIntStateOf(BreathPhase.INHALE.seconds) }
    var finished by remember { mutableStateOf(false) }
    var groundingStep by remember { mutableIntStateOf(0) }
    var delayEndAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableIntStateOf(0) }
    var delayStarted by remember { mutableStateOf(false) }
    val delayRemaining = (URGE_DELAY_SECONDS - elapsed).coerceAtLeast(0)
    val completedTools = remember { mutableStateMapOf<UrgeTool, Boolean>() }

    LaunchedEffect(tool, round) {
        finished = completedTools[tool] == true
        if (tool == UrgeTool.BREATHE && !finished) {
            remaining = SOS_TOTAL_SECONDS
            phase = BreathPhase.INHALE
            phaseLeft = BreathPhase.INHALE.seconds
            finished = false
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                phaseLeft -= 1
                if (phaseLeft <= 0) {
                    phase = phase.next()
                    phaseLeft = phase.seconds
                }
            }
            finished = true
            if (completedTools.put(UrgeTool.BREATHE, true) == null) onSessionCompleted()
        }
    }

    LaunchedEffect(tool, delayStarted) {
        if (tool != UrgeTool.DELAY || !delayStarted) return@LaunchedEffect
        if (delayEndAt == 0L) delayEndAt = System.currentTimeMillis() + URGE_DELAY_SECONDS * 1_000L
        while (elapsed < URGE_DELAY_SECONDS) {
            delay(1_000L)
            elapsed = ((System.currentTimeMillis() - (delayEndAt - URGE_DELAY_SECONDS * 1_000L)) / 1_000L)
                .toInt().coerceIn(0, URGE_DELAY_SECONDS)
        }
        finished = true
        if (completedTools.put(UrgeTool.DELAY, true) == null) onSessionCompleted()
    }

    val target = when {
        finished -> 1f
        phase == BreathPhase.EXHALE -> 0.78f
        else -> 1.16f
    }
    val breath by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = if (finished) 600 else phase.seconds * 1000,
            easing = FastOutSlowInEasing,
        ),
        label = "sosBreath",
    )

    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val background = MaterialTheme.colorScheme.background
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(primaryContainer, background)),
            ),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        UrgeTool.BREATHE to R.string.sos_tool_breathe,
                        UrgeTool.GROUND to R.string.sos_tool_ground,
                        UrgeTool.DELAY to R.string.sos_tool_delay,
                    ).forEach { (choice, title) ->
                        val selected = tool == choice
                        Surface(
                            onClick = {
                            tool = choice
                            finished = completedTools[choice] == true
                        },
                            shape = CircleShape,
                            color = if (selected) primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        ) {
                            Text(
                                stringResource(title),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (tool == UrgeTool.BREATHE && !finished) {
                    Text(
                        stringResource(R.string.sos_intro),
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(
                                color = primary.copy(alpha = 0.10f),
                                radius = size.minDimension / 2f,
                            )
                        }
                        Canvas(Modifier.fillMaxSize().scale(breath)) {
                            drawCircle(
                                color = primary.copy(alpha = 0.20f),
                                radius = size.minDimension / 2f,
                            )
                            drawCircle(
                                color = primary,
                                radius = size.minDimension / 2f - 8.dp.toPx(),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                when (phase) {
                                    BreathPhase.INHALE -> stringResource(R.string.sos_inhale)
                                    BreathPhase.HOLD -> stringResource(R.string.sos_hold)
                                    BreathPhase.EXHALE -> stringResource(R.string.sos_exhale)
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.sos_counter, remaining),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.sos_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.sos_close))
                    }
                } else if (tool == UrgeTool.GROUND) {
                    Icon(
                        Icons.Filled.SelfImprovement,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        stringResource(R.string.sos_ground_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.sos_ground_step, groundingStep + 1),
                        style = MaterialTheme.typography.titleLarge,
                        color = primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(listOf(R.string.sos_ground_see, R.string.sos_ground_touch, R.string.sos_ground_hear)[groundingStep]),
                        style = MaterialTheme.typography.bodyLarge,
                        color = onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { groundingStep = (groundingStep - 1).coerceAtLeast(0) }, enabled = groundingStep > 0) {
                            Text(stringResource(R.string.sos_ground_previous))
                        }
                        Button(onClick = {
                            if (groundingStep < 2) groundingStep += 1 else {
                                finished = true
                                if (completedTools.put(UrgeTool.GROUND, true) == null) onSessionCompleted()
                            }
                        }) {
                            Text(stringResource(if (groundingStep < 2) R.string.sos_ground_next else R.string.sos_ground_done))
                        }
                    }
                    if (finished) {
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.sos_done_title), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onBack) { Text(stringResource(R.string.sos_close)) }
                    }
                } else if (tool == UrgeTool.DELAY && !finished) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = primary, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.sos_delay_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.sos_delay_body), style = MaterialTheme.typography.bodyLarge, color = onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(22.dp))
                    Text(stringResource(R.string.sos_delay_counter, delayRemaining / 60, delayRemaining % 60), style = MaterialTheme.typography.displaySmall, color = primary)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = {
                        if (!delayStarted) {
                            delayEndAt = System.currentTimeMillis() + URGE_DELAY_SECONDS * 1_000L
                            elapsed = 0
                            delayStarted = true
                        }
                    }, enabled = !delayStarted) {
                        Text(stringResource(if (delayStarted) R.string.sos_delay_running else R.string.sos_delay_start))
                    }
                    TextButton(onClick = onBack) { Text(stringResource(R.string.sos_close)) }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = primary,
                        modifier = Modifier.size(96.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(44.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        stringResource(R.string.sos_done_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.sos_done_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(26.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = {
                            completedTools.clear()
                            groundingStep = 0
                            delayStarted = false
                            delayEndAt = 0L
                            elapsed = 0
                            finished = false
                            round += 1
                        }) {
                            Text(stringResource(R.string.sos_again))
                        }
                        Button(onClick = onBack) {
                            Text(stringResource(R.string.sos_close))
                        }
                    }
                }
            }
        }
}
