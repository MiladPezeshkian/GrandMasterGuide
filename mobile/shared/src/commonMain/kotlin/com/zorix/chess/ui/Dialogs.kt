package com.zorix.chess.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorix.chess.resources.*
import org.jetbrains.compose.resources.StringResource
import com.zorix.chess.controller.ChessController
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.components.BrandUnderline
import com.zorix.chess.ui.components.AppWordmark
import com.zorix.chess.ui.theme.ZorixColors

/** Paste / type a FEN. [onLoad] returns an error or null when the position was loaded. */
@Composable
fun FenDialog(initial: String, onLoad: (String) -> ChessController.FenError?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.fen_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    placeholder = { Text(stringResource(Res.string.fen_dialog_hint)) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    minLines = 2,
                    maxLines = 4,
                    isError = error != null,
                    supportingText = error?.let { e -> { Text(stringResource(e)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    clipboard.getText()?.text?.let {
                        text = it.trim()
                        error = null
                    }
                }) {
                    Icon(AppIcons.Paste, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.action_paste))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val result = onLoad(text)) {
                    null -> onDismiss()
                    ChessController.FenError.Malformed -> error = Res.string.fen_error_malformed
                    is ChessController.FenError.Invalid -> error = result.problem.label()
                }
            }) { Text(stringResource(Res.string.action_load)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
fun AboutDialog(versionName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) } },
        title = null,
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(Res.drawable.zc_emblem), contentDescription = null, modifier = Modifier.width(110.dp))
                Spacer(Modifier.height(14.dp))
                AppWordmark(fontSize = 13.sp, showMaker = true)
                Spacer(Modifier.height(8.dp))
                BrandUnderline(Modifier.width(200.dp).height(2.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.about_author),
                    style = MaterialTheme.typography.titleSmall,
                    color = ZorixColors.RedBright,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.about_version, versionName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(Res.string.about_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(Res.string.about_credits),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                for (line in listOf(Res.string.about_engine, Res.string.about_voice, Res.string.about_pieces, Res.string.about_font)) {
                    Text(
                        "• " + stringResource(line),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.about_rights), style = MaterialTheme.typography.labelMedium)
            }
        },
    )
}
