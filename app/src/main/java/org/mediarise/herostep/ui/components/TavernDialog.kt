package org.mediarise.herostep.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mediarise.herostep.data.model.Hero
import org.mediarise.herostep.data.model.Unit as GameUnit

@Composable
fun TavernDialog(
    hero: Hero,
    availableUnits: List<GameUnit>,
    onDismiss: () -> Unit,
    onHireUnit: (GameUnit) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1a1a2e)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Tavern",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Gold: ${hero.gold}",
                    fontSize = 18.sp,
                    color = Color(0xFFf5a623),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableUnits) { unit ->
                        UnitCard(
                            unit = unit,
                            canAfford = hero.gold >= unit.cost,
                            onHire = {
                                onHireUnit(unit)
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun UnitCard(
    unit: GameUnit,
    canAfford: Boolean,
    onHire: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (canAfford) Color(0xFF16213e) else Color(0xFF2a2a2a)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = unit.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = unit.race.displayName,
                    fontSize = 14.sp,
                    color = Color(0xFFa8a8a8)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ATK: ${unit.attack} | DEF: ${unit.defense} | HP: ${unit.health}",
                    fontSize = 12.sp,
                    color = Color(0xFFa8a8a8)
                )
            }
            
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.End
            ) {
                Text(
                    text = "${unit.cost} Gold",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFf5a623),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = onHire,
                    enabled = canAfford,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFe94560)
                    )
                ) {
                    Text("Hire")
                }
            }
        }
    }
}

