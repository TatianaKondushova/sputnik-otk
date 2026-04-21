package ru.sputnik.otk.ui.screen.otk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.sputnik.otk.data.Panel
import ru.sputnik.otk.ui.theme.SputnikOtkTheme

@Composable
fun PanelList(
    panels: List<Panel>,
    modifier: Modifier = Modifier,
) {
    if (panels.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Пока пусто. Добавь первую панель.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(panels, key = { it.id }) { panel ->
            Text(
                text = panel.id,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
        }
    }
}

@Preview
@Composable
private fun PanelListEmptyPreview() {
    SputnikOtkTheme { PanelList(panels = emptyList()) }
}

@Preview
@Composable
private fun PanelListFilledPreview() {
    SputnikOtkTheme {
        PanelList(panels = listOf(
            Panel("04:AB:CD"),
            Panel("04:EF:12"),
            Panel("04:34:56"),
        ))
    }
}
