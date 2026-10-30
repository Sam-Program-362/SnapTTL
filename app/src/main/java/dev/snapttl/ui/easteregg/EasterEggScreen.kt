package dev.snapttl.ui.easteregg

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.snapttl.R
import dev.snapttl.domain.MiniGame
import dev.snapttl.domain.MiniGameState
import kotlinx.coroutines.delay

private const val GRID = 9
private const val ROUND_MS = 30_000L
private const val TARGET_MS = 700L

@Composable
fun EasterEggScreen(onBack: () -> Unit) {
    var state by remember { mutableStateOf(MiniGameState.READY) }
    var score by remember { mutableStateOf(0) }
    var active by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state) {
        if (state != MiniGameState.PLAYING) return@LaunchedEffect
        val end = System.currentTimeMillis() + ROUND_MS
        while (System.currentTimeMillis() < end && state == MiniGameState.PLAYING) {
            active = (0 until GRID).random()
            delay(TARGET_MS)
        }
        active = null
        state = MiniGameState.FINISHED
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.easter_egg_title), style = MaterialTheme.typography.titleLarge)
            Text(
                "Tap the screenshots before they escape the shredder!",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    MiniGameState.READY -> {
                        Button(onClick = {
                            state = MiniGameState.PLAYING
                            score = 0
                            active = null
                        }) { Text(stringResource(R.string.easter_egg_start)) }
                    }

                    MiniGameState.PLAYING -> {
                        Text(stringResource(R.string.easter_egg_finish_score, score), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                            items(GRID) { index ->
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(64.dp)
                                        .background(
                                            if (index == active) Color(0xFFFF4500)
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            if (index == active) {
                                                score++
                                                active = null
                                            }
                                        }
                                )
                            }
                        }
                    }

                    MiniGameState.FINISHED -> {
                        Text(MiniGame.titleFor(score), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.easter_egg_finish_score, score), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (state == MiniGameState.FINISHED) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    state = MiniGameState.READY
                    score = 0
                    active = null
                }) { Text(stringResource(R.string.easter_egg_play_again)) }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
    }
}
