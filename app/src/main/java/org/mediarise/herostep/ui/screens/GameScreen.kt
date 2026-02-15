package org.mediarise.herostep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.mediarise.herostep.data.model.*
import org.mediarise.herostep.data.model.Unit as GameUnit
import org.mediarise.herostep.game.GameManager
import org.mediarise.herostep.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun GameScreen(
    race: Race = Race.HUMANS,
    profession: Profession = Profession.WARRIOR,
    heroName: String = "Hero"
) {
    val gameManager = remember { GameManager() }
    var gameState by remember { mutableStateOf<GameState?>(null) }
    var uiRefreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(race, profession, heroName) {
        gameState = withContext(Dispatchers.Default) {
            gameManager.startNewGame(race, heroName, profession)
        }
        uiRefreshTick++
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
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFe94560))
        }
        return
    }

    val currentState = gameState!!
    @Suppress("UNUSED_VARIABLE")
    val forceRecompose = uiRefreshTick
    val canPlayerAct = currentState.isPlayerTurn && currentState.hasRolledDice

    val refreshState: () -> Unit = {
        gameManager.getCurrentGameState()?.let { updatedState ->
            gameState = updatedState
            uiRefreshTick++
        }
    }

    val availableUnits = remember(currentState.playerHero.race) {
        listOf(
            GameUnit("unit_1", "Swordsman", currentState.playerHero.race, 50, 50, 8, 4, 2, 100),
            GameUnit("unit_2", "Archer", currentState.playerHero.race, 40, 40, 6, 3, 3, 120),
            GameUnit("unit_3", "Mage", currentState.playerHero.race, 30, 30, 10, 2, 2, 150)
        )
    }

    LaunchedEffect(currentState.currentHeroIndex, currentState.currentTurn) {
        selectedHero = null
        reachableCells = emptySet()
        selectedCell = null
        showCellDialog = false
        showTavernDialog = false
    }

    LaunchedEffect(
        currentState.currentHeroIndex,
        currentState.currentTurn,
        currentState.hasRolledDice,
        currentState.playerHero.movementPoints,
        currentState.playerHero.currentCell?.x,
        currentState.playerHero.currentCell?.y
    ) {
        if (currentState.isPlayerTurn && currentState.hasRolledDice) {
            val hero = currentState.playerHero
            if (hero.currentCell != null && hero.canMove()) {
                selectedHero = hero
                reachableCells = gameManager.getReachableCells(hero)
                    .filter { it != hero.currentCell }
                    .toSet()
            } else {
                selectedHero = null
                reachableCells = emptySet()
            }
        } else if (!currentState.isPlayerTurn) {
            selectedHero = null
            reachableCells = emptySet()
        }
    }

    LaunchedEffect(currentState.currentHeroIndex, currentState.hasRolledDice, currentState.currentTurn) {
        if (currentState.isPlayerTurn) return@LaunchedEffect

        if (!currentState.hasRolledDice) {
            gameManager.rollDiceForCurrentTurn()
            refreshState()
            return@LaunchedEffect
        }

        delay(600)
        gameManager.performCurrentAiTurn()
        refreshState()
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
                            if (selectedHero != null && reachableCells.contains(cell) && canPlayerAct) {
                                if (gameManager.moveHero(selectedHero!!, cell)) {
                                    selectedHero = null
                                    reachableCells = emptySet()
                                    refreshState()
                                }
                            } else if (
                                cell.hero?.id == currentState.playerHero.id &&
                                canPlayerAct
                            ) {
                                val hero = cell.hero!!
                                selectedHero = hero
                                reachableCells = gameManager.getReachableCells(hero)
                                    .filter { it != hero.currentCell }
                                    .toSet()
                            } else {
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
                Column(modifier = Modifier.fillMaxSize()) {
                    TurnControlPanel(
                        gameState = currentState,
                        onRollDice = {
                            if (currentState.isPlayerTurn) {
                                gameManager.rollDiceForCurrentTurn()
                                refreshState()
                            }
                        },
                        onEndTurn = {
                            if (currentState.isPlayerTurn && currentState.hasRolledDice) {
                                gameManager.endCurrentTurn()
                                refreshState()
                            }
                        }
                    )

                    HeroInfoPanel(
                        hero = currentState.playerHero,
                        isSelected = selectedHero == currentState.playerHero,
                        onSelectHero = if (canPlayerAct) {
                            {
                                try {
                                    if (selectedHero == currentState.playerHero) {
                                        selectedHero = null
                                        reachableCells = emptySet()
                                    } else {
                                        val hero = currentState.playerHero
                                        if (hero.currentCell != null && hero.canMove()) {
                                            selectedHero = hero
                                            reachableCells = gameManager.getReachableCells(hero)
                                                .filter { it != hero.currentCell }
                                                .toSet()
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("GameScreen", "Error in onSelectHero: ${e.message}", e)
                                    e.printStackTrace()
                                    selectedHero = null
                                    reachableCells = emptySet()
                                }
                            }
                        } else {
                            null
                        }
                    )
                }
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
                    if (canPlayerAct && cell.mob != null) {
                        gameManager.attackMob(currentState.playerHero, cell.mob!!)
                        refreshState()
                    }
                },
                onEnterTavern = {
                    if (canPlayerAct) {
                        showTavernDialog = true
                    }
                },
                onMove = {
                    val cell = selectedCell!!
                    if (canPlayerAct && gameManager.moveHero(currentState.playerHero, cell)) {
                        refreshState()
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
                    if (canPlayerAct && gameManager.hireUnit(currentState.playerHero, unit)) {
                        refreshState()
                    }
                }
            )
        }
    }
}

@Composable
private fun TurnControlPanel(
    gameState: GameState,
    onRollDice: () -> Unit,
    onEndTurn: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF23233a))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Раунд ${gameState.currentTurn}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "Ход: ${gameState.currentHero.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFd6d6d6)
            )

            val diceText = gameState.currentDiceRoll?.let { roll ->
                "${roll.dieOne} + ${roll.dieTwo} = ${roll.total}"
            } ?: "Кубики не брошены"

            Text(
                text = "2d6: $diceText",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFf5a623)
            )

            if (gameState.isPlayerTurn) {
                Button(
                    onClick = onRollDice,
                    enabled = !gameState.hasRolledDice,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Бросить 2 кубика")
                }
                Button(
                    onClick = onEndTurn,
                    enabled = gameState.hasRolledDice,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4a90e2))
                ) {
                    Text("Завершить ход")
                }
            } else {
                Text(
                    text = "Ходит AI...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFa8a8a8)
                )
            }
        }
    }
}