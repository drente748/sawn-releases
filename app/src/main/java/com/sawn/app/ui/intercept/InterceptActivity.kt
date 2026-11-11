package com.sawn.app.ui.intercept

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sawn.app.R
import com.sawn.domain.block.LimitReason
import com.sawn.core.data.BlockedItem
import com.sawn.app.ui.theme.SawnTheme
import com.sawn.app.sawnContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shown over a blocked app. Renders one of three experiences depending on the decision:
 * a hard block, a limit-reached notice, or the mindful pause that can grant timed access.
 */
class InterceptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Anti-tapjacking: a malicious overlay must not be able to trick taps on "Continue"
        // (which grants access) or dismiss the block.
        window.decorView.filterTouchesWhenObscured = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        // Back must send the user home; letting it fall through would resume the blocked app
        // waiting behind this screen.
        onBackPressedDispatcher.addCallback(this) { goHome() }
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_BLOCK
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        val reason = intent.getStringExtra(EXTRA_REASON)?.let { runCatching { LimitReason.valueOf(it) }.getOrNull() }
        val escalations = intent.getIntExtra(EXTRA_ESCALATIONS, 0)
        val adultHost = intent.getStringExtra(EXTRA_HOST)
        val contentLabel = intent.getStringExtra(EXTRA_FEATURE_LABEL)

        // عدّاد محلي فقط: كم محاولة رفضها صَوْن (يظهر في بطاقة حصيلة اليوم).
        lifecycleScope.launch { sawnContainer.settings.recordBlockEvent() }

        setContent {
            SawnTheme {
                var item by remember { mutableStateOf<BlockedItem?>(null) }
                var loadDone by remember { mutableStateOf(false) }
                LaunchedEffect(itemId) {
                    item = sawnContainer.repository.item(itemId)
                    // settled: found, or definitively absent (deleted while active)
                    loadDone = true
                }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val current = item
                    val needsItem = kind == KIND_PAUSE || kind == KIND_LIMIT
                    val itemLoaded = !needsItem || (current != null || loadDone)
                    when {
                        itemLoaded && kind == KIND_ADULT -> AdultBlockScreen(onHome = ::goHome)
                        itemLoaded && kind == KIND_SOCIAL -> SocialBlockScreen(platformLabel = contentLabel, onHome = ::goHome)
                        itemLoaded && kind == KIND_FEATURE -> FeatureBlockScreen(featureLabel = contentLabel, onHome = ::goHome)
                        itemLoaded && kind == KIND_PAUSE && current != null -> PauseScreen(
                            item = current,
                            escalations = escalations,
                            onContinue = { minutes -> grantAndEnter(current, minutes) },
                            onGiveUp = { recordDefeatAndGoHome() },
                        )
                        itemLoaded && kind == KIND_LIMIT && current != null -> LimitScreen(current, reason, ::goHome)
                        itemLoaded && kind == KIND_DEEP_FOCUS -> DeepFocusBlockScreen(onHome = ::goHome)
                        !itemLoaded -> {
                            // Pause/Limit while the row is still resolving: a neutral
                            // spinner instead of one (or many) frames of the wrong
                            // hard-block wall — and Hold-to-Leave is never bound to
                            // onHome in that window, so a defeat cannot be lost.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        else -> HardBlockScreen(current, ::goHome)
                    }
                }
            }
        }
    }

    private fun grantAndEnter(item: BlockedItem, minutes: Int) {
        lifecycleScope.launch {
            sawnContainer.repository.grantAccess(item, minutes)
            finish() // return to the app that is waiting behind us; the grant lets it through
        }
    }

    /** Exiting an active pause counts as a defeat — next attempt waits longer. */
    private fun recordDefeatAndGoHome() {
        lifecycleScope.launch {
            sawnContainer.settings.recordPauseDefeat(sawnContainer.settings.trustedNow())
            goHome()
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_REASON = "reason"
        const val EXTRA_ESCALATIONS = "escalations"
        const val EXTRA_FEATURE_LABEL = "feature_label"
        const val EXTRA_HOST = "host"
        const val KIND_PAUSE = "pause"
        const val KIND_BLOCK = "block"
        const val KIND_LIMIT = "limit"
        const val KIND_FEATURE = "feature"
        const val KIND_ADULT = "adult"
        const val KIND_SOCIAL = "social"
        const val KIND_DEEP_FOCUS = "deep_focus"
    }
}

@Composable
private fun SocialBlockScreen(platformLabel: String?, onHome: () -> Unit) {
    val platform = platformLabel ?: stringResource(R.string.intercept_this_app)
    CenteredHoldToLeave(
        title = stringResource(R.string.intercept_social_title),
        body = stringResource(R.string.intercept_social_body, platform),
        onHome = onHome,
    )
}

@Composable
private fun FeatureBlockScreen(featureLabel: String?, onHome: () -> Unit) {
    val feed = featureLabel ?: stringResource(R.string.intercept_this_feed)
    CenteredHoldToLeave(
        title = stringResource(R.string.intercept_feature_title, feed),
        body = stringResource(R.string.intercept_feature_body, feed),
        onHome = onHome,
    )
}

