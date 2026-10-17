package com.nexus.stream.presentation.feature.addons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nexus.stream.domain.model.AddonCategory

const val STORE_SHEET_GESTURES_ENABLED = true

@Composable
fun AddonStoreSheet(
    initialCategory: AddonCategory? = null,
    onDismiss: () -> Unit,
) {
    remember(initialCategory, onDismiss) { initialCategory to onDismiss }
}
