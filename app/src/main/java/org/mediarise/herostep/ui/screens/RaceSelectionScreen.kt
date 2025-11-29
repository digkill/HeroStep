package org.mediarise.herostep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import org.mediarise.herostep.data.model.Profession
import org.mediarise.herostep.data.model.Race

@Composable
fun RaceSelectionScreen(navController: NavController) {
    var selectedRace by remember { mutableStateOf<Race?>(null) }
    var selectedProfession by remember { mutableStateOf<Profession?>(null) }
    var heroName by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Choose your race",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFe94560),
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(Race.values().toList()) { race ->
                    RaceCard(
                        race = race,
                        isSelected = selectedRace == race,
                        onClick = { selectedRace = race }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Choose your profession",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFe94560),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                items(Profession.values().toList()) { profession ->
                    ProfessionCard(
                        profession = profession,
                        isSelected = selectedProfession == profession,
                        onClick = { selectedProfession = profession }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = heroName,
                onValueChange = { heroName = it },
                label = { Text("Hero name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFe94560),
                    unfocusedBorderColor = Color.Gray
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (selectedRace != null && selectedProfession != null && heroName.isNotBlank()) {
                        navController.navigate("game/${selectedRace!!.name}/${selectedProfession!!.name}/$heroName") {
                            popUpTo("race_selection") { inclusive = true }
                        }
                    }
                },
                enabled = selectedRace != null && selectedProfession != null && heroName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Begin adventure",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RaceCard(
    race: Race,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFe94560) else Color(0xFF16213e)
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, Color.White)
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = race.displayName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = race.description,
                fontSize = 12.sp,
                color = Color(0xFFa8a8a8)
            )
        }
    }
}

@Composable
fun ProfessionCard(
    profession: Profession,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFe94560) else Color(0xFF16213e)
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, Color.White)
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = profession.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

