package com.sawn.app.ui.onboarding.steps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sawn.app.R
import com.sawn.app.ui.SawnNumbers
import com.sawn.app.ui.dawnBackground
import com.sawn.app.ui.theme.SawnPalette
import com.sawn.domain.PlanBuilder

internal data class QuestionOption(val id: String, val label: String, val icon: ImageVector)

/** هيكل موّحد للأسئلة: شريط تقدّم + زر رجوع + قائمة خيارات. */
@Composable
internal fun QuestionScaffold(
    progress: Int,
    total: Int,
    onBack: () -> Unit,
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .dawnBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = SawnPalette.PrimarySoft,
                modifier = Modifier.size(38.dp).border(1.dp, SawnPalette.Line, CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.onboarding_prev),
                    tint = SawnPalette.PrimaryDeep,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(total) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (index < progress) SawnPalette.Primary else SawnPalette.Track),
                    )
                }
            }
            Text(
                stringResource(R.string.onboarding_q_progress, progress, total),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = SawnPalette.InkSoft,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = SawnPalette.Ink,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SawnPalette.InkSoft,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { content() }
        }
    }
}

/** بطاقة خيارٍ واحد: تنضغط بنبضة Spring عند اختيارها وتظهر علامة صح. */
@Composable
internal fun SelectableCard(
    option: QuestionOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        if (selected) 1.02f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "select_scale",
    )
    val borderColor by animateColorAsState(
        if (selected) SawnPalette.Primary else SawnPalette.Line,
        label = "select_border",
    )
    val backgroundColor by animateColorAsState(
        if (selected) SawnPalette.PrimarySoft else SawnPalette.Card,
        label = "select_bg",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                option.icon,
                contentDescription = null,
                tint = if (selected) SawnPalette.PrimaryDeep else SawnPalette.InkSoft,
                modifier = Modifier.size(24.dp),
            )
            Text(
                option.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = SawnPalette.Ink,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(SawnPalette.Primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** رقائق متعددة الاختيار للسؤال الرابع. */
@Composable
internal fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        if (selected) SawnPalette.Primary else SawnPalette.Card,
        label = "chip_bg",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = backgroundColor,
        modifier = if (selected) {
            Modifier
        } else {
            Modifier.border(1.5.dp, SawnPalette.Line, CircleShape)
        },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else SawnPalette.Ink,
            )
        }
    }
}

@Composable
fun QuestionOneStep(selected: String?, onSelect: (String) -> Unit, onBack: () -> Unit) {
    QuestionScaffold(
        progress = 1,
        total = 4,
        onBack = onBack,
        title = stringResource(R.string.onboarding_q1_title),
        subtitle = stringResource(R.string.onboarding_q1_sub),
    ) {
        val options = listOf(
            QuestionOption("src_x", stringResource(R.string.onboarding_q1_x), Icons.Filled.Message),
            QuestionOption("src_instagram", stringResource(R.string.onboarding_q1_instagram), Icons.Filled.CameraAlt),
            QuestionOption("src_youtube", stringResource(R.string.onboarding_q1_youtube), Icons.Filled.OndemandVideo),
            QuestionOption("src_tiktok", stringResource(R.string.onboarding_q1_tiktok), Icons.Filled.MusicNote),
            QuestionOption("src_google", stringResource(R.string.onboarding_q1_google), Icons.Filled.Search),
            QuestionOption("src_friend", stringResource(R.string.onboarding_q1_friend), Icons.Filled.People),
            QuestionOption("src_store", stringResource(R.string.onboarding_q1_store), Icons.Filled.Storefront),
            QuestionOption("src_other", stringResource(R.string.onboarding_q1_other), Icons.Filled.MoreHoriz),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                SelectableCard(option, selected == option.id) { onSelect(option.id) }
            }
        }
    }
}

