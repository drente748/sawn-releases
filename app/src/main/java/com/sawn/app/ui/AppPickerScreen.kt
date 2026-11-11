package com.sawn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawn.app.R
import com.sawn.domain.model.BlockType
import com.sawn.app.usage.AppsHelper
import com.sawn.app.usage.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppPickerScreen(vm: SawnViewModel, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val blockedPackages = items.filter { it.type == BlockType.APP }.map { it.value }.toSet()
    var query by remember { mutableStateOf("") }

    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { AppsHelper.launchableApps(context) }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        BackHeader(stringResource(R.string.app_picker_title), onBack)
        Text(
            stringResource(R.string.app_picker_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_apps)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        val list = apps
        if (list == null) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filtered = list.filter { it.label.contains(query, ignoreCase = true) }
            if (filtered.isEmpty()) {
                Text(
                    stringResource(R.string.app_picker_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val added = app.packageName in blockedPackages
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !added) { vm.addApp(app.packageName, app.label) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.packageName, modifier = Modifier.size(40.dp).padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (added) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = stringResource(R.string.added),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
