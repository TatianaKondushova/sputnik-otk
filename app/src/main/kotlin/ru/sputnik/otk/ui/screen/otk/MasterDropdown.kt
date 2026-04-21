package ru.sputnik.otk.ui.screen.otk

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ru.sputnik.otk.ui.theme.SputnikOtkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterDropdown(
    selected: String?,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected ?: "— Выбери мастера —",
            onValueChange = {},
            readOnly = true,
            label = { Text("Мастер") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun MasterDropdownEmptyPreview() {
    SputnikOtkTheme {
        MasterDropdown(selected = null, options = OtkUiState.DEFAULT_MASTERS, onSelected = {})
    }
}

@Preview
@Composable
private fun MasterDropdownSelectedPreview() {
    SputnikOtkTheme {
        MasterDropdown(selected = "Руслан", options = OtkUiState.DEFAULT_MASTERS, onSelected = {})
    }
}
