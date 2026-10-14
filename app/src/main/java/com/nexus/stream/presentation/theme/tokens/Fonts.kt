package com.nexus.stream.presentation.theme.tokens

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.nexus.stream.R
import com.nexus.stream.domain.model.AppFont

private val VARIABLE_WEIGHTS =
    listOf(
        400 to FontWeight.Normal,
        500 to FontWeight.Medium,
        600 to FontWeight.SemiBold,
        700 to FontWeight.Bold,
    )

private class FontSpec(
    val isVariable: Boolean,
    val fonts: List<Pair<Int, FontWeight>>,
)

private val FONT_SPECS =
    mapOf(
        AppFont.INTER to FontSpec(true, listOf(R.font.inter_variable to FontWeight.Normal)),
        AppFont.MANROPE to FontSpec(true, listOf(R.font.manrope_variable to FontWeight.Normal)),
        AppFont.DM_SANS to FontSpec(true, listOf(R.font.dm_sans_variable to FontWeight.Normal)),
        AppFont.OUTFIT to FontSpec(true, listOf(R.font.outfit_variable to FontWeight.Normal)),
        AppFont.PLUS_JAKARTA_SANS to FontSpec(true, listOf(R.font.plus_jakarta_sans_variable to FontWeight.Normal)),
        AppFont.LEXEND to FontSpec(true, listOf(R.font.lexend_variable to FontWeight.Normal)),
        AppFont.NUNITO to FontSpec(true, listOf(R.font.nunito_variable to FontWeight.Normal)),
        AppFont.SPACE_GROTESK to FontSpec(true, listOf(R.font.space_grotesk_variable to FontWeight.Normal)),
        AppFont.FIGTREE to FontSpec(true, listOf(R.font.figtree_variable to FontWeight.Normal)),
        AppFont.POPPINS to
            FontSpec(
                false,
                listOf(
                    R.font.poppins_regular to FontWeight.Normal,
                    R.font.poppins_medium to FontWeight.Medium,
                    R.font.poppins_semibold to FontWeight.SemiBold,
                    R.font.poppins_bold to FontWeight.Bold,
                ),
            ),
    )

private val FONT_CACHE: Map<AppFont, FontFamily> =
    FONT_SPECS.mapValues { (_, spec) ->
        if (spec.isVariable) {
            val resId = spec.fonts.first().first
            FontFamily(
                VARIABLE_WEIGHTS.map { (w, weight) ->
                    Font(
                        resId = resId,
                        weight = weight,
                        variationSettings = FontVariation.Settings(FontVariation.weight(w)),
                    )
                },
            )
        } else {
            FontFamily(
                spec.fonts.map { (resId, weight) ->
                    Font(resId = resId, weight = weight)
                },
            )
        }
    }

fun fontFamilyFor(font: AppFont): FontFamily =
    if (font == AppFont.SYSTEM) FontFamily.Default else FONT_CACHE[font] ?: FontFamily.Default
