package com.nexus.stream.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.AppFont
import com.nexus.stream.presentation.theme.tokens.fontFamilyFor

fun fontDisplayName(font: AppFont): Int =
    when (font) {
        AppFont.SYSTEM -> R.string.font_name_system
        AppFont.INTER -> R.string.font_name_inter
        AppFont.MANROPE -> R.string.font_name_manrope
        AppFont.DM_SANS -> R.string.font_name_dm_sans
        AppFont.OUTFIT -> R.string.font_name_outfit
        AppFont.PLUS_JAKARTA_SANS -> R.string.font_name_plus_jakarta_sans
        AppFont.LEXEND -> R.string.font_name_lexend
        AppFont.NUNITO -> R.string.font_name_nunito
        AppFont.SPACE_GROTESK -> R.string.font_name_space_grotesk
        AppFont.POPPINS -> R.string.font_name_poppins
        AppFont.FIGTREE -> R.string.font_name_figtree
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontPickerSheet(
    selected: AppFont,
    onSelect: (AppFont) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val fonts = remember { AppFont.entries }

    LaunchedEffect(selected) {
        val index = fonts.indexOf(selected)
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.font_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(fonts) { font ->
                    val isSelected = font == selected
                    val family = remember(font) { fontFamilyFor(font) }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(font)
                                }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(fontDisplayName(font)),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = family),
                                color =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                            Text(
                                text = stringResource(R.string.font_sample_line),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = family),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
