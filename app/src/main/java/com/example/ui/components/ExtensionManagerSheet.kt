package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ExtensionRegistryInfo
import com.example.domain.model.ExtensionSettingType
import com.example.domain.model.InstalledExtension
import com.example.domain.model.RegistryExtensionEntry
import com.example.ui.ExtensionManagerUiState
import com.example.ui.theme.LocalSonoraTokens
import com.example.ui.theme.MixaHapticKind
import com.example.ui.theme.MixaHaptics
import com.example.ui.theme.hapticClickable

private const val MORNYE_SAMPLE_REGISTRY_URL =
    "https://raw.githubusercontent.com/mornyeapp/Mornye-Extension/main/registry.json"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExtensionManagerSheet(
    registries: List<ExtensionRegistryInfo>,
    installedExtensions: List<InstalledExtension>,
    uiState: ExtensionManagerUiState,
    onDismiss: () -> Unit,
    onAddRegistry: (String) -> Unit,
    onSyncAllRegistries: () -> Unit,
    onRemoveRegistry: (String) -> Unit,
    onInstallFromRegistry: (RegistryExtensionEntry, String) -> Unit,
    onImportLocalSflx: (android.net.Uri) -> Unit,
    onToggleExtensionEnabled: (String, Boolean) -> Unit,
    onUninstallExtension: (String) -> Unit,
    onReadExtensionSettings: (String) -> Map<String, String>,
    onSaveExtensionSetting: (String, String, String, Boolean) -> Unit,
    onClearMessages: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalSonoraTokens.current
    val view = LocalView.current
    val primary = MaterialTheme.colorScheme.primary

    var registryUrlInput by remember { mutableStateOf("") }
    var configuringExt by remember { mutableStateOf<InstalledExtension?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportLocalSflx(uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .testTag("extension_manager_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(tokens.chipCornerRadius))
                            .background(primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = primary
                        )
                    }
                    Column {
                        Text(
                            text = "Extensions & Providers",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mornye .sflx QuickJS sandbox • Qobuz, Deezer & ISRC bridges",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = {
                    MixaHaptics.tap(view)
                    onDismiss()
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close extension manager")
                }
            }

            // Status / Error Banner
            val bannerText = uiState.errorMessage ?: uiState.statusMessage
            if (!bannerText.isNullOrBlank()) {
                val isError = uiState.errorMessage != null
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isError) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    } else {
                        tokens.emeraldAccent.copy(alpha = 0.16f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = bannerText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else tokens.emeraldAccent,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                MixaHaptics.tap(view)
                                onClearMessages()
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss status",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Registry Add Card
                item(key = "add_registry_card") {
                    Surface(
                        shape = RoundedCornerShape(tokens.cardCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = WaveformTealPlayed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "ADD EXTENSION REGISTRY OR .SFLX",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WaveformTealPlayed
                                )
                            }
                            TextField(
                                value = registryUrlInput,
                                onValueChange = { registryUrlInput = it },
                                placeholder = { Text("https://.../registry.json") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("registry_url_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (registryUrlInput.isNotBlank()) {
                                            MixaHaptics.confirm(view)
                                            onAddRegistry(registryUrlInput.trim())
                                            registryUrlInput = ""
                                        }
                                    },
                                    enabled = registryUrlInput.isNotBlank() && !uiState.isSyncing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("add_registry_button")
                                ) {
                                    if (uiState.isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text("Add Feed")
                                }
                                OutlinedButton(
                                    onClick = {
                                        MixaHaptics.tap(view)
                                        filePickerLauncher.launch(
                                            arrayOf("application/zip", "application/octet-stream", "*/*")
                                        )
                                    },
                                    modifier = Modifier.testTag("import_local_sflx_button")
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(".sflx")
                                }
                            }

                            if (registries.none { it.url == MORNYE_SAMPLE_REGISTRY_URL }) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = primary.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .hapticClickable(kind = MixaHapticKind.TAP) {
                                            registryUrlInput = MORNYE_SAMPLE_REGISTRY_URL
                                        }
                                        .testTag("prefill_mornye_registry_chip")
                                ) {
                                    Text(
                                        text = "Tap to autofill Mornye community registry.json URL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Installed Extensions
                if (installedExtensions.isNotEmpty()) {
                    item(key = "installed_header") {
                        Text(
                            text = "INSTALLED PROVIDERS (${installedExtensions.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = primary
                        )
                    }
                    items(installedExtensions, key = { "inst_${it.id}" }) { ext ->
                        Surface(
                            shape = RoundedCornerShape(tokens.cardCornerRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("installed_ext_${ext.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = ext.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (ext.manifest.isMetadataOnly) {
                                                    HiResGoldAccent.copy(alpha = 0.18f)
                                                } else {
                                                    WaveformTealPlayed.copy(alpha = 0.16f)
                                                }
                                            ) {
                                                Text(
                                                    text = if (ext.manifest.isMetadataOnly) "ISRC BRIDGE" else "AUDIO + SEARCH",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (ext.manifest.isMetadataOnly) HiResGoldAccent else WaveformTealPlayed,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "v${ext.version} • ${ext.manifest.author}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = ext.isEnabled,
                                        onCheckedChange = { enabled ->
                                            MixaHaptics.toggle(view, enabled)
                                            onToggleExtensionEnabled(ext.id, enabled)
                                        },
                                        modifier = Modifier.testTag("toggle_ext_${ext.id}")
                                    )
                                }

                                if (ext.manifest.description.isNotBlank()) {
                                    Text(
                                        text = ext.manifest.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (!ext.lastError.isNullOrBlank()) {
                                    Text(
                                        text = "Last error: ${ext.lastError}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (ext.manifest.settingsSchema.isNotEmpty()) {
                                        FilledTonalButton(
                                            onClick = {
                                                MixaHaptics.tap(view)
                                                configuringExt = ext
                                            },
                                            modifier = Modifier.testTag("configure_ext_${ext.id}")
                                        ) {
                                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Settings")
                                        }
                                    } else {
                                        Text(
                                            text = if (ext.manifest.allowedHosts.isEmpty()) {
                                                "No hosts declared"
                                            } else {
                                                "Hosts: ${ext.manifest.allowedHosts.joinToString(", ")}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            MixaHaptics.reject(view)
                                            onUninstallExtension(ext.id)
                                        },
                                        modifier = Modifier.testTag("uninstall_ext_${ext.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Uninstall ${ext.name}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Synced Registries & Available Packages
                if (registries.isNotEmpty()) {
                    item(key = "registries_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SYNCED REGISTRIES (${registries.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = primary
                            )
                            TextButton(
                                onClick = {
                                    MixaHaptics.tap(view)
                                    onSyncAllRegistries()
                                },
                                enabled = !uiState.isSyncing
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refresh")
                            }
                        }
                    }

                    registries.forEach { registry ->
                        item(key = "reg_${registry.url}") {
                            Surface(
                                shape = RoundedCornerShape(tokens.cardCornerRadius),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = registry.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${registry.entries.size} extensions • ${registry.url}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = {
                                            MixaHaptics.reject(view)
                                            onRemoveRegistry(registry.url)
                                        }) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Remove registry",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    registry.entries.forEach { entry ->
                                        val installedMatch = installedExtensions.firstOrNull { it.id == entry.id }
                                        val isInstalling = uiState.installingId == entry.id
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                    Text(
                                                        text = "${entry.name} (${entry.version})",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (entry.description.isNotBlank()) {
                                                        Text(
                                                            text = entry.description,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (entry.tags.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            entry.tags.take(3).forEach { tag ->
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                                                ) {
                                                                    Text(
                                                                        text = tag,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                if (installedMatch != null && installedMatch.version == entry.version) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CheckCircle,
                                                        contentDescription = "Installed",
                                                        tint = tokens.emeraldAccent,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                } else {
                                                    FilledTonalButton(
                                                        onClick = {
                                                            MixaHaptics.confirm(view)
                                                            onInstallFromRegistry(entry, registry.url)
                                                        },
                                                        enabled = !isInstalling,
                                                        modifier = Modifier.testTag("install_reg_${entry.id}")
                                                    ) {
                                                        if (isInstalling) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(16.dp),
                                                                strokeWidth = 2.dp
                                                            )
                                                        } else {
                                                            Icon(
                                                                Icons.Rounded.CloudDownload,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(if (installedMatch == null) "Install" else "Update")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (installedExtensions.isEmpty()) {
                    item(key = "extensions_empty_hint") {
                        Surface(
                            shape = RoundedCornerShape(tokens.cardCornerRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No Extension Providers Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Add a Mornye registry.json feed or import a .sflx file to search and stream from Qobuz, Deezer, or ISRC-bridged catalogs like Apple Music. Local, YouTube Music, and Hi-Res Radio work unaffected either way.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    configuringExt?.let { ext ->
        val existingSettings = remember(ext.id) { onReadExtensionSettings(ext.id) }
        val draftValues = remember(ext.id) {
            mutableStateMapOf<String, String>().apply {
                ext.manifest.settingsSchema.forEach { field ->
                    put(field.key, existingSettings[field.key] ?: field.defaultValue)
                }
            }
        }
        val secretVisibility = remember(ext.id) { mutableStateMapOf<String, Boolean>() }

        AlertDialog(
            onDismissRequest = { configuringExt = null },
            title = { Text("${ext.name} Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ext.manifest.settingsSchema.forEach { field ->
                        val currentVal = draftValues[field.key].orEmpty()
                        when (field.type) {
                            ExtensionSettingType.BOOLEAN -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(field.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (field.description.isNotBlank()) {
                                            Text(
                                                field.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = currentVal.equals("true", ignoreCase = true),
                                        onCheckedChange = { checked ->
                                            MixaHaptics.toggle(view, checked)
                                            draftValues[field.key] = checked.toString()
                                        }
                                    )
                                }
                            }
                            ExtensionSettingType.SELECT -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(field.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        field.options.forEach { opt ->
                                            FilterChip(
                                                selected = currentVal == opt,
                                                onClick = {
                                                    MixaHaptics.tick(view)
                                                    draftValues[field.key] = opt
                                                },
                                                label = { Text(opt) }
                                            )
                                        }
                                    }
                                }
                            }
                            ExtensionSettingType.SECRET,
                            ExtensionSettingType.TEXT -> {
                                val isSecret = field.type == ExtensionSettingType.SECRET
                                val visible = secretVisibility[field.key] == true
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(field.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (field.description.isNotBlank()) {
                                        Text(
                                            field.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextField(
                                        value = currentVal,
                                        onValueChange = { draftValues[field.key] = it },
                                        singleLine = true,
                                        visualTransformation = if (isSecret && !visible) {
                                            PasswordVisualTransformation()
                                        } else {
                                            VisualTransformation.None
                                        },
                                        trailingIcon = if (isSecret) {
                                            {
                                                IconButton(onClick = {
                                                    MixaHaptics.tap(view)
                                                    secretVisibility[field.key] = !visible
                                                }) {
                                                    Icon(
                                                        imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                        contentDescription = "Toggle visibility"
                                                    )
                                                }
                                            }
                                        } else null,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    MixaHaptics.confirm(view)
                    ext.manifest.settingsSchema.forEach { field ->
                        onSaveExtensionSetting(
                            ext.id,
                            field.key,
                            draftValues[field.key].orEmpty(),
                            field.type == ExtensionSettingType.SECRET
                        )
                    }
                    configuringExt = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    MixaHaptics.tap(view)
                    configuringExt = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
