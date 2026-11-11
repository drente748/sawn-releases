package com.sawn.app.ui.onboarding.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sawn.app.R
import com.sawn.app.ui.FeatureRow
import com.sawn.app.ui.PillCTA
import com.sawn.app.ui.SawnNumbers
import com.sawn.app.ui.dawnBackground
import com.sawn.app.ui.theme.SawnPalette
import com.sawn.domain.OnboardingAnswers
import com.sawn.domain.PlanBuilder
import com.sawn.domain.PlanFeature
import kotlinx.coroutines.delay

private data class RevealFeature(
    val icon: ImageVector,
    val iconColor: Color,
    val titleRes: Int,
    val subtitleRes: Int,
)

/** «خطتك الشخصية ✦» — قائمة ميزات تُكشف بتعاقبٍ ثم زر التفعيل. */
@Composable
fun PlanRevealStep(
    answers: OnboardingAnswers,
    reduceMotion: Boolean,
    onActivate: () -> Unit,
    onExplore: () -> Unit,
) {
    val plan = remember(answers) { PlanBuilder.build(answers) }
    var revealed by remember { mutableStateOf(0) }

    LaunchedEffect(plan) {
        if (reduceMotion) {
            revealed = plan.featureIds.size
        } else {
            for (i in 1..plan.featureIds.size) {
                delay(135)
                revealed = i
            }
        }
    }

    val counterProgress by animateFloatAsState(
        if (plan.featureIds.isEmpty()) 0f else revealed.toFloat() / plan.featureIds.size.toFloat(),
        label = "plan_counter",
    )

    Column(
        Modifier
            .fillMaxSize()
            .dawnBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ===== العنوان =====
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.onboarding_plan_kicker),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = SawnPalette.PrimaryDeep,
            )
            Text(
                stringResource(R.string.onboarding_plan_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = SawnPalette.Ink,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.onboarding_plan_sub),
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
                textAlign = TextAlign.Center,
            )
        }

        // ===== الميزات بالتعاقب =====
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            plan.featureIds.forEachIndexed { index, id ->
                val meta = featureMeta(id)
                AnimatedVisibility(
                    visible = index < revealed,
                    enter = fadeIn() + slideInVertically { it / 2 },
                ) {
                    FeatureRow(
                        icon = meta.icon,
                        iconBackground = meta.iconColor,
                        title = stringResource(meta.titleRes),
                        subtitle = stringResource(meta.subtitleRes),
                    )
                }
            }
            Text(
                stringResource(R.string.onboarding_plan_note),
                style = MaterialTheme.typography.labelSmall,
                color = SawnPalette.Muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // ===== العداد + الأزرار =====
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = SawnPalette.Card,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SawnPalette.Line, MaterialTheme.shapes.large),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.onboarding_plan_counter, revealed, plan.featureIds.size),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = SawnPalette.Ink,
                    )
                    // عدّاد تقدّم دائري صغير
                    androidx.compose.foundation.Canvas(Modifier.height(26.dp).width(64.dp)) {
                        val width = size.width
                        val stroke = 4.dp.toPx()
                        val trackY = size.height / 2
                        drawLine(
                            SawnPalette.Track,
                            Offset(stroke, trackY),
                            Offset(width - stroke, trackY),
                            stroke,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                        drawLine(
                            SawnPalette.Primary,
                            Offset(stroke, trackY),
                            Offset(stroke + (width - 2 * stroke) * counterProgress, trackY),
                            stroke,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                    }
                }
            }
            PillCTA(text = stringResource(R.string.onboarding_plan_cta), onClick = onActivate)
            TextButton(onClick = onExplore, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.onboarding_plan_explore),
                    color = SawnPalette.InkSoft,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun featureMeta(id: String): RevealFeature = when (id) {
    PlanFeature.PORN -> RevealFeature(Icons.Filled.VisibilityOff, Color(0xFF16A34A), R.string.plan_feature_porn_title, R.string.plan_feature_porn_sub)
    PlanFeature.SAFE_SEARCH -> RevealFeature(Icons.Filled.Search, Color(0xFFEA580C), R.string.plan_feature_safesearch_title, R.string.plan_feature_safesearch_sub)
    PlanFeature.ADS -> RevealFeature(Icons.Filled.Block, Color(0xFFF59E0B), R.string.plan_feature_ads_title, R.string.plan_feature_ads_sub)
    PlanFeature.DOH -> RevealFeature(Icons.Filled.Dns, Color(0xFF2563EB), R.string.plan_feature_doh_title, R.string.plan_feature_doh_sub)
    PlanFeature.STRICT -> RevealFeature(Icons.Filled.Lock, Color(0xFF7C3AED), R.string.plan_feature_strict_title, R.string.plan_feature_strict_sub)
    PlanFeature.APPS -> RevealFeature(Icons.Filled.Groups, Color(0xFF0EA5E9), R.string.plan_feature_apps_title, R.string.plan_feature_apps_sub)
    PlanFeature.FEEDS -> RevealFeature(Icons.Filled.Groups, Color(0xFF0EA5E9), R.string.plan_feature_feeds_title, R.string.plan_feature_feeds_sub)
    PlanFeature.FOCUS -> RevealFeature(Icons.Filled.Bolt, Color(0xFFD97706), R.string.plan_feature_focus_title, R.string.plan_feature_focus_sub)
    PlanFeature.NOTIFICATIONS -> RevealFeature(Icons.Filled.NotificationsOff, Color(0xFF64748B), R.string.plan_feature_notifications_title, R.string.plan_feature_notifications_sub)
    else -> RevealFeature(Icons.Filled.Bolt, SawnPalette.Primary, R.string.plan_feature_focus_title, R.string.plan_feature_focus_sub)
}
