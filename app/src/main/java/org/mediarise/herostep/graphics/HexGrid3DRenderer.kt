package org.mediarise.herostep.graphics

import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import org.mediarise.herostep.data.model.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random

class HexGrid3DRenderer(
    private val gameBoard: GameBoard,
    private val gameState: GameState? = null,
    private val onCellSelected: (HexCell) -> Unit,
    private val context: android.content.Context? = null
) : GLSurfaceView.Renderer {

    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private var cameraX = 0f
    private var cameraY = 15f // Высота камеры для изометрического вида
    private var cameraZ = 15f
    private var lookAtX = 0f
    private var lookAtY = 0f
    private var lookAtZ = 0f

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        attribute vec3 vNormal;
        varying vec3 vNormalView;
        varying vec3 vPositionView;
        
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            vNormalView = mat3(uMVPMatrix) * vNormal;
            vPositionView = vec3(uMVPMatrix * vPosition);
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform vec4 uColor;
        varying vec3 vNormalView;
        varying vec3 vPositionView;
        
        void main() {
            // Солнце в центре (0, 0, 0) на высоте
            vec3 sunPosition = vec3(0.0, 15.0, 0.0);
            vec3 lightDir = normalize(sunPosition - vPositionView);
            
            // Диффузное освещение
            float diff = max(dot(normalize(vNormalView), lightDir), 0.3);
            
            // Атмосферное освещение (ambient) - увеличиваем для более ярких цветов
            float ambient = 0.6;
            
            // Финальное освещение - делаем цвета ярче
            float lighting = ambient + diff * 0.4;
            
            // Применяем цвет с освещением, гарантируем полную непрозрачность
            gl_FragColor = vec4(uColor.rgb * lighting, 1.0);
        }
    """.trimIndent()

    private val texturedVertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        attribute vec3 aNormal;
        uniform vec3 uLightDir;
        varying vec2 vTexCoord;
        varying float vLighting;

        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTexCoord = aTexCoord;
            vec3 normal = normalize(aNormal);
            float diff = max(dot(normal, normalize(uLightDir)), 0.0);
            vLighting = 0.60 + diff * 0.40;
        }
    """.trimIndent()

    private val texturedFragmentShaderCode = """
        precision mediump float;
        uniform sampler2D uTexture;
        varying vec2 vTexCoord;
        varying float vLighting;

        void main() {
            vec4 baseColor = texture2D(uTexture, vTexCoord);
            gl_FragColor = vec4(baseColor.rgb * vLighting, baseColor.a);
        }
    """.trimIndent()

    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0

    private var texturedProgram = 0
    private var texturedPositionHandle = 0
    private var texturedTexCoordHandle = 0
    private var texturedNormalHandle = 0
    private var texturedMvpMatrixHandle = 0
    private var texturedSamplerHandle = 0
    private var texturedLightDirHandle = 0

    private val hexCells = mutableListOf<HexCell3D>()
    private var isInitialized = false
    private var initializationIndex = 0
    private val cellsToInitialize = mutableListOf<HexCell>()
    
    // Модели героев
    private val heroModels = mutableMapOf<String, HeroModel3D>()
    private var lastFrameTime = System.currentTimeMillis()
    
    // Доступные ячейки для перемещения (защищены синхронизацией)
    private val reachableCellsLock = Any()
    private var reachableCells = mutableSetOf<HexCell>()
    @Volatile
    private var selectedHero: Hero? = null

    // Случайный декор для равнин из gross1.glb
    private var plainsModelLoaded = false
    private var plainsVerticesBuffer: FloatBuffer? = null
    private var plainsNormalsBuffer: FloatBuffer? = null
    private var plainsTexCoordsBuffer: FloatBuffer? = null
    private var plainsIndicesBuffer: ShortBuffer? = null
    private var plainsTextureId = 0
    private var plainsIndexCount = 0
    private val plainsDecorRotationByCell = mutableMapOf<HexCell, Float>()

    init {
        cellsToInitialize.addAll(gameBoard.getAllCells())
        initializePlainsDecorCells()
    }

    private fun initializePlainsDecorCells() {
        gameBoard.getAllCells().forEach { cell ->
            if (cell.type == HexCellType.PLAINS && Random.nextFloat() < 0.35f) {
                val stepCount = 360 / PLAINS_DECOR_ROTATION_STEP_DEGREES
                val randomStep = Random.nextInt(stepCount)
                plainsDecorRotationByCell[cell] = (randomStep * PLAINS_DECOR_ROTATION_STEP_DEGREES).toFloat()
            }
        }
    }

    private fun initializeHexCells() {
        if (isInitialized) return

        // Initialize in small batches to keep the GL thread responsive.
        val batchSize = 4
        val endIndex = (initializationIndex + batchSize).coerceAtMost(cellsToInitialize.size)

        for (i in initializationIndex until endIndex) {
            val cell = cellsToInitialize[i]
            try {
                val hex3D = HexCell3D(cell)
                hexCells.add(hex3D)
            } catch (e: Exception) {
                android.util.Log.e("HexGrid3D", "Error creating hex cell: ${e.message}")
            }
        }

        initializationIndex = endIndex

        if (initializationIndex >= cellsToInitialize.size) {
            isInitialized = true
            cellsToInitialize.clear()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            // Темный фон (временно убрали голубое небо)
            GLES20.glClearColor(0.1f, 0.1f, 0.15f, 1.0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            GLES20.glCullFace(GLES20.GL_BACK)
            // Отключаем прозрачность/блендинг для непрозрачных объектов
            GLES20.glDisable(GLES20.GL_BLEND)

            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

            if (vertexShader == 0 || fragmentShader == 0) {
                android.util.Log.e("HexGrid3D", "Failed to load shaders")
                return
            }

            program = GLES20.glCreateProgram()
            if (program == 0) {
                android.util.Log.e("HexGrid3D", "Failed to create program")
                return
            }

            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)

            // Проверяем статус линковки
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val error = GLES20.glGetProgramInfoLog(program)
                android.util.Log.e("HexGrid3D", "Program link failed: $error")
                GLES20.glDeleteProgram(program)
                program = 0
                return
            }

            positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
            normalHandle = GLES20.glGetAttribLocation(program, "vNormal")
            colorHandle = GLES20.glGetUniformLocation(program, "uColor")
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

            initTexturedProgram()
            loadPlainsModelIfNeeded()
        } catch (e: Exception) {
            android.util.Log.e("HexGrid3D", "Error in onSurfaceCreated: ${e.message}", e)
            program = 0
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height

        // Используем правильный frustum без искажений
        // Ближняя и дальняя плоскости для правильной перспективы
        // Границы рассчитываются с учетом соотношения сторон
        val near = 1f
        val far = 100f
        val fov = 45f // Поле зрения в градусах
        val top = near * kotlin.math.tan(Math.toRadians(fov / 2.0)).toFloat()
        val bottom = -top
        val right = top * ratio
        val left = -right
        
        Matrix.frustumM(projectionMatrix, 0, left, right, bottom, top, near, far)
    }

    override fun onDrawFrame(gl: GL10?) {
        try {
            if (program == 0) {
                return
            }
            
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            if (!isInitialized) {
                initializeHexCells()
                if (!isInitialized) {
                    return
                }
            }

            if (hexCells.isEmpty()) {
                return
            }

            Matrix.setLookAtM(
                viewMatrix, 0,
                cameraX, cameraY, cameraZ,
                lookAtX, lookAtY, lookAtZ,
                0f, 1f, 0f
            )

            // Получаем копию доступных ячеек для безопасного доступа из GL thread
            val currentReachableCells = getReachableCellsCopy()
            
            hexCells.forEach { hexCell ->
                try {
                    // Проверяем, является ли ячейка доступной для перемещения
                    val isReachable = currentReachableCells.contains(hexCell.cell)
                    if (!shouldHideBaseHex(hexCell.cell)) {
                        drawHexCell(hexCell, isReachable)
                    }
                    drawPlainsDecor(hexCell.cell)
                } catch (e: Exception) {
                    android.util.Log.e("HexGrid3D", "Error drawing hex cell: ${e.message}")
                }
            }
            
            // Обновляем анимации моделей
            val currentTime = System.currentTimeMillis()
            val deltaTime = ((currentTime - lastFrameTime) / 1000.0f).coerceAtMost(0.1f)
            lastFrameTime = currentTime
            
            heroModels.values.forEach { model ->
                model.updateAnimation(deltaTime)
            }
            
            // Рисуем героев (модели или кубики)
            gameState?.let { state ->
                // Рисуем игрового героя
                state.playerHero.currentCell?.let { cell ->
                    drawHero(state.playerHero, cell)
                }
                
                // Рисуем AI героев
                state.aiHeroes.forEach { hero ->
                    hero.currentCell?.let { cell ->
                        drawHero(hero, cell)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HexGrid3D", "Error in onDrawFrame: ${e.message}", e)
        }
    }

    private fun drawHexCell(hexCell: HexCell3D, isReachable: Boolean = false) {
        if (program == 0) return
        
        GLES20.glUseProgram(program)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, hexCell.x, hexCell.y, hexCell.z)

        Matrix.multiplyMM(vPMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, vPMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, vPMatrix, 0)

        // Получаем цвет для типа ячейки
        var color = getColorForCellType(hexCell.cell.type)
        
        // Доступные для перемещения ячейки подсвечиваем зеленым.
        if (isReachable) {
            color = floatArrayOf(
                0.15f,
                0.95f,
                0.25f,
                1.0f
            )
        }
        
        // Сначала рисуем боковые грани (более темные)
        val sideColor = floatArrayOf(
            color[0] * 0.7f,  // Немного темнее для боковых граней
            color[1] * 0.7f,
            color[2] * 0.7f,
            1.0f  // Всегда полностью непрозрачный
        )
        GLES20.glUniform4fv(colorHandle, 1, sideColor, 0)

        hexCell.sideFacesBuffer?.let { buffer ->
            try {
                buffer.position(0)
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glEnableVertexAttribArray(normalHandle)
                val stride = 6 * 4
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, stride, buffer)
                buffer.position(3)
                GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, stride, buffer)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, hexCell.sideFacesCount)
                GLES20.glDisableVertexAttribArray(positionHandle)
                GLES20.glDisableVertexAttribArray(normalHandle)
            } catch (e: Exception) {
                android.util.Log.e("HexGrid3D", "Error drawing side faces: ${e.message}")
            }
        }

        // Затем рисуем верхнюю грань (яркий цвет) - она должна быть сверху
        GLES20.glUniform4fv(colorHandle, 1, color, 0)
        
        // Временно отключаем cull face для верхней грани, чтобы она точно отрисовалась
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        hexCell.topFaceBuffer?.let { buffer ->
            try {
                buffer.position(0)
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glEnableVertexAttribArray(normalHandle)
                val stride = 6 * 4 // 3 position + 3 normal floats
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, stride, buffer)
                buffer.position(3)
                GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, stride, buffer)
                // Рисуем 6 треугольников (18 вершин)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 18)
                GLES20.glDisableVertexAttribArray(positionHandle)
                GLES20.glDisableVertexAttribArray(normalHandle)
            } catch (e: Exception) {
                android.util.Log.e("HexGrid3D", "Error drawing top face: ${e.message}")
            }
        }
        
        // Включаем cull face обратно
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    private fun initTexturedProgram() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, texturedVertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, texturedFragmentShaderCode)
        if (vertexShader == 0 || fragmentShader == 0) {
            android.util.Log.e("HexGrid3D", "Failed to compile textured shaders")
            return
        }

        texturedProgram = GLES20.glCreateProgram()
        if (texturedProgram == 0) {
            android.util.Log.e("HexGrid3D", "Failed to create textured program")
            return
        }

        GLES20.glAttachShader(texturedProgram, vertexShader)
        GLES20.glAttachShader(texturedProgram, fragmentShader)
        GLES20.glLinkProgram(texturedProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(texturedProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES20.glGetProgramInfoLog(texturedProgram)
            android.util.Log.e("HexGrid3D", "Textured program link failed: $error")
            GLES20.glDeleteProgram(texturedProgram)
            texturedProgram = 0
            return
        }

        texturedPositionHandle = GLES20.glGetAttribLocation(texturedProgram, "aPosition")
        texturedTexCoordHandle = GLES20.glGetAttribLocation(texturedProgram, "aTexCoord")
        texturedNormalHandle = GLES20.glGetAttribLocation(texturedProgram, "aNormal")
        texturedMvpMatrixHandle = GLES20.glGetUniformLocation(texturedProgram, "uMVPMatrix")
        texturedSamplerHandle = GLES20.glGetUniformLocation(texturedProgram, "uTexture")
        texturedLightDirHandle = GLES20.glGetUniformLocation(texturedProgram, "uLightDir")
    }

    private fun createTextureFromBytes(imageBytes: ByteArray): Int {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return 0

        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]
        if (textureId == 0) {
            bitmap.recycle()
            return 0
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        // NPOT-safe параметры для GLES2, иначе текстура может стать "черной".
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        bitmap.recycle()
        return textureId
    }

    private fun loadPlainsModelIfNeeded() {
        if (plainsModelLoaded) return
        val ctx = context ?: return

        try {
            val stream = ctx.assets.open("models/hexagons/gross1.glb")
            val parsed = stream.use { GLBParser.parseGLB(it) }
            if (parsed == null || parsed.vertices.isEmpty() || parsed.indices.isEmpty()) {
                android.util.Log.w("HexGrid3D", "gross1.glb parse failed or empty")
                return
            }

            plainsVerticesBuffer = ByteBuffer.allocateDirect(parsed.vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(parsed.vertices)
            plainsVerticesBuffer?.position(0)

            plainsNormalsBuffer = ByteBuffer.allocateDirect(parsed.normals.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(parsed.normals)
            plainsNormalsBuffer?.position(0)

            parsed.texCoords?.let { texCoords ->
                plainsTexCoordsBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(texCoords)
                plainsTexCoordsBuffer?.position(0)
            }

            val shortIndices = ShortArray(parsed.indices.size) { index ->
                val raw = parsed.indices[index]
                if (raw in 0..65535) raw.toShort() else 0
            }
            plainsIndicesBuffer = ByteBuffer.allocateDirect(shortIndices.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .put(shortIndices)
            plainsIndicesBuffer?.position(0)
            plainsIndexCount = shortIndices.size

            if (parsed.textureImageData != null && texturedProgram != 0) {
                plainsTextureId = createTextureFromBytes(parsed.textureImageData)
            }

            plainsModelLoaded = true
            android.util.Log.d("HexGrid3D", "Loaded plains decor gross1.glb with $plainsIndexCount indices")
        } catch (e: Exception) {
            android.util.Log.e("HexGrid3D", "Failed to load plains decor model: ${e.message}", e)
        }
    }

    private fun drawPlainsDecor(cell: HexCell) {
        if (cell.type != HexCellType.PLAINS) return
        val rotation = plainsDecorRotationByCell[cell] ?: return
        if (!plainsModelLoaded || program == 0) return

        val vertices = plainsVerticesBuffer ?: return
        val normals = plainsNormalsBuffer ?: return
        val indices = plainsIndicesBuffer ?: return
        if (plainsIndexCount <= 0) return

        val (worldX, worldZ) = HexCell3D.hexToWorld(cell.x, cell.y)
        val baseY = 0.22f

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, worldX, baseY, worldZ)
        Matrix.rotateM(modelMatrix, 0, rotation, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, PLAINS_DECOR_SCALE, PLAINS_DECOR_SCALE, PLAINS_DECOR_SCALE)

        Matrix.multiplyMM(vPMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, vPMatrix, 0)

        val texCoords = plainsTexCoordsBuffer
        val canUseTexture = texturedProgram != 0 && plainsTextureId != 0 && texCoords != null

        if (canUseTexture) {
            GLES20.glUseProgram(texturedProgram)
            GLES20.glUniformMatrix4fv(texturedMvpMatrixHandle, 1, false, vPMatrix, 0)
            GLES20.glUniform3f(texturedLightDirHandle, 0.35f, 0.85f, 0.25f)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, plainsTextureId)
            GLES20.glUniform1i(texturedSamplerHandle, 0)

            vertices.position(0)
            normals.position(0)
            texCoords?.position(0)
            indices.position(0)

            GLES20.glEnableVertexAttribArray(texturedPositionHandle)
            GLES20.glEnableVertexAttribArray(texturedTexCoordHandle)
            GLES20.glEnableVertexAttribArray(texturedNormalHandle)
            GLES20.glVertexAttribPointer(texturedPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices)
            GLES20.glVertexAttribPointer(texturedTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoords)
            GLES20.glVertexAttribPointer(texturedNormalHandle, 3, GLES20.GL_FLOAT, false, 0, normals)

            GLES20.glDisable(GLES20.GL_CULL_FACE)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, plainsIndexCount, GLES20.GL_UNSIGNED_SHORT, indices)
            GLES20.glEnable(GLES20.GL_CULL_FACE)

            GLES20.glDisableVertexAttribArray(texturedPositionHandle)
            GLES20.glDisableVertexAttribArray(texturedTexCoordHandle)
            GLES20.glDisableVertexAttribArray(texturedNormalHandle)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            return
        }

        // Fallback: если в модели нет UV/текстуры, рисуем однотонно.
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, vPMatrix, 0)
        GLES20.glUniform4fv(colorHandle, 1, floatArrayOf(0.78f, 0.84f, 0.64f, 1.0f), 0)

        vertices.position(0)
        normals.position(0)
        indices.position(0)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normals)

        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, plainsIndexCount, GLES20.GL_UNSIGNED_SHORT, indices)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }

    /**
     * Рисует героя (модель или кубик, если модель не найдена)
     */
    private fun drawHero(hero: Hero, cell: HexCell) {
        // Получаем или создаем модель героя
        val modelKey = "${hero.id}_${hero.race.name}_${hero.profession.name}"
        var heroModel = heroModels[modelKey]
        
        if (heroModel == null && context != null) {
            // Пытаемся загрузить модель
            try {
                android.util.Log.d("HexGrid3D", "Creating hero model for ${hero.race}/${hero.profession}")
                heroModel = HeroModel3D(context, hero)
                heroModels[modelKey] = heroModel
                
                if (heroModel.isLoaded()) {
                    android.util.Log.d("HexGrid3D", "Hero model loaded successfully")
                } else {
                    android.util.Log.w("HexGrid3D", "Hero model not loaded, will use cube")
                }
            } catch (e: Exception) {
                android.util.Log.e("HexGrid3D", "Error creating hero model: ${e.message}", e)
                e.printStackTrace()
            }
        }
        
        // Если модель загружена, рисуем её, иначе рисуем кубик
        if (heroModel != null && heroModel.isLoaded()) {
            android.util.Log.v("HexGrid3D_RENDER", "[${hero.name}] Drawing hero MODEL")
            drawHeroModel(heroModel, hero, cell)
        } else {
            android.util.Log.v("HexGrid3D_RENDER", "[${hero.name}] Drawing hero CUBE (model not loaded)")
            drawHeroCube(hero, cell)
        }
    }
    
    /**
     * Рисует 3D модель героя
     */
    private fun drawHeroModel(heroModel: HeroModel3D, hero: Hero, cell: HexCell) {
        if (program == 0) return
        
        GLES20.glUseProgram(program)
        
        // Получаем позицию ячейки
        val (worldX, worldZ) = HexCell3D.hexToWorld(cell.x, cell.y)
        val cellY = when (cell.type) {
            HexCellType.MOUNTAINS -> 0.3f
            HexCellType.LAKE -> -0.1f
            HexCellType.CORRUPTED_LAND -> 0.1f
            else -> 0.0f
        }
        
        // Позиция героя на ячейке
        // Для GLB моделей позиционируем выше, так как они могут быть большими
        val heroY = cellY + 0.1f
        
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, worldX, heroY, worldZ)
        Matrix.scaleM(modelMatrix, 0, HERO_MODEL_SCALE, HERO_MODEL_SCALE, HERO_MODEL_SCALE)
        
        // ВАЖНО: Применяем анимацию ПОСЛЕ масштабирования, но ДО умножения матриц
        applyHeroEventAnimationTransform(hero)
        
        Matrix.multiplyMM(vPMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, vPMatrix, 0)
        
        // Цвет модели в зависимости от расы и профессии
        val heroColor = getColorForRace(hero.race, hero.profession)
        
        // Рисуем модель
        heroModel.draw(
            program,
            vPMatrix,
            mvpMatrixHandle,
            positionHandle,
            normalHandle,
            colorHandle,
            heroColor
        )
    }

    private fun drawHeroCube(hero: Hero, cell: HexCell) {
        if (program == 0) return
        
        GLES20.glUseProgram(program)
        
        // Получаем позицию ячейки
        val (worldX, worldZ) = HexCell3D.hexToWorld(cell.x, cell.y)
        val cellY = when (cell.type) {
            HexCellType.MOUNTAINS -> 0.3f
            HexCellType.LAKE -> -0.1f
            HexCellType.CORRUPTED_LAND -> 0.1f
            else -> 0.0f
        }
        
        // Размер кубика героя
        val cubeSize = 0.3f
        val cubeHeight = 0.4f
        val cubeY = cellY + 0.2f + cubeHeight / 2f // На вершине ячейки
        
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, worldX, cubeY, worldZ)
        Matrix.scaleM(modelMatrix, 0, cubeSize, cubeHeight, cubeSize)
        
        // ВАЖНО: Применяем анимацию ПОСЛЕ масштабирования
        applyHeroEventAnimationTransform(hero)
        
        Matrix.multiplyMM(vPMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, vPMatrix, 0)
        
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, vPMatrix, 0)
        
        // Цвет кубика в зависимости от расы и профессии героя
        val heroColor = getColorForRace(hero.race, hero.profession)
        GLES20.glUniform4fv(colorHandle, 1, heroColor, 0)
        
        // Рисуем кубик (простой куб из 6 граней)
        drawCube()
    }
    
    private fun drawCube() {
        // Простой куб: 8 вершин, 12 треугольников (2 на грань)
        // Вершины куба от -0.5 до 0.5
        val vertices = floatArrayOf(
            // Передняя грань
            -0.5f, -0.5f,  0.5f,  0f, 0f, 1f,
             0.5f, -0.5f,  0.5f,  0f, 0f, 1f,
             0.5f,  0.5f,  0.5f,  0f, 0f, 1f,
            -0.5f, -0.5f,  0.5f,  0f, 0f, 1f,
             0.5f,  0.5f,  0.5f,  0f, 0f, 1f,
            -0.5f,  0.5f,  0.5f,  0f, 0f, 1f,
            // Задняя грань
            -0.5f, -0.5f, -0.5f,  0f, 0f, -1f,
             0.5f,  0.5f, -0.5f,  0f, 0f, -1f,
             0.5f, -0.5f, -0.5f,  0f, 0f, -1f,
            -0.5f, -0.5f, -0.5f,  0f, 0f, -1f,
            -0.5f,  0.5f, -0.5f,  0f, 0f, -1f,
             0.5f,  0.5f, -0.5f,  0f, 0f, -1f,
            // Верхняя грань
            -0.5f,  0.5f, -0.5f,  0f, 1f, 0f,
             0.5f,  0.5f, -0.5f,  0f, 1f, 0f,
             0.5f,  0.5f,  0.5f,  0f, 1f, 0f,
            -0.5f,  0.5f, -0.5f,  0f, 1f, 0f,
             0.5f,  0.5f,  0.5f,  0f, 1f, 0f,
            -0.5f,  0.5f,  0.5f,  0f, 1f, 0f,
            // Нижняя грань
            -0.5f, -0.5f, -0.5f,  0f, -1f, 0f,
             0.5f, -0.5f,  0.5f,  0f, -1f, 0f,
             0.5f, -0.5f, -0.5f,  0f, -1f, 0f,
            -0.5f, -0.5f, -0.5f,  0f, -1f, 0f,
            -0.5f, -0.5f,  0.5f,  0f, -1f, 0f,
             0.5f, -0.5f,  0.5f,  0f, -1f, 0f,
            // Правая грань
             0.5f, -0.5f, -0.5f,  1f, 0f, 0f,
             0.5f,  0.5f,  0.5f,  1f, 0f, 0f,
             0.5f, -0.5f,  0.5f,  1f, 0f, 0f,
             0.5f, -0.5f, -0.5f,  1f, 0f, 0f,
             0.5f,  0.5f, -0.5f,  1f, 0f, 0f,
             0.5f,  0.5f,  0.5f,  1f, 0f, 0f,
            // Левая грань
            -0.5f, -0.5f, -0.5f,  -1f, 0f, 0f,
            -0.5f, -0.5f,  0.5f,  -1f, 0f, 0f,
            -0.5f,  0.5f,  0.5f,  -1f, 0f, 0f,
            -0.5f, -0.5f, -0.5f,  -1f, 0f, 0f,
            -0.5f,  0.5f,  0.5f,  -1f, 0f, 0f,
            -0.5f,  0.5f, -0.5f,  -1f, 0f, 0f
        )
        
        val vertexBuffer = java.nio.ByteBuffer.allocateDirect(vertices.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexBuffer.put(vertices)
        vertexBuffer.position(0)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)
        val stride = 6 * 4
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, stride, vertexBuffer)
        vertexBuffer.position(3)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, stride, vertexBuffer)
        
        GLES20.glDisable(GLES20.GL_CULL_FACE) // Отключаем для кубика
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36) // 6 граней * 6 вершин
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }

    private val lastAnimationLogTimeByHero = mutableMapOf<String, Long>()
    
    private fun applyHeroEventAnimationTransform(hero: Hero) {
        // Базовый idle-слой работает всегда (кроме состояния смерти).
        // Это процедурная idle-анимация для всех моделей (и GLB, и кубиков)
        val t = System.currentTimeMillis() / 1000f
        
        // Логируем состояние анимации раз в 3 секунды для отладки (отдельно для каждого героя)
        val now = System.currentTimeMillis()
        val lastLogTime = lastAnimationLogTimeByHero[hero.id] ?: 0L
        if (now - lastLogTime > 3000) {
            val yOffset = kotlin.math.abs(kotlin.math.sin(t * 2.2f)) * 0.18f
            val rotationAngle = kotlin.math.sin(t * 1.5f) * 8.0f
            android.util.Log.d("HexGrid3D_ANIM", "[${hero.name}] Event: ${hero.animationEvent}, t=$t, yOffset=$yOffset, rotation=$rotationAngle")
            lastAnimationLogTimeByHero[hero.id] = now
        }
        
        if (hero.animationEvent != HeroAnimationEvent.DEATH) {
            // Плавное покачивание вверх-вниз (дыхание) - ЗНАЧИТЕЛЬНО увеличена амплитуда
            // Используем большую амплитуду, так как модели масштабируются в 4 раза
            val yOffset = kotlin.math.abs(kotlin.math.sin(t * 2.2f)) * 0.06f
            Matrix.translateM(modelMatrix, 0, 0f, yOffset, 0f)
            
            // Плавное вращение влево-вправо (осмотр окружения) - хорошо заметный угол
            val rotationAngle = kotlin.math.sin(t * 1.5f) * 15.0f
            Matrix.rotateM(modelMatrix, 0, rotationAngle, 0f, 1f, 0f)
        }

        // Для событий кроме IDLE требуется установленное время начала
        if (hero.animationEventStartTimeMs <= 0L && hero.animationEvent != HeroAnimationEvent.IDLE) {
            return
        }

        val elapsedMs = (System.currentTimeMillis() - hero.animationEventStartTimeMs).coerceAtLeast(0L)
        val elapsedSec = elapsedMs / 1000f

        when (hero.animationEvent) {
            HeroAnimationEvent.ROLL -> {
                if (elapsedMs <= HERO_ROLL_ANIM_DURATION_MS) {
                    val phase = (elapsedSec * 12f).coerceAtMost(8f)
                    // Дополнительное движение поверх базового idle
                    Matrix.translateM(modelMatrix, 0, 0f, kotlin.math.abs(kotlin.math.sin(phase)) * 0.15f, 0f)
                    Matrix.rotateM(modelMatrix, 0, phase * 22f, 0f, 1f, 0f)
                } else {
                    hero.clearAnimation()
                }
            }
            HeroAnimationEvent.WALK -> {
                if (elapsedMs <= HERO_WALK_ANIM_DURATION_MS) {
                    val phase = elapsedSec * 12f
                    // Ходьба с покачиванием
                    Matrix.translateM(modelMatrix, 0, 0f, kotlin.math.abs(kotlin.math.sin(phase)) * 0.08f, 0f)
                    Matrix.rotateM(modelMatrix, 0, kotlin.math.sin(phase) * 3f, 0f, 1f, 0f)
                } else {
                    hero.clearAnimation()
                }
            }
            HeroAnimationEvent.RUN -> {
                if (elapsedMs <= HERO_RUN_ANIM_DURATION_MS) {
                    val phase = elapsedSec * 20f
                    // Бег с наклоном вперед
                    Matrix.translateM(modelMatrix, 0, 0f, kotlin.math.abs(kotlin.math.sin(phase)) * 0.12f, 0f)
                    Matrix.rotateM(modelMatrix, 0, -10f, 1f, 0f, 0f)
                } else {
                    hero.clearAnimation()
                }
            }
            HeroAnimationEvent.FIGHT -> {
                if (elapsedMs <= HERO_FIGHT_ANIM_DURATION_MS) {
                    val phase = (elapsedSec * 10f).coerceAtMost(6f)
                    // Боевая стойка с выпадами
                    Matrix.translateM(modelMatrix, 0, 0f, 0f, kotlin.math.sin(phase) * 0.16f)
                    Matrix.rotateM(modelMatrix, 0, kotlin.math.sin(phase) * 12f, 0f, 1f, 0f)
                } else {
                    hero.clearAnimation()
                }
            }
            HeroAnimationEvent.DEATH -> {
                val progress = (elapsedMs.toFloat() / HERO_DEATH_ANIM_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                // Падение на бок
                Matrix.translateM(modelMatrix, 0, 0f, -0.35f * progress, 0f)
                Matrix.rotateM(modelMatrix, 0, 88f * progress, 0f, 0f, 1f)
                // Сохраняем финальную позу (не сбрасываем в IDLE).
            }
            HeroAnimationEvent.IDLE -> {
                // Базовая idle-анимация уже применена выше (покачивание и вращение)
                // Дополнительных трансформаций не требуется
            }
        }
    }
    
    private fun getColorForRace(race: Race, profession: org.mediarise.herostep.data.model.Profession? = null): FloatArray {
        // Базовый цвет расы
        val baseColor = when (race) {
            Race.HUMANS -> floatArrayOf(0.9f, 0.8f, 0.7f, 1.0f) // Телесный
            Race.ORCS -> floatArrayOf(0.2f, 0.6f, 0.2f, 1.0f) // Зеленый
            Race.ELVES -> floatArrayOf(0.8f, 0.9f, 0.6f, 1.0f) // Светло-желтый
            Race.DWARVES -> floatArrayOf(0.6f, 0.4f, 0.3f, 1.0f) // Коричневый
            Race.CHAOS_LEGION -> floatArrayOf(0.8f, 0.2f, 0.2f, 1.0f) // Красный
            Race.UNDEAD -> floatArrayOf(0.5f, 0.5f, 0.6f, 1.0f) // Серо-синий
        }
        
        // Добавляем оттенок профессии
        if (profession != null) {
            val professionTint = when (profession) {
                org.mediarise.herostep.data.model.Profession.WARRIOR -> floatArrayOf(1.0f, 0.9f, 0.9f, 1.0f) // Слегка красноватый
                org.mediarise.herostep.data.model.Profession.ARCHER -> floatArrayOf(0.9f, 1.0f, 0.9f, 1.0f) // Слегка зеленоватый
                org.mediarise.herostep.data.model.Profession.ROGUE -> floatArrayOf(0.8f, 0.8f, 1.0f, 1.0f) // Слегка синеватый
                org.mediarise.herostep.data.model.Profession.MAGE -> floatArrayOf(1.0f, 0.9f, 1.0f, 1.0f) // Слегка розоватый
                org.mediarise.herostep.data.model.Profession.PRIEST -> floatArrayOf(1.0f, 1.0f, 0.9f, 1.0f) // Слегка желтоватый
            }
            
            // Смешиваем базовый цвет с оттенком профессии
            return floatArrayOf(
                baseColor[0] * professionTint[0],
                baseColor[1] * professionTint[1],
                baseColor[2] * professionTint[2],
                1.0f
            )
        }
        
        return baseColor
    }

    private fun getColorForCellType(type: HexCellType): FloatArray {
        // Возвращаем яркие, насыщенные цвета для каждого типа ячейки
        return when (type) {
            // Лес - насыщенный темно-зеленый
            HexCellType.FOREST -> floatArrayOf(0.0f, 0.5f, 0.1f, 1.0f)
            // Равнина - яркий желто-зеленый (трава)
            HexCellType.PLAINS -> floatArrayOf(0.6f, 0.8f, 0.3f, 1.0f)
            // Горы - светло-серый/бежевый (камень)
            HexCellType.MOUNTAINS -> floatArrayOf(0.75f, 0.75f, 0.7f, 1.0f)
            // Озеро - насыщенный синий
            HexCellType.LAKE -> floatArrayOf(0.1f, 0.4f, 0.9f, 1.0f)
            // Пустошь - песочный/желтый
            HexCellType.WASTELAND -> floatArrayOf(0.85f, 0.75f, 0.5f, 1.0f)
            // Оскверненная земля - темно-фиолетовый/красный (зло)
            HexCellType.CORRUPTED_LAND -> floatArrayOf(0.5f, 0.0f, 0.3f, 1.0f)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            android.util.Log.e("HexGrid3D", "Failed to create shader")
            return 0
        }
        
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        // Проверяем статус компиляции
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            android.util.Log.e("HexGrid3D", "Shader compilation failed: $error")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }

    fun updateCamera(x: Float, y: Float, z: Float) {
        cameraX = x
        cameraY = y
        cameraZ = z
    }

    fun updateLookAt(x: Float, y: Float, z: Float) {
        lookAtX = x
        lookAtY = y
        lookAtZ = z
    }

    /**
     * Обрабатывает тап по экрану: выбирает ближайшую ячейку под курсором и вызывает callback.
     */
    fun handleTap(screenX: Float, screenY: Float, viewWidth: Int, viewHeight: Int): HexCell? {
        val cell = pickCellByScreenPoint(screenX, screenY, viewWidth, viewHeight)
        if (cell != null) {
            onCellSelected(cell)
        }
        return cell
    }

    private fun pickCellByScreenPoint(
        screenX: Float,
        screenY: Float,
        viewWidth: Int,
        viewHeight: Int
    ): HexCell? {
        if (viewWidth <= 0 || viewHeight <= 0) return null

        val localViewMatrix = FloatArray(16)
        Matrix.setLookAtM(
            localViewMatrix, 0,
            cameraX, cameraY, cameraZ,
            lookAtX, lookAtY, lookAtZ,
            0f, 1f, 0f
        )

        val vpMatrix = FloatArray(16)
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, localViewMatrix, 0)

        val inverseVP = FloatArray(16)
        val inverted = Matrix.invertM(inverseVP, 0, vpMatrix, 0)
        if (!inverted) return null

        val ndcX = (2f * screenX / viewWidth) - 1f
        val ndcY = 1f - (2f * screenY / viewHeight)

        val nearPoint = unprojectPoint(ndcX, ndcY, -1f, inverseVP) ?: return null
        val farPoint = unprojectPoint(ndcX, ndcY, 1f, inverseVP) ?: return null

        val rayDirX = farPoint[0] - nearPoint[0]
        val rayDirY = farPoint[1] - nearPoint[1]
        val rayDirZ = farPoint[2] - nearPoint[2]

        if (kotlin.math.abs(rayDirY) < 0.0001f) return null

        // Пересекаем луч с плоскостью земли y=0.
        val t = -nearPoint[1] / rayDirY
        if (t < 0f) return null

        val hitX = nearPoint[0] + rayDirX * t
        val hitZ = nearPoint[2] + rayDirZ * t

        var nearestCell: HexCell? = null
        var nearestDist2 = Float.MAX_VALUE

        gameBoard.getAllCells().forEach { cell ->
            val (cellX, cellZ) = HexCell3D.hexToWorld(cell.x, cell.y)
            val dx = hitX - cellX
            val dz = hitZ - cellZ
            val dist2 = dx * dx + dz * dz

            if (dist2 < nearestDist2) {
                nearestDist2 = dist2
                nearestCell = cell
            }
        }

        val maxPickDistance = 2.6f
        return if (nearestCell != null && nearestDist2 <= maxPickDistance * maxPickDistance) {
            nearestCell
        } else {
            null
        }
    }

    private fun unprojectPoint(
        ndcX: Float,
        ndcY: Float,
        ndcZ: Float,
        inverseVP: FloatArray
    ): FloatArray? {
        val inVec = floatArrayOf(ndcX, ndcY, ndcZ, 1f)
        val outVec = FloatArray(4)
        Matrix.multiplyMV(outVec, 0, inverseVP, 0, inVec, 0)

        if (kotlin.math.abs(outVec[3]) < 0.00001f) return null
        val invW = 1f / outVec[3]

        return floatArrayOf(
            outVec[0] * invW,
            outVec[1] * invW,
            outVec[2] * invW
        )
    }

    fun isReady(): Boolean = isInitialized

    private fun shouldHideBaseHex(cell: HexCell): Boolean {
        if (!plainsModelLoaded) return false
        if (cell.type != HexCellType.PLAINS) return false
        return plainsDecorRotationByCell.containsKey(cell)
    }
    
    /**
     * Устанавливает доступные ячейки для перемещения и выбранного героя
     * Безопасно вызывать из любого потока
     */
    fun setReachableCells(cells: Set<HexCell>, hero: Hero?) {
        try {
            synchronized(reachableCellsLock) {
                reachableCells.clear()
                reachableCells.addAll(cells)
            }
            selectedHero = hero
        } catch (e: Exception) {
            android.util.Log.e("HexGrid3DRenderer", "Error setting reachable cells: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Очищает доступные ячейки
     */
    fun clearReachableCells() {
        try {
            synchronized(reachableCellsLock) {
                reachableCells.clear()
            }
            selectedHero = null
        } catch (e: Exception) {
            android.util.Log.e("HexGrid3DRenderer", "Error clearing reachable cells: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Безопасно получает копию доступных ячеек
     */
    private fun getReachableCellsCopy(): Set<HexCell> {
        return synchronized(reachableCellsLock) {
            reachableCells.toSet()
        }
    }

    companion object {
        // Масштаб hero-модели.
        private const val HERO_MODEL_SCALE = 4.0f
        private const val HERO_ROLL_ANIM_DURATION_MS = 520L
        private const val HERO_WALK_ANIM_DURATION_MS = 650L
        private const val HERO_RUN_ANIM_DURATION_MS = 480L
        private const val HERO_FIGHT_ANIM_DURATION_MS = 420L
        private const val HERO_DEATH_ANIM_DURATION_MS = 900L
        // Масштаб декоративной модели gross1.glb на равнинах.
        private const val PLAINS_DECOR_SCALE = 1.8f
        // Поворот декоративной модели выбирается случайно шагом в 30 градусов.
        private const val PLAINS_DECOR_ROTATION_STEP_DEGREES = 30
    }
}

class HexCell3D(val cell: HexCell) {
    val x: Float
    val y: Float
    val z: Float
    var topFaceBuffer: FloatBuffer? = null
    var sideFacesBuffer: FloatBuffer? = null
    var sideFacesCount: Int = 0

    init {
        val (worldX, worldZ) = hexToWorld(cell.x, cell.y)
        x = worldX
        z = worldZ
        y = when (cell.type) {
            HexCellType.MOUNTAINS -> 0.3f
            HexCellType.LAKE -> -0.1f
            HexCellType.CORRUPTED_LAND -> 0.1f
            else -> 0.0f
        }

        createGeometry()
    }

    private fun createGeometry() {
        // Используем радиус из companion object
        val radius = Companion.HEX_RADIUS
        // Увеличиваем высоту ячеек для лучшей видимости и закраски
        val hexHeight = 0.2f
        val baseY = y
        val normal = floatArrayOf(0f, 1f, 0f)

        // Создаем верхнюю грань как треугольники (6 треугольников от центра к краям)
        // 6 треугольников * 3 вершины * 6 элементов (3 позиции + 3 нормали) = 108 элементов
        val topVertices = FloatArray(6 * 3 * 6)
        var idx = 0
        
        val centerX = 0f
        val centerY = baseY + hexHeight
        val centerZ = 0f

        // Создаем 6 треугольников по часовой стрелке
        for (i in 0..5) {
            val angle1 = Math.PI / 3 * i - Math.PI / 6
            val angle2 = Math.PI / 3 * (i + 1) - Math.PI / 6
            
            val x1 = (radius * kotlin.math.cos(angle1)).toFloat()
            val z1 = (radius * kotlin.math.sin(angle1)).toFloat()
            val x2 = (radius * kotlin.math.cos(angle2)).toFloat()
            val z2 = (radius * kotlin.math.sin(angle2)).toFloat()
            
            // Первая вершина - центр
            topVertices[idx++] = centerX
            topVertices[idx++] = centerY
            topVertices[idx++] = centerZ
            topVertices[idx++] = normal[0]
            topVertices[idx++] = normal[1]
            topVertices[idx++] = normal[2]
            
            // Вторая вершина - первая точка на краю
            topVertices[idx++] = x1
            topVertices[idx++] = centerY
            topVertices[idx++] = z1
            topVertices[idx++] = normal[0]
            topVertices[idx++] = normal[1]
            topVertices[idx++] = normal[2]
            
            // Третья вершина - вторая точка на краю
            topVertices[idx++] = x2
            topVertices[idx++] = centerY
            topVertices[idx++] = z2
            topVertices[idx++] = normal[0]
            topVertices[idx++] = normal[1]
            topVertices[idx++] = normal[2]
        }

        val topBuffer = ByteBuffer.allocateDirect(topVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        topBuffer.put(topVertices)
        topBuffer.position(0)
        topFaceBuffer = topBuffer

        // 6 граней * 6 вершин на грань * 6 элементов на вершину (3 позиции + 3 нормали) = 216
        val sideVertices = FloatArray(6 * 6 * 6)
        idx = 0
        val bottomY = baseY

        for (i in 0..5) {
            val angle1 = Math.PI / 3 * i - Math.PI / 6
            val angle2 = Math.PI / 3 * (i + 1) - Math.PI / 6

            val x1 = (radius * kotlin.math.cos(angle1)).toFloat()
            val z1 = (radius * kotlin.math.sin(angle1)).toFloat()
            val x2 = (radius * kotlin.math.cos(angle2)).toFloat()
            val z2 = (radius * kotlin.math.sin(angle2)).toFloat()

            val dx = x2 - x1
            val dz = z2 - z1
            val len = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
            val nx = if (len > 0.0001f) -dz / len else 0f
            val nz = if (len > 0.0001f) dx / len else 0f

            // Первый треугольник: верх-верх-низ
            sideVertices[idx++] = x1; sideVertices[idx++] = bottomY + hexHeight; sideVertices[idx++] = z1
            sideVertices[idx++] = nx; sideVertices[idx++] = 0f; sideVertices[idx++] = nz
            sideVertices[idx++] = x2; sideVertices[idx++] = bottomY + hexHeight; sideVertices[idx++] = z2
            sideVertices[idx++] = nx; sideVertices[idx++] = 0f; sideVertices[idx++] = nz
            sideVertices[idx++] = x1; sideVertices[idx++] = bottomY; sideVertices[idx++] = z1
            sideVertices[idx++] = nx; sideVertices[idx++] = 0f; sideVertices[idx++] = nz

            // Второй треугольник: верх-низ-низ
            sideVertices[idx++] = x2; sideVertices[idx++] = bottomY + hexHeight; sideVertices[idx++] = z2
            sideVertices[idx++] = nx; sideVertices[idx++] = 0f; sideVertices[idx++] = nz
            sideVertices[idx++] = x2; sideVertices[idx++] = bottomY; sideVertices[idx++] = z2
            sideVertices[idx++] = nx; sideVertices[idx++] = 0f; sideVertices[idx++] = nz
            sideVertices[idx++] = x1; sideVertices[idx++] = bottomY; sideVertices[idx++] = z1
            sideVertices[idx++] = nx; sideVertices[idx++] = 0f; sideVertices[idx++] = nz
        }

        // 6 граней * 6 вершин = 36 вершин
        sideFacesCount = 36
        val sideBuffer = ByteBuffer.allocateDirect(sideVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        sideBuffer.put(sideVertices)
        sideBuffer.position(0)
        sideFacesBuffer = sideBuffer
    }

    companion object {
        // Расстояние между центрами соседних ячеек (увеличено в 2.5 раза)
        private const val HEX_SIZE = 2.5f
        // Радиус шестиугольника = немного меньше половины (чтобы точно убрать промежутки) (увеличено пропорционально)
        private const val HEX_RADIUS = 2.4f

        fun hexToWorld(q: Int, r: Int): Pair<Float, Float> {
            // Используем размер без промежутков - ячейки слипшиеся
            val sqrt3 = kotlin.math.sqrt(3.0).toFloat()
            val x = HEX_SIZE * (sqrt3 * q + sqrt3 / 2 * r)
            val z = HEX_SIZE * (3.0f / 2 * r)
            return Pair(x, z)
        }
    }
}
