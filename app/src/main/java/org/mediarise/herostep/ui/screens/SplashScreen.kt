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
import org.mediarise.herostep.utils.DebugConfig
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    LaunchedEffect(Unit) {
        // В режиме отладки пропускаем все экраны
        if (DebugConfig.IsDebug) {
            navController.navigate("${org.mediarise.herostep.ui.navigation.Screen.Game.route}/${DebugConfig.DEBUG_RACE.name}/${DebugConfig.DEBUG_PROFESSION.name}/${DebugConfig.DEBUG_HERO_NAME}") {
                popUpTo(0) { inclusive = true }
            }
        } else {
            delay(2000) // Short splash before intro video.
            navController.navigate("intro_video") {
                popUpTo("splash") { inclusive = true }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "HeroStep",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFe94560),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Preparing your adventure...",
                fontSize = 20.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

