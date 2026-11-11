package com.sawn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sawn.app.R
import com.sawn.app.ui.theme.SawnPalette
import com.sawn.domain.feature.JournalSnapshot
import java.time.LocalDate
import java.util.Locale

/** أرقام لاتينية دائماً — بغض النظر عن لغة النظام. */
object SawnNumbers {
    fun format(n: Long): String = String.format(Locale.US, "%d", n)
    fun format(n: Int): String = String.format(Locale.US, "%d", n)
}

/** خلفية «الفجر»: تدرج دافئ من الخوخي الكريمي إلى الأبيض الناعم + قرص شمس خفيف. */
fun Modifier.dawnBackground(): Modifier = this.drawBehind {
    val width = size.width
    val height = size.height
    drawRect(
        Brush.verticalGradient(
            colors = listOf(
                SawnPalette.DawnBg,
                Color(0xFFFFF1E4),
                Color(0xFFFFFDF8),
            ),
        ),
    )
    // قرص الشمس (شعاعي ناعم) في الثلث العلوي
    val radius = minOf(width, height) * 0.55f
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to SawnPalette.Gold.copy(alpha = 0.55f),
                0.55f to SawnPalette.Gold.copy(alpha = 0.18f),
                1f to SawnPalette.Gold.copy(alpha = 0f),
            ),
            center = Offset(width * 0.5f, height * 0.16f),
            radius = radius,
        ),
        radius = radius,
        center = Offset(width * 0.5f, height * 0.16f),
    )
}

/** بطاقة صَوْن: سطح أبيض بحافة خوخي ناعمة وظل دافئ. */
@Composable
fun DawnCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = Color(0x1FEA580C), spotColor = Color(0x26EA580C))
            .border(1.dp, SawnPalette.Line, shape),
        shape = shape,
        color = SawnPalette.Card,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), content = content)
    }
}

/** عنوان قسم هادئ — شريط عمودي برتقالي + عنوان + تلميح. */
@Composable
fun SectionHeader(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SawnPalette.Primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = SawnPalette.Ink)
        }
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** رسم أعمدة لآخر سبعة أيام — يستخدمه الرئيسية والإنجازات معاً. */
@Composable
fun ProtectionWeekChart(snapshot: JournalSnapshot, modifier: Modifier = Modifier) {
    val letters = listOf(
        R.string.day_letter_mon, R.string.day_letter_tue, R.string.day_letter_wed,
        R.string.day_letter_thu, R.string.day_letter_fri, R.string.day_letter_sat,
        R.string.day_letter_sun,
    )
    val today = LocalDate.now()
    val peak = maxOf(1, snapshot.peak)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        snapshot.last7.forEachIndexed { index, count ->
            val day = today.minusDays((6 - index).toLong())
            val isToday = index == snapshot.last7.lastIndex
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    SawnNumbers.format(count),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) SawnPalette.PrimaryDeep else SawnPalette.InkSoft,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(SawnPalette.Track),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (count > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight((count.toFloat() / peak).coerceIn(0.08f, 1f))
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isToday) SawnPalette.Primary else SawnPalette.GoldDeep),
                        )
                    }
                }
                Text(
                    stringResource(letters[day.dayOfWeek.value - 1]),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) SawnPalette.PrimaryDeep else SawnPalette.InkSoft,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** بطاقة رقم مختصرة. [highlight] تجعلها بلون العلامة، ويبقى النص مقروءاً دائماً. */
@Composable
fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (highlight) SawnPalette.PrimarySoft else SawnPalette.Card,
        modifier = modifier
            .border(1.dp, SawnPalette.Line, MaterialTheme.shapes.medium),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = if (highlight) SawnPalette.PrimaryDeep else SawnPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
                maxLines = 2,
            )
        }
    }
}

/** بلاطة أداة: أيقونة + عنوان + سطر وصفي. */
@Composable
fun ToolTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = SawnPalette.Card,
        modifier = modifier.border(1.dp, SawnPalette.Line, MaterialTheme.shapes.medium),
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SawnPalette.PrimarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = SawnPalette.Primary, modifier = Modifier.size(22.dp))
            }
            Text(title, style = MaterialTheme.typography.labelLarge, color = SawnPalette.Ink, textAlign = TextAlign.Center)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = SawnPalette.InkSoft, textAlign = TextAlign.Center)
        }
    }
}

