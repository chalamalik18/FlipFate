package com.flipfate.lbsroc.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EntryScreen(
    settings: EntrySettings,
    openBrowser: (String) -> Unit,
    openGame: () -> Unit
) {
    androidx.activity.compose.BackHandler {}
    val context = LocalContext.current
    val service = remember { EntryService(context.applicationContext) }
    var unavailable by remember { mutableStateOf(false) }

    var retryIndex by remember { mutableStateOf(0) }

    LaunchedEffect(retryIndex) {
        unavailable = false
        val savedUrl = settings.savedPage()
        if (savedUrl != null) {
            openBrowser(savedUrl)
            return@LaunchedEffect
        }

        val reply = service.fetchEntryPayload()

        if (reply.isNullOrBlank()) {
            unavailable = true
            return@LaunchedEffect
        }

        val destination = browserDestination(reply)
        if (destination != null) {
            settings.savePage(destination)
            openBrowser(destination.url)
            return@LaunchedEffect
        }

        openGame()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (unavailable) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Unable to reach the service",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Check your connection and try again. The service may be temporarily unavailable.",
                    color = Color(0xFFB8B8B8),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { retryIndex += 1 }) {
                    Text("Try again")
                }
            }
        } else {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
