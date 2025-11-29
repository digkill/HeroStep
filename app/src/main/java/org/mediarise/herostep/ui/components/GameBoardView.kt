package org.mediarise.herostep.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mediarise.herostep.data.model.*

@Composable
fun GameBoardView(
    gameState: org.mediarise.herostep.data.model.GameState,
    onCellClick: (HexCell) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0a0a1a))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            items(8) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(8) { col ->
                        val cell = gameState.board.getCell(col, row)
                        if (cell != null) {
                            HexCellView(
                                cell = cell,
                                onClick = { onCellClick(cell) },
                                modifier = Modifier
                                    .size(60.dp)
                                    .padding(2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HexCellView(
    cell: HexCell,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cellColor = when (cell.type) {
        HexCellType.FOREST -> Color(0xFF2d5016)
        HexCellType.PLAINS -> Color(0xFF8b9a46)
        HexCellType.MOUNTAINS -> Color(0xFF6b6b6b)
        HexCellType.LAKE -> Color(0xFF1e3a5f)
        HexCellType.WASTELAND -> Color(0xFF8b7355)
        HexCellType.CORRUPTED_LAND -> Color(0xFF5a1a1a)
    }
    
    val borderColor = when {
        cell.hero != null -> Color(0xFFe94560)
        cell.unit != null -> Color(0xFF4a90e2)
        cell.hasTavern -> Color(0xFFf5a623)
        cell.hasMob -> Color(0xFFd63031)
        else -> Color(0xFF444444)
    }
    
    Box(
        modifier = modifier.clickable { onClick() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawHexagon(cellColor, borderColor)
        }
        
        // Индикаторы
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            if (cell.hasTavern) {
                Text("T", color = Color.White, fontSize = 10.dp.value.sp)
            }
            if (cell.hasMob) {
                Text("M", color = Color.White, fontSize = 10.dp.value.sp)
            }
            if (cell.hero != null) {
                Text("H", color = Color.White, fontSize = 10.dp.value.sp)
            }
        }
    }
}

private fun DrawScope.drawHexagon(fillColor: Color, borderColor: Color) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = size.minDimension / 2 - 2
    
    val path = Path().apply {
        for (i in 0..5) {
            val angle = Math.PI / 3 * i - Math.PI / 6
            val x = center.x + radius * kotlin.math.cos(angle).toFloat()
            val y = center.y + radius * kotlin.math.sin(angle).toFloat()
            if (i == 0) {
                moveTo(x, y)
            } else {
                lineTo(x, y)
            }
        }
        close()
    }
    
    drawPath(path, fillColor)
    drawPath(path, borderColor, style = Stroke(width = 2f))
}

