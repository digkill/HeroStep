package org.mediarise.herostep.ui.components

import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import org.mediarise.herostep.data.model.GameState
import org.mediarise.herostep.graphics.HexGrid3DRenderer
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HexGrid3DView(
    gameState: GameState,
    onCellClick: (org.mediarise.herostep.data.model.HexCell) -> Unit,
    reachableCells: Set<org.mediarise.herostep.data.model.HexCell> = emptySet(),
    selectedHero: org.mediarise.herostep.data.model.Hero? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
    var rendererRef by remember { mutableStateOf<HexGrid3DRenderer?>(null) }
    var isViewReady by remember { mutableStateOf(false) }
    
    var lastTouchX by remember { mutableStateOf(0f) }
    var lastTouchY by remember { mutableStateOf(0f) }
    var touchStartX by remember { mutableStateOf(0f) }
    var touchStartY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var cameraAngle by remember { mutableStateOf(Math.PI / 4.0) } // 45 градусов для изометрического вида
    var cameraRadius by remember { mutableStateOf(20f) } // Увеличено для правильного обзора
    val tapSlop = 16f

    // Создаем рендерер один раз
    val renderer = remember(gameState.board) {
        HexGrid3DRenderer(gameState.board, gameState, { cell ->
            onCellClick(cell)
        }, context)
    }
    rendererRef = renderer
    
    // Обновляем доступные ячейки в рендерере
    LaunchedEffect(reachableCells, selectedHero) {
        try {
            // Обновляем состояние рендерера напрямую (это безопасно, так как мы только устанавливаем переменные)
            renderer.setReachableCells(reachableCells, selectedHero)
            // Запрашиваем перерисовку, если view готов
            if (isViewReady) {
                glSurfaceViewRef?.post {
                    try {
                        glSurfaceViewRef?.requestRender()
                    } catch (e: Exception) {
                        android.util.Log.e("HexGrid3DView", "Error requesting render: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HexGrid3DView", "Error setting reachable cells: ${e.message}", e)
            e.printStackTrace()
        }
    }

    AndroidView(
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                
                // ВАЖНО: Сначала устанавливаем рендерер, это создаст GLThread
                setRenderer(renderer)
                
                // Сохраняем ссылку на view
                glSurfaceViewRef = this
                
                // Устанавливаем renderMode ПОСЛЕ того, как view будет прикреплен к window
                // Используем post для безопасной установки после инициализации
                post {
                    try {
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        requestRender()
                        isViewReady = true
                    } catch (e: Exception) {
                        android.util.Log.e("HexGrid3D", "Error setting render mode: ${e.message}", e)
                        e.printStackTrace()
                    }
                }

                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastTouchX = event.x
                            lastTouchY = event.y
                            touchStartX = event.x
                            touchStartY = event.y
                            isDragging = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.x - lastTouchX
                            val dy = event.y - lastTouchY

                            if (!isDragging) {
                                val totalDx = event.x - touchStartX
                                val totalDy = event.y - touchStartY
                                if (kotlin.math.abs(totalDx) > tapSlop || kotlin.math.abs(totalDy) > tapSlop) {
                                    isDragging = true
                                }
                            }

                            if (isDragging) {
                                cameraAngle += dx * 0.01
                                cameraRadius = (cameraRadius - dy * 0.1f).coerceIn(10f, 30f) // Границы для зума

                                val cameraX = (cameraRadius * cos(cameraAngle)).toFloat()
                                val cameraZ = (cameraRadius * sin(cameraAngle)).toFloat()
                                val cameraY = cameraRadius * 0.7f // Высота пропорциональна расстоянию

                                renderer.updateCamera(cameraX, cameraY, cameraZ)
                                renderer.updateLookAt(0f, 0f, 0f)

                                // Безопасный вызов requestRender
                                val glView = glSurfaceViewRef
                                glView?.post {
                                    try {
                                        glView.requestRender()
                                    } catch (e: Exception) {
                                        android.util.Log.e("HexGrid3DView", "Error requesting render: ${e.message}", e)
                                    }
                                }
                            }

                            lastTouchX = event.x
                            lastTouchY = event.y
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!isDragging) {
                                try {
                                    renderer.handleTap(event.x, event.y, view.width, view.height)
                                } catch (e: Exception) {
                                    android.util.Log.e("HexGrid3DView", "Error handling tap: ${e.message}", e)
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            isDragging = false
                            true
                        }
                        else -> false
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            // Обновляем ссылку на view
            glSurfaceViewRef = view
            
            // Управляем режимом рендеринга только после готовности view
            if (isViewReady) {
                val targetMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                
                // Безопасно устанавливаем режим рендеринга
                view.post {
                    try {
                        if (view.renderMode != targetMode) {
                            view.renderMode = targetMode
                            view.requestRender()
                        } else {
                            view.requestRender()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HexGrid3D", "Error updating render mode: ${e.message}", e)
                    }
                }
            }
        }
    )
    
    // Управление жизненным циклом GLSurfaceView
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle = lifecycleOwner.lifecycle
    
    DisposableEffect(lifecycle) {
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        glSurfaceViewRef?.onPause()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        glSurfaceViewRef?.onResume()
                    }
                    else -> {}
                }
            }
        }
        lifecycle.addObserver(observer)
        
        onDispose {
            lifecycle.removeObserver(observer)
            glSurfaceViewRef?.onPause()
            glSurfaceViewRef = null
            rendererRef = null
        }
    }
}
