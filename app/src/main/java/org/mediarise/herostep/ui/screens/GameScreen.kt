package org.mediarise.herostep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.mediarise.herostep.data.model.*
import org.mediarise.herostep.data.model.Unit as GameUnit
import org.mediarise.herostep.game.GameManager
import org.mediarise.herostep.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GameScreen(
    race: Race = Race.HUMANS,
    heroName: String = "Hero"
) {
    val gameManager = remember { GameManager() }
    var gameState by remember { mutableStateOf<GameState?>(null) }

    LaunchedEffect(race, heroName) {
        gameState = withContext(Dispatchers.Default) {
            gameManager.startNewGame(race, heroName)
        }
    }

    var selectedCell by remember { mutableStateOf<HexCell?>(null) }
    var showTavernDialog by remember { mutableStateOf(false) }
    var showCellDialog by remember { mutableStateOf(false) }

    if (gameState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0f0f1e)),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFe94560))
        }
        return
    }

    val currentState = gameState!!

    val availableUnits = remember(currentState.playerHero.race) {
        listOf(
            GameUnit("unit_1", "Swordsman", currentState.playerHero.race, 50, 50, 8, 4, 2, 100),
            GameUnit("unit_2", "Archer", currentState.playerHero.race, 40, 40, 6, 3, 3, 120),
            GameUnit("unit_3", "Mage", currentState.playerHero.race, 30, 30, 10, 2, 2, 150)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0f0f1e))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight()
            ) {
                HexGrid3DView(
                    gameState = currentState,
                    onCellClick = { cell ->
                        selectedCell = cell
                        showCellDialog = true
                    }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF1a1a2e))
            ) {
                HeroInfoPanel(hero = currentState.playerHero)
            }
        }

        if (showCellDialog && selectedCell != null) {
            CellInteractionDialog(
                cell = selectedCell!!,
                onDismiss = {
                    showCellDialog = false
                    selectedCell = null
                },
                onAttackMob = {
                    val cell = selectedCell!!
                    if (cell.mob != null) {
                        gameManager.attackMob(currentState.playerHero, cell.mob!!)
                        gameState = gameManager.getCurrentGameState() ?: currentState
                    }
                },
                onEnterTavern = {
                    showTavernDialog = true
                },
                onMove = {
                    val cell = selectedCell!!
                    if (gameManager.moveHero(currentState.playerHero, cell)) {
                        gameState = gameManager.getCurrentGameState() ?: currentState
                    }
                }
            )
        }

        if (showTavernDialog) {
            TavernDialog(
                hero = currentState.playerHero,
                availableUnits = availableUnits,
                onDismiss = {
                    showTavernDialog = false
                },
                onHireUnit = { unit ->
                    if (gameManager.hireUnit(currentState.playerHero, unit)) {
                        gameState = gameManager.getCurrentGameState() ?: currentState
                    }
                }
            )
        }
    }
}