@Composable
fun QuestionTwoStep(selected: String?, onSelect: (String) -> Unit, onBack: () -> Unit) {
    QuestionScaffold(
        progress = 2,
        total = 4,
        onBack = onBack,
        title = stringResource(R.string.onboarding_q2_title),
        subtitle = stringResource(R.string.onboarding_q2_sub),
    ) {
        val options = listOf(
            QuestionOption(PlanBuilder.GOAL_PORN, stringResource(R.string.onboarding_q2_porn), Icons.Filled.VisibilityOff),
            QuestionOption(PlanBuilder.GOAL_DISTRACTION, stringResource(R.string.onboarding_q2_distraction), Icons.Filled.PhoneAndroid),
            QuestionOption(PlanBuilder.GOAL_ADS, stringResource(R.string.onboarding_q2_ads), Icons.Filled.Block),
            QuestionOption(PlanBuilder.GOAL_FOCUS, stringResource(R.string.onboarding_q2_focus), Icons.Filled.Bolt),
            QuestionOption(PlanBuilder.GOAL_FAMILY, stringResource(R.string.onboarding_q2_family), Icons.Filled.FamilyRestroom),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                SelectableCard(option, selected == option.id) { onSelect(option.id) }
            }
        }
    }
}

@Composable
fun QuestionThreeStep(selected: String?, onSelect: (String) -> Unit, onBack: () -> Unit) {
    QuestionScaffold(
        progress = 3,
        total = 4,
        onBack = onBack,
        title = stringResource(R.string.onboarding_q3_title),
        subtitle = stringResource(R.string.onboarding_q3_sub),
    ) {
        val commitments = listOf(
            Triple(PlanBuilder.COMMIT_SOFT, R.string.onboarding_q3_soft, R.string.onboarding_q3_soft_sub),
            Triple(PlanBuilder.COMMIT_SERIOUS, R.string.onboarding_q3_serious, R.string.onboarding_q3_serious_sub),
            Triple(PlanBuilder.COMMIT_STRICT, R.string.onboarding_q3_strict, R.string.onboarding_q3_strict_sub),
        )
        val commitIcons = listOf(Icons.Filled.Spa, Icons.Filled.FitnessCenter, Icons.Filled.Lock)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            commitments.forEachIndexed { index, (id, label, sub) ->
                val option = QuestionOption(id, stringResource(label), commitIcons[index])
                SelectableCardWithCaption(option, stringResource(sub), selected == option.id) { onSelect(option.id) }
            }
        }
    }
}

/** بطاقة التزام مع سطر وصفي أطول. */
@Composable
private fun SelectableCardWithCaption(
    option: QuestionOption,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, spring(dampingRatio = 0.45f, stiffness = 380f), label = "commit_scale")
    val borderColor by animateColorAsState(if (selected) SawnPalette.Primary else SawnPalette.Line, label = "commit_border")
    val backgroundColor by animateColorAsState(if (selected) SawnPalette.PrimarySoft else SawnPalette.Card, label = "commit_bg")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth().scale(scale).border(1.5.dp, borderColor, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(option.icon, contentDescription = null, tint = if (selected) SawnPalette.PrimaryDeep else SawnPalette.InkSoft, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(option.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SawnPalette.Ink)
                Text(caption, style = MaterialTheme.typography.labelSmall, color = SawnPalette.InkSoft)
            }
            if (selected) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(SawnPalette.Primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QuestionFourStep(
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    onBack: () -> Unit,
    onBuildPlan: () -> Unit,
) {
    QuestionScaffold(
        progress = 4,
        total = 4,
        onBack = onBack,
        title = stringResource(R.string.onboarding_q4_title),
        subtitle = stringResource(R.string.onboarding_q4_sub),
    ) {
        val chips = listOf(
            PlanBuilder.PORN_CONTENT to stringResource(R.string.onboarding_q4_porn),
            PlanBuilder.ADS_CONTENT to stringResource(R.string.onboarding_q4_ads),
            PlanBuilder.FEEDS_CONTENT to stringResource(R.string.onboarding_q4_feeds),
            PlanBuilder.APPS_CONTENT to stringResource(R.string.onboarding_q4_apps),
            PlanBuilder.SEARCH_CONTENT to stringResource(R.string.onboarding_q4_search),
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { (id, label) ->
                    SelectableChip(label, id in selected) {
                        onChange(if (id in selected) selected - id else selected + id)
                    }
                }
            }
            // تلميح الإباحية: يظهر فقط عند اختيارها
            if (PlanBuilder.PORN_CONTENT in selected) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = SawnPalette.PrimarySoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SawnPalette.Line, MaterialTheme.shapes.medium),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = SawnPalette.PrimaryDeep, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.onboarding_q4_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = SawnPalette.PrimaryDeep,
                        )
                    }
                }
            }
            com.sawn.app.ui.PillCTA(
                text = stringResource(R.string.onboarding_q4_cta),
                onClick = onBuildPlan,
            )
        }
    }
}
