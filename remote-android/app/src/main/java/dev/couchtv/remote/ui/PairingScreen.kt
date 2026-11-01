package dev.couchtv.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.couchtv.remote.R

/**
 * The pairing code, entered once per TV.
 *
 * Six boxes drawn by us, over one real text field: the numeric keypad, pasting a code from a message
 * and the system's one-time-code autofill all keep working, and the boxes are only a rendering of
 * what the field holds.
 */
@Composable
fun PairingScreen(
    tvName: String,
    attempts: Int,
    message: String?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.pair_with, tvName), style = Couch.title(20))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.pair_hint), style = Couch.Mono, color = Couch.Dim)

        Spacer(Modifier.height(24.dp))

        Box(contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(6) { index ->
                    val digit = code.getOrNull(index)?.toString().orEmpty()
                    val active = index == code.length.coerceAtMost(5)
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = if (digit.isEmpty()) 0.05f else 0.10f))
                            .border(
                                1.dp,
                                if (active) Couch.Phosphor.copy(alpha = 0.6f) else Couch.Hairline,
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = digit,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Couch.Ink,
                            ),
                        )
                    }
                }
            }

            BasicTextField(
                value = code,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(6)
                    code = digits
                    // Six digits means the code is complete: send it once, as soon as it lands.
                    if (digits.length == 6) onSubmit(digits)
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = TextStyle(color = Color.Transparent),
                cursorBrush = SolidColor(Color.Transparent),
                singleLine = true,
            )
        }

        val hint = when {
            attempts >= 5 -> "Too many attempts. Wait a few minutes, then ask the TV for a new code."
            message != null -> message
            code.isNotEmpty() && code.length < 6 -> "The code is six digits, shown on the TV under Settings › Phone remote."
            else -> null
        }
        if (hint != null) {
            Spacer(Modifier.height(14.dp))
            Text(hint, color = Couch.Warning, style = Couch.Mono)
        }

        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(label = stringResource(R.string.cancel), onClick = onCancel)
            PillButton(
                label = stringResource(R.string.pair),
                prominent = code.length == 6,
                onClick = { if (code.length == 6) onSubmit(code) },
            )
        }
    }
}
