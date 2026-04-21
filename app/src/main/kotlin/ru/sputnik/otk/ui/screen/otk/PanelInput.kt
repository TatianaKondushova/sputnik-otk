package ru.sputnik.otk.ui.screen.otk

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.sputnik.otk.ui.theme.SputnikOtkTheme

@Composable
fun PanelInput(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            label = { Text("Номер панели") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onAdd,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить панель")
        }
    }
}

@Preview
@Composable
private fun PanelInputEnabledPreview() {
    SputnikOtkTheme {
        PanelInput(value = "04:AB", enabled = true, onValueChange = {}, onAdd = {})
    }
}

@Preview
@Composable
private fun PanelInputDisabledPreview() {
    SputnikOtkTheme {
        PanelInput(value = "", enabled = false, onValueChange = {}, onAdd = {})
    }
}
