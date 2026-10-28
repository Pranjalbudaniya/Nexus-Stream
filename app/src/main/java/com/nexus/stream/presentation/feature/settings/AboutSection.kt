package com.nexus.stream.presentation.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun AboutSection(
    state: AboutUiState,
    onEvent: (AboutEvent) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // 1. Version
        SettingsValueRow(
            title = stringResource(R.string.settings_about_version),
            value = state.versionText,
        )

        // 2. Source code on GitHub
        SettingsRow(
            title = stringResource(R.string.settings_about_github),
            onClick = { onEvent(AboutEvent.OpenGithub) },
        )

        // 3. Report an issue
        SettingsRow(
            title = stringResource(R.string.settings_about_issues),
            onClick = { onEvent(AboutEvent.OpenIssues) },
        )

        // 4. Open-source licenses
        SettingsRow(
            title = stringResource(R.string.settings_about_licenses),
            onClick = { onEvent(AboutEvent.OpenLicenses) },
        )

        // 5. Disclaimer
        SettingsRow(
            title = stringResource(R.string.settings_about_disclaimer),
            onClick = { onEvent(AboutEvent.OpenDisclaimer) },
        )
    }

    if (state.showLicenses) {
        LicensesSheet(
            onDismiss = { onEvent(AboutEvent.DismissLicenses) },
            onOpenUrl = onOpenUrl,
        )
    }

    if (state.showDisclaimer) {
        AlertDialog(
            onDismissRequest = { onEvent(AboutEvent.DismissDisclaimer) },
            title = { Text(stringResource(R.string.settings_about_disclaimer)) },
            text = { Text(stringResource(R.string.settings_about_disclaimer_text)) },
            confirmButton = {
                NexusTextButton(onClick = { onEvent(AboutEvent.DismissDisclaimer) }) {
                    Text(stringResource(R.string.settings_about_close))
                }
            },
        )
    }
}

@Preview(name = "AboutSection - Expanded")
@Composable
internal fun AboutSectionExpandedPreview() {
    NexusTheme {
        AboutSection(
            state = AboutUiState(versionText = "1.0.0 (10000)"),
            onEvent = {},
            onOpenUrl = {},
        )
    }
}
