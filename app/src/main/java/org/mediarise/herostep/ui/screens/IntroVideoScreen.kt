package org.mediarise.herostep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
fun IntroVideoScreen(navController: NavController) {
    LaunchedEffect(Unit) {
        delay(5000) // Simulate intro playback.
        navController.navigate("main_menu") {
            popUpTo("intro_video") { inclusive = true }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0f0f1e)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "A forgotten realm calls for new heroes...",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFe94560),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Gather your allies and claim your destiny.",
                fontSize = 18.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "The march begins soon.",
                fontSize = 18.sp,
                color = Color(0xFFa8a8a8),
                textAlign = TextAlign.Center
            )
        }
    }
}

