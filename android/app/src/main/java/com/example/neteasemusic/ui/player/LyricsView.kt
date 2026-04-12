package com.example.neteasemusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.neteasemusic.data.model.LyricLine

/**
 * A scrollable lyrics view that highlights the currently active lyric line
 * and auto-scrolls to keep it visible.
 *
 * @param lyrics Parsed lyric lines including timed lines and any untimed header lines.
 * @param activeIndex Index of the currently active (highlighted) line.
 *                    Pass -1 when no line should be highlighted.
 */
@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    activeIndex: Int,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无歌词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        return
    }

    // Only scroll through timed lyrics; untimed lines stay at the top.
    val timedLyrics = remember(lyrics) { lyrics.filter { it.timeMs >= 0 } }
    val untimedLyrics = remember(lyrics) { lyrics.filter { it.timeMs < 0 } }

    val allLines = untimedLyrics + timedLyrics
    val adjustedActiveIndex = when {
        activeIndex < 0 -> -1
        else -> untimedLyrics.size + activeIndex
    }

    val listState = remember { LazyListState() }

    // Auto-scroll to keep the active lyric line centered.
    LaunchedEffect(adjustedActiveIndex) {
        if (adjustedActiveIndex >= 0 && adjustedActiveIndex < allLines.size) {
            listState.animateScrollToItem(
                index = adjustedActiveIndex.coerceAtLeast(0),
                scrollOffset = -200
            )
        }
    }

    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(allLines, key = { index, _ -> index }) { index, line ->
            val isActive = index == adjustedActiveIndex
            LyricLineItem(text = line.text, isActive = isActive)
        }
    }
}

@Composable
private fun LyricLineItem(text: String, isActive: Boolean) {
    val textColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }

    val style = if (isActive) {
        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    } else {
        MaterialTheme.typography.bodyMedium
    }

    Text(
        text = text,
        style = style,
        color = textColor,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                ) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
