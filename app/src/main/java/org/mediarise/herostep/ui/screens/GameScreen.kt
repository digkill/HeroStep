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
    profession: Profession = Profession.WARRIOR,
    heroName: String = "Hero"
) {
    val gameManager = remember { GameManager() }
    var gameState by remember { mutableStateOf<GameState?>(null) }

    LaunchedEffect(race, profession, heroName) {
        gameState = withContext(Dispatchers.Default) {
            gameManager.startNewGame(race, heroName, profession)
        }
    }

    var selectedCell by remember { mutableStateOf<HexCell?>(null) }
    var selectedHero by remember { mutableStateOf<Hero?>(null) }
    var reachableCells by remember { mutableStateOf<Set<HexCell>>(emptySet()) }
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
                    reachableCells = reachableCells,
                    selectedHero = selectedHero,
                    onCellClick = { cell ->
                        try {
                            // Если герой выбран и ячейка доступна для перемещения
                            if (selectedHero != null && reachableCells.contains(cell)) {
                                if (gameManager.moveHero(selectedHero!!, cell)) {
                                    gameState = gameManager.getCurrentGameState() ?: currentState
                                    // Очищаем выбор после перемещения
                                    selectedHero = null
                                    reachableCells = emptySet()
                                }
                            } else if (cell.hero != null && cell.hero == currentState.playerHero) {
                                // Выбираем героя игрока
                                val hero = cell.hero!!
                                selectedHero = hero
                                try {
                                    reachableCells = gameManager.getReachableCells(hero)
                                } catch (e: Exception) {
                                    android.util.Log.e("GameScreen", "Error getting reachable cells: ${e.message}", e)
                                    e.printStackTrace()
                                    selectedHero = null
                                    reachableCells = emptySet()
                                }
                            } else {
                                // Обычный клик на ячейку
                                selectedCell = cell
                                showCellDialog = true
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GameScreen", "Error handling cell click: ${e.message}", e)
                            e.printStackTrace()
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF1a1a2e))
            ) {
                HeroInfoPanel(
                    hero = currentState.playerHero,
                    isSelected = selectedHero == currentState.playerHero,
                    onSelectHero = {
                        try {
                            if (selectedHero == currentState.playerHero) {
                                // Снимаем выбор
                                selectedHero = null
                                reachableCells = emptySet()
                            } else {
                                // Выбираем героя
                                val hero = currentState.playerHero
                                if (hero.currentCell != null && hero.canMove()) {
                                    selectedHero = hero
                                    // Вычисляем доступные ячейки синхронно (быстро для небольшой карты)
                                    // Если будет медленно, можно вернуть асинхронный вариант
                                    try {
                                        reachableCells = gameManager.getReachableCells(hero)
                                    } catch (e: Exception) {
                                        android.util.Log.e("GameScreen", "Error getting reachable cells: ${e.message}", e)
                                        e.printStackTrace()
                                        selectedHero = null
                                        reachableCells = emptySet()
                                    }
                                } else {
                                    android.util.Log.w("GameScreen", "Hero cannot be selected: currentCell=${hero.currentCell}, canMove=${hero.canMove()}")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GameScreen", "Error in onSelectHero: ${e.message}", e)
                            e.printStackTrace()
                            selectedHero = null
                            reachableCells = emptySet()
                        }
                    }
                )
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

