package org.mediarise.herostep.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mediarise.herostep.data.model.Hero

@Composable
fun HeroInfoPanel(
    hero: Hero,
    isSelected: Boolean = false,
    onSelectHero: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = hero.name,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color(0xFF4a90e2) else Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        if (onSelectHero != null && hero.canMove()) {
            Button(
                onClick = onSelectHero,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFF4a90e2) else Color(0xFF2a2a3e)
                )
            ) {
                Text(
                    text = if (isSelected) "Selected - Click to deselect" else "Select for movement",
                    fontSize = 14.sp
                )
            }
        }
        
        Text(
            text = hero.race.displayName,
            fontSize = 16.sp,
            color = Color(0xFFa8a8a8),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Divider(color = Color(0xFF444444), modifier = Modifier.padding(vertical = 8.dp))
        
        InfoRow("Level", hero.level.toString())
        InfoRow("Experience", "${hero.experience}/${hero.level * 100}")
        InfoRow("Gold", hero.gold.toString())
        
        Divider(color = Color(0xFF444444), modifier = Modifier.padding(vertical = 8.dp))
        
        Text(
            text = "Stats",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        InfoRow("Health", "${hero.health}/${hero.maxHealth}")
        InfoRow("Attack", hero.attack.toString())
        InfoRow("Defense", hero.defense.toString())
        InfoRow("Movement", "${hero.movementPoints}/${hero.maxMovementPoints}")
        
        Divider(color = Color(0xFF444444), modifier = Modifier.padding(vertical = 8.dp))
        
        if (hero.skills.isNotEmpty()) {
            Text(
                text = "Skills",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            hero.skills.forEach { skill ->
                Text(
                    text = skill.name,
                    fontSize = 14.sp,
                    color = Color(0xFFa8a8a8),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
        
        Divider(color = Color(0xFF444444), modifier = Modifier.padding(vertical = 8.dp))
        
        if (hero.inventory.isNotEmpty()) {
            Text(
                text = "Inventory",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            hero.inventory.forEach { item ->
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    color = Color(0xFFa8a8a8),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFFa8a8a8)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

