package com.nexus.stream.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.presentation.theme.isLowChroma
import com.nexus.stream.presentation.theme.normalizeSeed
import com.nexus.stream.presentation.theme.tokens.AccentSwatches
import com.nexus.stream.presentation.theme.tokens.BrandAccent
import com.nexus.stream.presentation.theme.wasAdjusted
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorSheet(
    initialColor: Color,
    onDismiss: () -> Unit,
    onPreview: (argb: Long?, immediate: Boolean) -> Unit,
    onApply: (argb: Long, remember: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current

    val initialHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv.first) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv.second) }
    var value by rememberSaveable { mutableFloatStateOf(initialHsv.third) }

    val currentColor = remember(hue, saturation, value) { Color.hsv(hue, saturation, value) }
    var hexText by rememberSaveable { mutableStateOf(currentColor.toHexString()) }
    var isHexValid by rememberSaveable { mutableStateOf(true) }
    var isUserEditingHex by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            onPreview(null, true)
            onDismiss()
        },
        sheetState = sheetState,
        modifier = modifier.imePadding(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.custom_color_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .drawBehind {
                                drawCircle(color = Color.hsv(hue, saturation, value))
                            },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val hueLabel = stringResource(R.string.custom_color_hue)
            val satLabel = stringResource(R.string.custom_color_saturation)
            val valLabel = stringResource(R.string.custom_color_brightness)

            val hueGradient =
                remember {
                    Brush.horizontalGradient(
                        listOf(
                            Color.hsv(0f, 1f, 1f),
                            Color.hsv(60f, 1f, 1f),
                            Color.hsv(120f, 1f, 1f),
                            Color.hsv(180f, 1f, 1f),
                            Color.hsv(240f, 1f, 1f),
                            Color.hsv(300f, 1f, 1f),
                            Color.hsv(360f, 1f, 1f),
                        ),
                    )
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(hueGradient),
            )
            Spacer(modifier = Modifier.height(4.dp))
            NexusSlider(
                value = hue,
                onValueChange = {
                    isUserEditingHex = false
                    hue = it
                    val color = Color.hsv(it, saturation, value)
                    hexText = color.toHexString()
                    isHexValid = true
                    onPreview(toNormalizedArgb(color), false)
                },
                onValueChangeFinished = {
                    val color = Color.hsv(hue, saturation, value)
                    onPreview(toNormalizedArgb(color), true)
                },
                valueRange = 0f..360f,
                label = { "$hueLabel: ${it.roundToInt()}°" },
            )

            NexusSlider(
                value = saturation,
                onValueChange = {
                    isUserEditingHex = false
                    saturation = it
                    val color = Color.hsv(hue, it, value)
                    hexText = color.toHexString()
                    isHexValid = true
                    onPreview(toNormalizedArgb(color), false)
                },
                onValueChangeFinished = {
                    val color = Color.hsv(hue, saturation, value)
                    onPreview(toNormalizedArgb(color), true)
                },
                valueRange = 0f..1f,
                label = { "$satLabel: ${(it * 100).roundToInt()}%" },
            )

            NexusSlider(
                value = value,
                onValueChange = {
                    isUserEditingHex = false
                    value = it
                    val color = Color.hsv(hue, saturation, it)
                    hexText = color.toHexString()
                    isHexValid = true
                    onPreview(toNormalizedArgb(color), false)
                },
                onValueChangeFinished = {
                    val color = Color.hsv(hue, saturation, value)
                    onPreview(toNormalizedArgb(color), true)
                },
                valueRange = 0f..1f,
                label = { "$valLabel: ${(it * 100).roundToInt()}%" },
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = hexText,
                onValueChange = { input ->
                    isUserEditingHex = true
                    hexText = input.uppercase()
                    val parsed = parseHexColor(input)
                    if (parsed != null) {
                        isHexValid = true
                        val hsv = colorToHsv(parsed)
                        hue = hsv.first
                        saturation = hsv.second
                        value = hsv.third
                        onPreview(toNormalizedArgb(parsed), false)
                    } else {
                        isHexValid = false
                    }
                },
                label = { Text(stringResource(R.string.custom_color_hex)) },
                isError = !isHexValid,
                supportingText =
                    if (!isHexValid) {
                        { Text(stringResource(R.string.custom_color_hex_error)) }
                    } else {
                        null
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        isUserEditingHex = false
                    }),
                leadingIcon = {
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .drawBehind {
                                    drawCircle(color = Color.hsv(hue, saturation, value))
                                },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLowChroma(currentColor)) {
                Text(
                    text = stringResource(R.string.custom_color_low_chroma_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (wasAdjusted(currentColor, normalizeSeed(currentColor))) {
                Text(
                    text = stringResource(R.string.custom_color_adjusted_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexusTextButton(
                    onClick = {
                        val defaultHsv = colorToHsv(BrandAccent)
                        hue = defaultHsv.first
                        saturation = defaultHsv.second
                        value = defaultHsv.third
                        isUserEditingHex = false
                        hexText = BrandAccent.toHexString()
                        isHexValid = true
                        onPreview(toNormalizedArgb(BrandAccent), false)
                    },
                ) {
                    Text(stringResource(R.string.custom_color_use_default))
                }

                Row {
                    NexusOutlinedButton(
                        onClick = {
                            onPreview(null, true)
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    NexusButton(
                        onClick = {
                            if (isHexValid) {
                                val currentArgb = toNormalizedArgb(Color.hsv(hue, saturation, value))
                                val matchesSwatch =
                                    AccentSwatches.any {
                                        (it.color.toArgb().toLong() and 0x00FFFFFFL) == (currentArgb and 0x00FFFFFFL)
                                    }
                                onApply(currentArgb, !matchesSwatch)
                                onDismiss()
                            }
                        },
                        enabled = isHexValid,
                    ) {
                        Text(stringResource(R.string.btn_apply))
                    }
                }
            }
        }
    }
}

private const val FULL_ALPHA = 255L
private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0x00FFFFFFL

private fun toNormalizedArgb(color: Color): Long =
    (FULL_ALPHA shl ALPHA_SHIFT) or (color.toArgb().toLong() and RGB_MASK)

private fun colorToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val hue =
        when {
            delta == 0f -> 0f
            max == r -> ((g - b) / delta).let { if (it < 0) it + 6f else it } * 60f
            max == g -> (((b - r) / delta) + 2f) * 60f
            else -> (((r - g) / delta) + 4f) * 60f
        }
    val sat = if (max == 0f) 0f else delta / max
    val value = max
    return Triple(hue.coerceIn(0f, MAX_HUE), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
}

private const val MAX_HUE = 360f
