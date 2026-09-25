package com.feiqi.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.feiqi.R
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.GlassStrength

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = stringResource(R.string.confirm),
    dismissText: String = stringResource(R.string.cancel),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // 液态玻璃弹窗（DESIGN.md §4）：厚档半透明 + xl 圆角 + 高光边；透出的底色是被压暗的页面内容
    val shape = RoundedCornerShape(FeiQiRadius.xl)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.glassEdge(shape, GlassStrength.Thick),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = GlassStrength.Thick.fillAlpha),
        title = { Text(title) },
        text = { Text(text, color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        }
    )
}

/** 单行文本编辑弹窗：用于长按重命名待办 / 清单，或往清单里追加一条。 */
@Composable
fun TextEditDialog(
    title: String,
    initialText: String,
    label: String = "",
    confirmText: String = stringResource(R.string.save),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    val shape = RoundedCornerShape(FeiQiRadius.xl)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.glassEdge(shape, GlassStrength.Thick),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = GlassStrength.Thick.fillAlpha),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = if (label.isBlank()) null else ({ Text(label) }),
                singleLine = true,
                modifier = Modifier,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank()) onConfirm(text.trim())
                })
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Text(confirmText, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** 纯提示弹窗（只有一个「知道了」按钮）。 */
@Composable
fun InfoDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(FeiQiRadius.xl)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.glassEdge(shape, GlassStrength.Thick),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = GlassStrength.Thick.fillAlpha),
        title = { Text(title) },
        text = { Text(text, color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.got_it), color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

/**
 * 全局删除二次确认的状态持有者。
 * 调用方在屏幕顶层渲染 [DeleteConfirmHost]，并在任意删除按钮的 onClick 里调用
 * [DeleteConfirmState.request]，确认对话框才会弹出，确认后执行 [onConfirm]。
 */
data class DeleteRequest(val message: String, val onConfirm: () -> Unit)

class DeleteConfirmState {
    var pending by mutableStateOf<DeleteRequest?>(null)
        private set

    fun request(message: String, onConfirm: () -> Unit) {
        pending = DeleteRequest(message, onConfirm)
    }

    fun dismiss() {
        pending = null
    }
}

@Composable
fun rememberDeleteConfirm(): DeleteConfirmState = remember { DeleteConfirmState() }

@Composable
fun DeleteConfirmHost(state: DeleteConfirmState) {
    state.pending?.let { req ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_title),
            text = req.message,
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                req.onConfirm()
                state.dismiss()
            },
            onDismiss = { state.dismiss() }
        )
    }
}
