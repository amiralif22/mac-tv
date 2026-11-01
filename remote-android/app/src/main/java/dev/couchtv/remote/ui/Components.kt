package dev.couchtv.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The remote's controls behave like hardware: they depress by four percent, they tick, and they act
 * on release. Everything below is that one idea, drawn three ways.
 */
@Composable
fun PillButton(
    label: String,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    Text(
        text = label,
        style = Couch.Mono.copy(color = if (prominent) Couch.Canvas else Couch.Ink, fontWeight = FontWeight.SemiBold),
        modifier = modifier
            .scale(if (pressed) 0.96f else 1f)
            .clip(RoundedCornerShape(50))
            .background(if (prominent) Couch.Phosphor else Couch.Glass)
            .border(1.dp, if (prominent) Color.Transparent else Couch.Hairline, RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

/** One key of the D-pad, an arrow or OK, with the same press behaviour as a physical button. */
@Composable
fun PadKey(
    icon: ImageVector? = null,
    label: String? = null,
    size: Int = 56,
    enabled: Boolean = true,
    onPress: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .size(size.dp)
            .scale(if (pressed) 0.94f else 1f)
            .clip(shape)
            .background(Color.White.copy(alpha = if (pressed) 0.16f else 0.07f))
            .border(1.dp, Couch.Hairline, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPress()
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Icon(icon, contentDescription = label, tint = Couch.Ink, modifier = Modifier.size(18.dp))
            label != null -> Text(label, color = Couch.Ink, fontWeight = FontWeight.Bold)
        }
    }
}

/** The glass panel the sheets sit on. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(shape)
            .background(Couch.Glass)
            .border(1.dp, Couch.Hairline, shape)
    ) { content() }
}
