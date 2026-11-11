package com.sawn.app.ui.onboarding.steps

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.sawn.app.R
import com.sawn.app.ui.PillCTA
import com.sawn.app.ui.dawnBackground
import com.sawn.app.ui.theme.SawnPalette

/** «ما اسمك؟» — اسم + عمر محليان 100%، بدون أي حساب. */
@Composable
fun ProfileStep(
    initialName: String?,
    initialAge: Int?,
    onContinue: (name: String?, age: Int?) -> Unit,
    onSkip: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName.orEmpty()) }
    var age by remember(initialAge) { mutableStateOf(initialAge?.toString().orEmpty()) }

    Column(
        Modifier
            .fillMaxSize()
            .dawnBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.onboarding_profile_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = SawnPalette.Ink,
            )
            Text(
                stringResource(R.string.onboarding_profile_sub),
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(30) },
            label = { Text(stringResource(R.string.onboarding_profile_name_hint)) },
            singleLine = true,
            shape = CircleShape,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = age,
            onValueChange = { raw -> age = raw.filter(Char::isDigit).take(3) },
            label = { Text(stringResource(R.string.onboarding_profile_age_hint)) },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PillCTA(
                text = stringResource(R.string.onboarding_profile_next),
                onClick = {
                    val ageValue = age.toIntOrNull()?.takeIf { it in 1..120 }
                    onContinue(name.trim().ifBlank { null }, ageValue)
                },
            )
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.onboarding_profile_skip),
                    color = SawnPalette.InkSoft,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SawnPalette.Primary,
    unfocusedBorderColor = SawnPalette.Line,
    focusedContainerColor = SawnPalette.Card,
    unfocusedContainerColor = SawnPalette.Card,
    focusedLabelColor = SawnPalette.Primary,
    unfocusedLabelColor = SawnPalette.InkSoft,
    cursorColor = SawnPalette.Primary,
)
