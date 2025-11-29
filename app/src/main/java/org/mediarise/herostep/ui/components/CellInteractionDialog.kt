package org.mediarise.herostep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mediarise.herostep.data.model.HexCell

@Composable
fun CellInteractionDialog(
    cell: HexCell,
    onDismiss: () -> Unit,
    onAttackMob: () -> Unit,
    onEnterTavern: () -> Unit,
    onMove: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1a1a2e)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Cell actions",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "Terrain: ${cell.type.displayName}",
                    fontSize = 16.sp,
                    color = Color(0xFFa8a8a8),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (cell.hasMob && cell.mob != null) {
                    Text(
                        text = "Mob: ${cell.mob!!.name}",
                        fontSize = 16.sp,
                        color = Color(0xFFd63031),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = {
                            onAttackMob()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFd63031)
                        )
                    ) {
                        Text("Attack mob")
                    }
                }
                
                if (cell.hasTavern) {
                    Text(
                        text = "Tavern available",
                        fontSize = 16.sp,
                        color = Color(0xFFf5a623),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = {
                            onEnterTavern()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFf5a623)
                        )
                    ) {
                        Text("Enter tavern")
                    }
                }
                
                if (!cell.isOccupied() && !cell.hasMob && !cell.hasTavern) {
                    Button(
                        onClick = {
                            onMove()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Move here")
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