/** صف ميزة مضغوط: أيقونة ملونة + عنوان + سطر + إجراء اختياري. */
@Composable
fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBackground: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = SawnPalette.Ink)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SawnPalette.InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** زر صَوْن الكبير — الكبسولة البرتقالية بظل دافئ. */
@Composable
fun PillCTA(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(10.dp, CircleShape, ambientColor = Color(0x4DEA580C), spotColor = Color(0x4DEA580C)),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = SawnPalette.Primary,
            contentColor = SawnPalette.OnPrimary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** بطاقة مفتاح «الحماية المباشرة» — أيقونة + عنوان + سطر + سويتش. */
@Composable
fun ProtectionToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = SawnPalette.Card,
        modifier = modifier.border(1.dp, SawnPalette.Line, MaterialTheme.shapes.medium),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = SawnPalette.Primary, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = SawnPalette.Ink, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SawnPalette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.align(Alignment.Start),
            )
        }
    }
}

/** شرائح «خفيف/متوازن/الحد الأقصى». */
@Composable
fun PresetChips(
    selected: Int,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Surface(
                onClick = { onSelect(index) },
                shape = CircleShape,
                color = if (active) SawnPalette.Primary else SawnPalette.Card,
                modifier = if (active) {
                    Modifier
                } else {
                    Modifier.border(1.dp, SawnPalette.Line, CircleShape)
                },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) SawnPalette.OnPrimary else SawnPalette.InkSoft,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** الدرع/الشمس داخل حلقة التقدم — «بطل» الهوية. */
enum class HeroState { PROTECTED, PARTIAL, DOWN, NEEDS_SETUP, LOCKDOWN }

@Composable
fun HeroShield(
    state: HeroState,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val ringColor = when (state) {
        HeroState.PROTECTED -> SawnPalette.Primary
        HeroState.PARTIAL -> SawnPalette.GoldDeep
        HeroState.DOWN -> SawnPalette.Error
        HeroState.NEEDS_SETUP -> SawnPalette.OnGoldContainer
        HeroState.LOCKDOWN -> SawnPalette.Ink
    }
    Canvas(modifier.aspectRatio(1f)) {
        val stroke = size.minDimension * 0.105f
        val radius = (size.minDimension - stroke) / 2
        val center = Offset(size.width / 2, size.height / 2)
        // المسار
        drawCircle(SawnPalette.Track, radius, center, style = Stroke(stroke, cap = StrokeCap.Round))
        // التقدّم (0..1)
        val clamped = progress.coerceIn(0f, 1f)
        drawArc(
            color = ringColor,
            startAngle = -90f,
            sweepAngle = 360f * clamped,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        // قرص داخلي متدرج
        drawCircle(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0f to SawnPalette.Gold.copy(alpha = 0.9f),
                    0.6f to SawnPalette.PrimarySoft,
                    1f to SawnPalette.DawnBg,
                ),
                radius = radius * 0.78f,
            ),
            radius * 0.78f,
            center,
        )
        // درع الشمس: دائرة شمس + أفق + خط أشعّة
        val sunR = radius * 0.30f
        drawCircle(SawnPalette.Primary, sunR, Offset(center.x, center.y - radius * 0.08f))
        val horizon = center.y + radius * 0.24f
        drawPath(
            path = Path().apply {
                moveTo(center.x - radius * 0.44f, horizon)
                lineTo(center.x + radius * 0.44f, horizon)
            },
            color = SawnPalette.PrimaryDeep,
            style = Stroke(radius * 0.06f, cap = StrokeCap.Round),
        )
        val rayColor = SawnPalette.PrimaryDeep
        val rayLen = radius * 0.13f
        val sunCenter = Offset(center.x, center.y - radius * 0.08f)
        for (angleDeg in listOf(-90f, -135f, -45f, -180f, 0f)) {
            val a = Math.toRadians(angleDeg.toDouble())
            val dir = Offset(Math.cos(a).toFloat(), Math.sin(a).toFloat())
            drawPath(
                path = Path().apply {
                    moveTo(sunCenter.x + dir.x * (sunR + rayLen * 0.35f), sunCenter.y + dir.y * (sunR + rayLen * 0.35f))
                    lineTo(sunCenter.x + dir.x * (sunR + rayLen), sunCenter.y + dir.y * (sunR + rayLen))
                },
                color = rayColor,
                style = Stroke(radius * 0.05f, cap = StrokeCap.Round),
            )
        }
    }
}
