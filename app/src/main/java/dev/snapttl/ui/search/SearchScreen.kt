package dev.snapttl.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.snapttl.ui.LocalSnapTtlFactory

@Composable
fun SearchScreen() {
    val viewModel: SearchViewModel = viewModel(factory = LocalSnapTtlFactory.current)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Search", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            TextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Text or labels indexed on device") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::indexAll) {
                    Text(if (state.indexing) "Indexing…" else "Index screenshots")
                }
                if (state.indexing) {
                    Text("Indexing screenshots…", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (state.query.isBlank() || state.results.isEmpty()) {
                Text("No matches.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.results, key = { it.screenshotId }) { item ->
                        Card {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Screenshot #${item.screenshotId}", style = MaterialTheme.typography.bodyMedium)
                                if (item.extractedText.isNotBlank()) {
                                    Text(item.extractedText, style = MaterialTheme.typography.bodySmall)
                                }
                                if (item.labels.isNotBlank()) {
                                    Text("Labels: ${item.labels}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
