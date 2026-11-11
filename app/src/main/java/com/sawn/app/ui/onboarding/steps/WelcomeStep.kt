package com.sawn.app.ui.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sawn.app.R
import com.sawn.app.ui.PillCTA
import com.sawn.app.ui.dawnBackground
import com.sawn.app.ui.theme.SawnPalette

/** الترحيب «صباحٌ بلا ضجيج» — الشمس الذهبية وأفق هادئ. */
@Composable
fun WelcomeStep(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .dawnBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SpacerHeight(8.dp)

        SurfaceChip(stringResource(R.string.onboarding_badge))

        SunGuardian()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = SawnPalette.Ink,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.onboarding_welcome_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = SawnPalette.InkSoft,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrustChip(stringResource(R.string.onboarding_trust_noaccount))
                TrustChip(stringResource(R.string.onboarding_trust_notrack))
                TrustChip(stringResource(R.string.onboarding_trust_local))
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillCTA(text = stringResource(R.string.onboarding_welcome_cta), onClick = onStart)
            Text(
                stringResource(R.string.onboarding_welcome_hint),
                style = MaterialTheme.typography.labelSmall,
                color = SawnPalette.InkSoft,
            )
        }
        SpacerHeight(16.dp)
    }
}

/** شمس الشروق: قرص ذكي + أفقان + خط أفق — مرسومة بالكامل بدون أي صورة. */
@Composable
private fun SunGuardian(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(172.dp)) {
        val c = center
        val r = size.minDimension / 2f
        // هالة ذهبية
        drawCircle(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0f to SawnPalette.Gold.copy(alpha = 0.85f),
                    0.55f to SawnPalette.Gold.copy(alpha = 0.25f),
                    1f to Color.Transparent,
                ),
            ),
            radius = r,
            center = c,
        )
        // قرص الشمس
        drawCircle(SawnPalette.Primary, radius = r * 0.42f, center = Offset(c.x, c.y - r * 0.10f))
        val horizonY = c.y + r * 0.18f
        // الأفق
        drawLine(
            SawnPalette.PrimaryDeep,
            start = Offset(c.x - r * 0.52f, horizonY),
            end = Offset(c.x + r * 0.52f, horizonY),
            strokeWidth = r * 0.06f,
            cap = StrokeCap.Round,
        )
        drawLine(
            SawnPalette.PrimaryDeep.copy(alpha = 0.75f),
            start = Offset(c.x - r * 0.34f, horizonY + r * 0.16f),
            end = Offset(c.x + r * 0.34f, horizonY + r * 0.16f),
            strokeWidth = r * 0.045f,
            cap = StrokeCap.Round,
        )
        drawLine(
            SawnPalette.Primary.copy(alpha = 0.6f),
            start = Offset(c.x - r * 0.18f, horizonY + r * 0.31f),
            end = Offset(c.x + r * 0.18f, horizonY + r * 0.31f),
            strokeWidth = r * 0.035f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SurfaceChip(text: String) {
    Surface(
        shape = CircleShape,
        color = SawnPalette.PrimarySoft,
        modifier = Modifier.border(1.dp, SawnPalette.Line, CircleShape),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = SawnPalette.PrimaryDeep,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TrustChip(text: String) {
    Surface(
        shape = CircleShape,
        color = SawnPalette.Card,
        modifier = Modifier.border(1.dp, SawnPalette.Line, CircleShape),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = SawnPalette.InkSoft,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SpacerHeight(value: androidx.compose.ui.unit.Dp) =
    androidx.compose.foundation.layout.Spacer(Modifier.height(value))
