package com.nexus.stream.presentation.feature.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAddonSheet(
    state: AddSheetState,
    onEvent: (AddonManagerEvent) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is AddSheetState.Closed) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onEvent(AddonManagerEvent.DismissAddSheet) },
        sheetState = sheetState,
        shape = NexusTheme.shapes.sheet,
        modifier = modifier.imePadding(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexusTheme.spacing.lg)
                    .padding(bottom = NexusTheme.spacing.xxl)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
        ) {
            Text(
                text = stringResource(R.string.addon_add_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            when (state) {
                is AddSheetState.Input -> {
                    InputContent(state = state, onEvent = onEvent)
                }

                is AddSheetState.Checking -> {
                    LoadingContent(
                        text = stringResource(R.string.addon_checking_loading),
                    )
                }

                is AddSheetState.Preview -> {
                    PreviewCard(
                        preview = state,
                        onInstall = { onEvent(AddonManagerEvent.Install) },
                        onOpenUrl = onOpenUrl,
                    )
                }

                is AddSheetState.Installing -> {
                    LoadingContent(
                        text = stringResource(R.string.addon_installing_loading),
                    )
                }

                AddSheetState.Closed -> {}
            }
        }
    }
}

@Composable
private fun formatAddonError(error: AddonErrorText): String =
    if (error.formatArgs.isEmpty()) {
        stringResource(error.resId)
    } else if (error.formatArgs.size == 1) {
        stringResource(error.resId, error.formatArgs[0])
    } else {
        stringResource(error.resId, error.formatArgs[0], error.formatArgs[1])
    }

@Composable
private fun InputContent(
    state: AddSheetState.Input,
    onEvent: (AddonManagerEvent) -> Unit,
) {
    val spacing = NexusTheme.spacing
    val clipboardManager = LocalClipboardManager.current

    OutlinedTextField(
        value = state.url,
        onValueChange = { onEvent(AddonManagerEvent.UrlChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.addon_url_label)) },
        placeholder = { Text(stringResource(R.string.addon_url_placeholder)) },
        isError = state.error != null,
        supportingText = {
            state.error?.let { err ->
                val text = formatAddonError(err)
                Text(text = text, color = MaterialTheme.colorScheme.error)
            }
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
    ) {
        NexusOutlinedButton(
            onClick = {
                val clipText =
                    clipboardManager
                        .getText()
                        ?.text
                        ?.trim()
                        .orEmpty()
                if (clipText.isNotEmpty()) {
                    onEvent(AddonManagerEvent.UrlChanged(clipText))
                }
            },
        ) {
            Text(stringResource(R.string.addon_btn_paste))
        }

        NexusButton(
            onClick = { onEvent(AddonManagerEvent.Check) },
            enabled = state.url.isNotBlank(),
        ) {
            Text(stringResource(R.string.addon_btn_check))
        }
    }
}