@Composable
private fun AdultBlockScreen(onHome: () -> Unit) {
    CenteredHoldToLeave(
        title = stringResource(R.string.intercept_adult_title),
        body = stringResource(R.string.intercept_adult_body),
        onHome = onHome,
    )
}

/**
 * Hold-to-leave (Flint-style): the exit button needs a deliberate 1.2s press — no leaving
 * by accident, no muscle-memory taps. The ring around the button fills as you hold.
 */
@Composable
private fun CenteredHoldToLeave(title: String, body: String, onHome: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Shield,
            contentDescription = stringResource(R.string.intercept_shield),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        HoldToLeaveButton(onDone = onHome)
    }
}

private const val HOLD_MS = 1200

/**
 * Deliberate exit (Flint-style hold-to-leave): the user must hold the center button for
 * ~1.2 s while a ring fills; releasing early cancels. Uses detectTapGestures' onPress
 * suspension (clean down→release lifecycle) instead of raw pointer plumbing.
 */
@Composable
private fun HoldToLeaveButton(onDone: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }
        val startedAt = SystemClock.elapsedRealtime()
        while (holding) {
            progress = ((SystemClock.elapsedRealtime() - startedAt) / HOLD_MS.toFloat()).coerceIn(0f, 1f)
            delay(40L)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(84.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(64.dp)
                    .pointerInput(Unit) {
                        // awaitEachGesture: نافذة واضحة من الضغط إلى الإفلات.
                        // إلغاء أو رفع مبكر = لا خروج؛ القصد وحده يُحتسب.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            holding = true
                            val start = SystemClock.elapsedRealtime()
                            val up = waitForUpOrCancellation()
                            val held = SystemClock.elapsedRealtime() - start
                            holding = false
                            if (up != null && held >= HOLD_MS) onDone()
                        }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.intercept_hold_to_leave),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PauseScreen(item: BlockedItem, escalations: Int, onContinue: (Int) -> Unit, onGiveUp: () -> Unit) {
    // Escalating friction: each same-day exit from a pause makes the next wait longer.
    val extraWait = escalations * item.pauseSeconds.coerceAtLeast(5)
    var remaining by remember { mutableStateOf((item.pauseSeconds + extraWait).coerceAtLeast(1)) }
    LaunchedEffect(Unit) {
        while (remaining > 0) { delay(1000); remaining-- }
    }
    val breathing by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.72f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "scale",
    )

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(220.dp).scale(breathing),
            ) {}
            Text(
                if (remaining > 0) {
                    stringResource(R.string.intercept_breathe, remaining)
                } else {
                    stringResource(R.string.intercept_ready)
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            item.customPrompt?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.intercept_why_opening, item.label),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        if (remaining > 0) {
            if (escalations > 0) {
                Text(
                    stringResource(R.string.intercept_escalations, escalations),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                stringResource(R.string.intercept_pause_note),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.intercept_continue_for), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1, 5, 15).forEach { min ->
                    OutlinedButton(onClick = { onContinue(min) }) {
                        Text(stringResource(R.string.intercept_minutes, min))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        HoldToLeaveButton(onDone = onGiveUp)
    }
}

@Composable
private fun HardBlockScreen(item: BlockedItem?, onHome: () -> Unit) {
    CenteredHoldToLeave(
        title = stringResource(R.string.intercept_block_title),
        body = item?.let {
            stringResource(R.string.intercept_block_body, it.label)
        } ?: stringResource(R.string.intercept_block_generic),
        onHome = onHome,
    )
}

/**
 * جدار التركيز العميق: التطبيق خارج القائمة البيضاء، فلا مهلة ولا استثناء.
 * الفكّ يتم من داخل صَوْن (شاشة «الترميز العميق») بالرقم السري، فيوجّه النص إلى
 * هناك بدل زرٍّ بلا أثر.
 */
@Composable
private fun DeepFocusBlockScreen(onHome: () -> Unit) {
    CenteredHoldToLeave(
        title = stringResource(R.string.intercept_deep_focus_title),
        body = stringResource(R.string.intercept_deep_focus_body),
        onHome = onHome,
    )
}

@Composable
private fun LimitScreen(item: BlockedItem, reason: LimitReason?, onHome: () -> Unit) {
    val body = when (reason) {
        LimitReason.OPENS -> stringResource(R.string.intercept_limit_opens, item.label)
        LimitReason.TIME -> stringResource(R.string.intercept_limit_time, item.label)
        LimitReason.COOLDOWN -> stringResource(R.string.intercept_limit_cooldown, item.label)
        null -> stringResource(R.string.intercept_limit_generic, item.label)
    }
    CenteredHoldToLeave(
        title = stringResource(R.string.intercept_limit_title),
        body = body,
        onHome = onHome,
    )
}