@Composable
private fun LoadingContent(text: String) {
    val spacing = NexusTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.xl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(spacing.md))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewCard(
    preview: AddSheetState.Preview,
    onInstall: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    val manifest = preview.manifest

    NexusCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AddonLogo(logoUrl = manifest.logo, addonName = manifest.name)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = manifest.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "v${manifest.version} • ${preview.displayHost}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (manifest.description.isNotBlank()) {
                Text(
                    text = manifest.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PreviewFlowChips(manifest = manifest)

            if (preview.alreadyInstalled) {
                InfoBanner(message = stringResource(R.string.addon_already_installed_banner))
            }

            if (preview.isHttp) {
                WarningBanner(message = stringResource(R.string.addon_http_warning))
            }

            if (manifest.behaviorHints.configurationRequired) {
                WarningBanner(
                    message = stringResource(R.string.addon_config_required_warning),
                    actionLabel = stringResource(R.string.addon_open_config),
                    onAction = {
                        val configUrl = "${preview.transportUrl.removeSuffix("/")}/configure"
                        onOpenUrl(configUrl)
                    },
                )
            }

            if (preview.isAdultBlocked) {
                WarningBanner(
                    message = stringResource(R.string.addon_url_adult_blocked),
                    isError = true,
                )
            }

            if (!preview.usable) {
                WarningBanner(
                    message = stringResource(R.string.addon_unusable_warning),
                    isError = true,
                )
            }

            val buttonLabel =
                if (preview.alreadyInstalled) {
                    stringResource(R.string.addon_btn_update)
                } else {
                    stringResource(R.string.addon_btn_install)
                }

            NexusButton(
                onClick = onInstall,
                enabled = preview.usable && !preview.isAdultBlocked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun PreviewFlowChips(
    manifest: AddonManifest,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        for (type in manifest.types) {
            SuggestionChip(
                onClick = {},
                label = { Text(type) },
                shape = NexusTheme.shapes.chip,
            )
        }
        for (resource in manifest.resources) {
            SuggestionChip(
                onClick = {},
                label = { Text(resource.name) },
                shape = NexusTheme.shapes.chip,
            )
        }
        if (manifest.catalogs.isNotEmpty()) {
            AssistChip(
                onClick = {},
                label = {
                    Text(stringResource(R.string.addon_catalogs_count, manifest.catalogs.size))
                },
                shape = NexusTheme.shapes.chip,
            )
        }
        if (manifest.behaviorHints.adult) {
            BadgeChip(
                label = stringResource(R.string.addon_badge_18_plus),
                isWarning = true,
            )
        }
        if (manifest.behaviorHints.p2p) {
            BadgeChip(
                label = stringResource(R.string.addon_badge_p2p),
                isWarning = false,
            )
        }
        if (manifest.behaviorHints.configurationRequired) {
            BadgeChip(
                label = stringResource(R.string.addon_badge_config_required),
                isWarning = true,
            )
        }
    }
}

@Composable
private fun BadgeChip(
    label: String,
    isWarning: Boolean,
) {
    val bg =
        if (isWarning) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val fg =
        if (isWarning) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    Box(
        modifier =
            Modifier
                .clip(NexusTheme.shapes.chip)
                .background(bg)
                .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xxs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

@Composable
private fun InfoBanner(message: String) {
    val spacing = NexusTheme.spacing
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(NexusTheme.shapes.card)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(spacing.sm),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun WarningBanner(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = NexusTheme.spacing
    val bg =
        if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            NexusTheme.extended.warning.copy(alpha = 0.15f)
        }
    val fg =
        if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            NexusTheme.extended.warning
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(NexusTheme.shapes.card)
                .background(bg)
                .padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = fg,
                modifier = Modifier.weight(1f),
            )
        }
        if (actionLabel != null && onAction != null) {
            NexusTextButton(
                onClick = onAction,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = actionLabel, color = fg, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(name = "AddAddonSheet - Input")
@Composable
internal fun InputContentPreview() {
    NexusTheme {
        InputContent(
            state = AddSheetState.Input(url = "https://example.com/manifest.json"),
            onEvent = {},
        )
    }
}

@Preview(name = "AddAddonSheet - Preview")
@Composable
internal fun PreviewCardPreview() {
    NexusTheme {
        PreviewCard(
            preview =
                AddSheetState.Preview(
                    manifest =
                        AddonManifest(
                            id = "org.stremio.cinemeta",
                            version = "3.0.14",
                            name = "Cinemeta",
                            description = "Official movie directory",
                            logo = null,
                            background = null,
                            types = listOf("movie", "series"),
                            idPrefixes = listOf("tt"),
                            resources = listOf(ResourceSpec("catalog", null, null)),
                            catalogs = emptyList(),
                            behaviorHints =
                                ManifestHints(
                                    adult = false,
                                    p2p = false,
                                    configurable = true,
                                    configurationRequired = false,
                                ),
                        ),
                    transportUrl = "https://v3-cinemeta.strem.io/",
                    displayHost = "v3-cinemeta.strem.io",
                    isHttp = false,
                    alreadyInstalled = true,
                    usable = true,
                ),
            onInstall = {},
            onOpenUrl = {},
        )
    }
}
