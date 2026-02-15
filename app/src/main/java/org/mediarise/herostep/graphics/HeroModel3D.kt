package org.mediarise.herostep.graphics

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import org.mediarise.herostep.data.model.Hero
import org.mediarise.herostep.models.HeroModelLoader
import java.io.InputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer

/**
 * Класс для загрузки и отображения 3D модели героя.
 * Использует текстуру из самой GLB модели, если она доступна,
 * иначе применяет vertex-color или baseColorFactor материала.
 */
class HeroModel3D(
    private val context: Context,
    private val hero: Hero
) {
    private var modelLoaded = false
    private var verticesBuffer: FloatBuffer? = null
    private var normalsBuffer: FloatBuffer? = null
    private var texCoordsBuffer: FloatBuffer? = null
    private var colorsBuffer: FloatBuffer? = null
    private var shortIndicesBuffer: ShortBuffer? = null
    private var intIndicesBuffer: IntBuffer? = null
    private var indicesType: Int = 5123
    private var indexCount = 0
    private var animationTime = 0f
    private var didLogUintFallback = false
    private var supportsUintIndices: Boolean? = null

    private var textureImageData: ByteArray? = null
    private var materialBaseColorFactor: FloatArray? = null
    private var textureId = 0

    private var texturedProgram = 0
    private var texturedPositionHandle = 0
    private var texturedTexCoordHandle = 0
    private var texturedNormalHandle = 0
    private var texturedMvpMatrixHandle = 0
    private var texturedSamplerHandle = 0
    private var texturedLightDirHandle = 0

    private var vertexColorProgram = 0
    private var vertexColorPositionHandle = 0
    private var vertexColorAttrHandle = 0
    private var vertexColorNormalHandle = 0
    private var vertexColorMvpMatrixHandle = 0
    private var vertexColorLightDirHandle = 0

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
            float nLen = max(length(aNormal), 0.0001);
            vec3 normal = aNormal / nLen;
            float diff = max(dot(normal, normalize(uLightDir)), 0.0);
            vLighting = 0.85 + diff * 0.15;
        }
    """.trimIndent()

    private val texturedFragmentShaderCode = """
        precision mediump float;
        uniform sampler2D uTexture;
        varying vec2 vTexCoord;
        varying float vLighting;

        void main() {
            vec4 baseColor = texture2D(uTexture, vTexCoord);
            vec3 lit = baseColor.rgb * vLighting + vec3(0.06, 0.06, 0.06);
            gl_FragColor = vec4(min(lit, vec3(1.0, 1.0, 1.0)), baseColor.a);
        }
    """.trimIndent()

    private val vertexColorVertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        attribute vec4 aColor;
        attribute vec3 aNormal;
        uniform vec3 uLightDir;
        varying vec4 vColor;
        varying float vLighting;

        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vColor = aColor;
            float nLen = max(length(aNormal), 0.0001);
            vec3 normal = aNormal / nLen;
            float diff = max(dot(normal, normalize(uLightDir)), 0.0);
            vLighting = 0.85 + diff * 0.15;
        }
    """.trimIndent()

    private val vertexColorFragmentShaderCode = """
        precision mediump float;
        varying vec4 vColor;
        varying float vLighting;

        void main() {
            vec3 lit = vColor.rgb * vLighting + vec3(0.06, 0.06, 0.06);
            gl_FragColor = vec4(min(lit, vec3(1.0, 1.0, 1.0)), vColor.a);
        }
    """.trimIndent()

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val loader = HeroModelLoader(context)
            val config = loader.getModelConfigForHero(hero)

            Log.d("HeroModel3D", "Attempting to load model for ${hero.race}/${hero.profession}")
            Log.d("HeroModel3D", "Model path: ${config.modelPath}")

            if (!loader.modelExists(config)) {
                Log.d("HeroModel3D", "Model not found for ${hero.race}/${hero.profession}, using cube")
                modelLoaded = false
                return
            }

            val modelStream = loader.loadModel(config)
            if (modelStream != null) {
                val actualPath = loader.findModelPath(config) ?: config.modelPath
                Log.d("HeroModel3D", "Actual model path: $actualPath")
                parseModel(modelStream, actualPath)
                modelStream.close()
            } else {
                Log.w("HeroModel3D", "Model stream is null")
                modelLoaded = false
            }
        } catch (e: Exception) {
            Log.e("HeroModel3D", "Error loading model: ${e.message}", e)
            modelLoaded = false
        }
    }

    private fun parseModel(stream: InputStream, path: String) {
        try {
            if (path.endsWith(".glb") || path.endsWith(".gltf")) {
                val bytes = stream.readBytes()
                val parsedModel = GLBParser.parseGLB(bytes.inputStream())
                if (parsedModel?.hasAnimation == true) {
                    Log.d("HeroModel3D", "GLB contains animation clips (using event-driven runtime animation)")
                }

                if (parsedModel != null && parsedModel.vertices.isNotEmpty() && parsedModel.indices.isNotEmpty()) {
                    val normalizedVertices = normalizeModelVertices(parsedModel.vertices)
                    verticesBuffer = ByteBuffer.allocateDirect(normalizedVertices.size * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                        .put(normalizedVertices)
                    verticesBuffer?.position(0)

                    normalsBuffer = ByteBuffer.allocateDirect(parsedModel.normals.size * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                        .put(parsedModel.normals)
                    normalsBuffer?.position(0)

                    parsedModel.texCoords?.let { uv ->
                        texCoordsBuffer = ByteBuffer.allocateDirect(uv.size * 4)
                            .order(ByteOrder.nativeOrder())
                            .asFloatBuffer()
                            .put(uv)
                        texCoordsBuffer?.position(0)
                    }

                    parsedModel.colors?.let { colors ->
                        if (hasUsableVertexColors(colors)) {
                            colorsBuffer = ByteBuffer.allocateDirect(colors.size * 4)
                                .order(ByteOrder.nativeOrder())
                                .asFloatBuffer()
                                .put(colors)
                            colorsBuffer?.position(0)
                        } else {
                            colorsBuffer = null
                            Log.w("HeroModel3D", "Model COLOR_0 is near-black; falling back to hero tint color")
                        }
                    }

                    textureImageData = parsedModel.textureImageData
                    materialBaseColorFactor = parsedModel.materialBaseColorFactor

                    indicesType = parsedModel.indicesType
                    indexCount = parsedModel.indices.size
                    if (indicesType == INDEX_TYPE_UNSIGNED_INT) {
                        intIndicesBuffer = ByteBuffer.allocateDirect(parsedModel.indices.size * 4)
                            .order(ByteOrder.nativeOrder())
                            .asIntBuffer()
                            .put(parsedModel.indices)
                        intIndicesBuffer?.position(0)
                        shortIndicesBuffer = null
                    } else {
                        val shortIndices = ShortArray(parsedModel.indices.size) { i ->
                            val raw = parsedModel.indices[i]
                            if (raw in 0..65535) raw.toShort() else 0
                        }
                        shortIndicesBuffer = ByteBuffer.allocateDirect(shortIndices.size * 2)
                            .order(ByteOrder.nativeOrder())
                            .asShortBuffer()
                            .put(shortIndices)
                        shortIndicesBuffer?.position(0)
                        intIndicesBuffer = null
                    }
                    modelLoaded = true
                } else {
                    Log.w("HeroModel3D", "GLB parse failed, using placeholder geometry")
                    createPlaceholderGeometry()
                }
            } else if (path.endsWith(".obj")) {
                parseOBJ(stream)
            } else {
                Log.w("HeroModel3D", "Unsupported model format: $path")
                modelLoaded = false
            }
        } catch (e: Exception) {
            Log.e("HeroModel3D", "Error parsing model: ${e.message}", e)
            modelLoaded = false
        }
    }

    private fun createPlaceholderGeometry() {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        val segments = 32
        val radius = 0.5f
        val height = 1.5f

        vertices.add(0f)
        vertices.add(height / 2)
        vertices.add(0f)
        normals.add(0f)
        normals.add(1f)
        normals.add(0f)

        for (i in 0..segments) {
            val angle = 2.0 * Math.PI * i / segments
            val x = (radius * Math.cos(angle)).toFloat()
            val z = (radius * Math.sin(angle)).toFloat()

            vertices.add(x)
            vertices.add(height / 2)
            vertices.add(z)
            normals.add(0f)
            normals.add(1f)
            normals.add(0f)
        }

        for (i in 0..segments) {
            val angle = 2.0 * Math.PI * i / segments
            val x = (radius * Math.cos(angle)).toFloat()
            val z = (radius * Math.sin(angle)).toFloat()

            vertices.add(x)
            vertices.add(height / 2)
            vertices.add(z)
            val nx = x / radius
            val nz = z / radius
            normals.add(nx)
            normals.add(0f)
            normals.add(nz)

            vertices.add(x)
            vertices.add(-height / 2)
            vertices.add(z)
            normals.add(nx)
            normals.add(0f)
            normals.add(nz)
        }

        vertices.add(0f)
        vertices.add(-height / 2)
        vertices.add(0f)
        normals.add(0f)
        normals.add(-1f)
        normals.add(0f)

        for (i in 0..segments) {
            val angle = 2.0 * Math.PI * i / segments
            val x = (radius * Math.cos(angle)).toFloat()
            val z = (radius * Math.sin(angle)).toFloat()

            vertices.add(x)
            vertices.add(-height / 2)
            vertices.add(z)
            normals.add(0f)
            normals.add(-1f)
            normals.add(0f)
        }

        for (i in 1..segments) {
            indices.add(0)
            indices.add(i.toShort())
            indices.add((i + 1).toShort())
        }

        val topStart = 1
        val bottomStart = (segments + 2).toShort()
        for (i in 0 until segments) {
            val top1 = (topStart + i * 2).toShort()
            val top2 = (topStart + (i + 1) * 2).toShort()
            val bottom1 = (bottomStart + i * 2).toShort()
            val bottom2 = (bottomStart + (i + 1) * 2).toShort()

            indices.add(top1)
            indices.add(bottom1)
            indices.add(top2)
            indices.add(top2)
            indices.add(bottom1)
            indices.add(bottom2)
        }

        val verticesArray = vertices.toFloatArray()
        val normalsArray = normals.toFloatArray()
        val shortIndices = ShortArray(indices.size) { indices[it] }

        verticesBuffer = ByteBuffer.allocateDirect(verticesArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(verticesArray)
        verticesBuffer?.position(0)

        normalsBuffer = ByteBuffer.allocateDirect(normalsArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(normalsArray)
        normalsBuffer?.position(0)

        shortIndicesBuffer = ByteBuffer.allocateDirect(shortIndices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(shortIndices)
        shortIndicesBuffer?.position(0)
        intIndicesBuffer = null

        indicesType = 5123
        indexCount = shortIndices.size
        materialBaseColorFactor = null
        modelLoaded = true
    }

    private fun parseOBJ(stream: InputStream) {
        createPlaceholderGeometry()
    }

    fun updateAnimation(deltaTime: Float) {
        if (modelLoaded) {
            animationTime += deltaTime
        }
    }

    fun draw(
        program: Int,
        mvpMatrix: FloatArray,
        mvpMatrixHandle: Int,
        positionHandle: Int,
        normalHandle: Int,
        colorHandle: Int,
        color: FloatArray
    ) {
        if (!modelLoaded) return
        val vertices = verticesBuffer ?: return
        val normals = normalsBuffer ?: return
        val drawIndices = resolveDrawIndices() ?: return
        if (drawIndices.count <= 0) return

        try {
            val canDrawTextured = texCoordsBuffer != null && textureImageData != null
            val canDrawVertexColored = colorsBuffer != null
            if (canDrawTextured) {
                ensureTexturedResources()
            }
            if (canDrawVertexColored) {
                ensureVertexColorProgram()
            }

            if (canDrawTextured && texturedProgram != 0 && textureId != 0) {
                drawTextured(mvpMatrix, vertices, normals, drawIndices)
            } else if (canDrawVertexColored && vertexColorProgram != 0) {
                drawVertexColored(mvpMatrix, vertices, normals, drawIndices)
            } else {
                val modelColor = materialBaseColorFactor ?: color
                drawColored(
                    program = program,
                    mvpMatrix = mvpMatrix,
                    mvpMatrixHandle = mvpMatrixHandle,
                    positionHandle = positionHandle,
                    normalHandle = normalHandle,
                    colorHandle = colorHandle,
                    color = modelColor,
                    vertices = vertices,
                    normals = normals,
                    indices = drawIndices
                )
            }
        } catch (e: Exception) {
            Log.e("HeroModel3D", "Error drawing model: ${e.message}", e)
        }
    }

    private data class DrawIndices(
        val buffer: Buffer,
        val glType: Int,
        val count: Int
    )

    private fun resolveDrawIndices(): DrawIndices? {
        if (indexCount <= 0) return null

        return if (indicesType == INDEX_TYPE_UNSIGNED_INT) {
            val supportsUint = ensureUintIndexSupport()
            if (supportsUint) {
                val indices = intIndicesBuffer ?: return null
                indices.position(0)
                DrawIndices(indices, GL_UNSIGNED_INT_ENUM, indexCount)
            } else {
                val fallback = getOrCreateFallbackShortIndices() ?: return null
                fallback.position(0)
                DrawIndices(fallback, GLES20.GL_UNSIGNED_SHORT, indexCount)
            }
        } else {
            val indices = shortIndicesBuffer ?: return null
            indices.position(0)
            DrawIndices(indices, GLES20.GL_UNSIGNED_SHORT, indexCount)
        }
    }

    private fun ensureUintIndexSupport(): Boolean {
        supportsUintIndices?.let { return it }
        val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
        val supported = extensions.contains("OES_element_index_uint")
        supportsUintIndices = supported
        Log.d("HeroModel3D", "GL_OES_element_index_uint support: $supported")
        return supported
    }

    private fun getOrCreateFallbackShortIndices(): ShortBuffer? {
        shortIndicesBuffer?.let { return it }
        val source = intIndicesBuffer ?: return null

        source.position(0)
        val shortIndices = ShortArray(indexCount) { i ->
            val value = source.get(i)
            if (value in 0..65535) value.toShort() else 0
        }
        source.position(0)

        shortIndicesBuffer = ByteBuffer.allocateDirect(shortIndices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(shortIndices)
        shortIndicesBuffer?.position(0)

        if (!didLogUintFallback) {
            Log.w("HeroModel3D", "GL_OES_element_index_uint unsupported, using degraded ushort index fallback")
            didLogUintFallback = true
        }
        return shortIndicesBuffer
    }

    private fun hasUsableVertexColors(colors: FloatArray): Boolean {
        if (colors.isEmpty()) return false
        val limit = colors.size - (colors.size % 4)
        for (i in 0 until limit step 4) {
            if (colors[i] > 0.02f || colors[i + 1] > 0.02f || colors[i + 2] > 0.02f) {
                return true
            }
        }
        return false
    }

    private fun normalizeModelVertices(vertices: FloatArray): FloatArray {
        if (vertices.isEmpty()) return vertices

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        for (i in vertices.indices step 3) {
            val x = vertices[i]
            val y = vertices[i + 1]
            val z = vertices[i + 2]

            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
        }

        val sizeY = (maxY - minY).coerceAtLeast(0.0001f)
        val targetHeight = 0.9f
        val scale = targetHeight / sizeY
        val centerX = (minX + maxX) * 0.5f
        val centerZ = (minZ + maxZ) * 0.5f
        val baseY = minY

        val out = vertices.copyOf()
        for (i in out.indices step 3) {
            out[i] = (out[i] - centerX) * scale
            out[i + 1] = (out[i + 1] - baseY) * scale
            out[i + 2] = (out[i + 2] - centerZ) * scale
        }

        Log.d(
            "HeroModel3D",
            "Normalized model bounds x[$minX,$maxX] y[$minY,$maxY] z[$minZ,$maxZ], scale=$scale"
        )
        return out
    }

    private fun ensureTexturedResources() {
        if (texturedProgram == 0) {
            initTexturedProgram()
        }
        if (textureId == 0 && textureImageData != null) {
            textureId = createTextureFromBytes(textureImageData!!)
        }
    }

    private fun ensureVertexColorProgram() {
        if (vertexColorProgram != 0) return
        initVertexColorProgram()
    }

    private fun initTexturedProgram() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, texturedVertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, texturedFragmentShaderCode)
        if (vertexShader == 0 || fragmentShader == 0) return

        val program = GLES20.glCreateProgram()
        if (program == 0) return

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            GLES20.glDeleteProgram(program)
            return
        }

        texturedProgram = program
        texturedPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texturedTexCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        texturedNormalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        texturedMvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        texturedSamplerHandle = GLES20.glGetUniformLocation(program, "uTexture")
        texturedLightDirHandle = GLES20.glGetUniformLocation(program, "uLightDir")
    }

    private fun initVertexColorProgram() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexColorVertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, vertexColorFragmentShaderCode)
        if (vertexShader == 0 || fragmentShader == 0) return

        val program = GLES20.glCreateProgram()
        if (program == 0) return

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            GLES20.glDeleteProgram(program)
            return
        }

        vertexColorProgram = program
        vertexColorPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        vertexColorAttrHandle = GLES20.glGetAttribLocation(program, "aColor")
        vertexColorNormalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        vertexColorMvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        vertexColorLightDirHandle = GLES20.glGetUniformLocation(program, "uLightDir")
    }

    private fun createTextureFromBytes(imageBytes: ByteArray): Int {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return 0

        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val id = textureIds[0]
        if (id == 0) {
            bitmap.recycle()
            return 0
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap.recycle()

        return id
    }

    private fun drawTextured(
        mvpMatrix: FloatArray,
        vertices: FloatBuffer,
        normals: FloatBuffer,
        indices: DrawIndices
    ) {
        val texCoords = texCoordsBuffer ?: return

        GLES20.glUseProgram(texturedProgram)
        GLES20.glUniformMatrix4fv(texturedMvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform3f(texturedLightDirHandle, 0.35f, 0.85f, 0.25f)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(texturedSamplerHandle, 0)

        vertices.position(0)
        normals.position(0)
        texCoords.position(0)
        indices.buffer.position(0)

        GLES20.glEnableVertexAttribArray(texturedPositionHandle)
        GLES20.glEnableVertexAttribArray(texturedTexCoordHandle)
        GLES20.glEnableVertexAttribArray(texturedNormalHandle)
        GLES20.glVertexAttribPointer(texturedPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glVertexAttribPointer(texturedTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glVertexAttribPointer(texturedNormalHandle, 3, GLES20.GL_FLOAT, false, 0, normals)

        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.count, indices.glType, indices.buffer)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        GLES20.glDisableVertexAttribArray(texturedPositionHandle)
        GLES20.glDisableVertexAttribArray(texturedTexCoordHandle)
        GLES20.glDisableVertexAttribArray(texturedNormalHandle)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun drawVertexColored(
        mvpMatrix: FloatArray,
        vertices: FloatBuffer,
        normals: FloatBuffer,
        indices: DrawIndices
    ) {
        val colors = colorsBuffer ?: return

        GLES20.glUseProgram(vertexColorProgram)
        GLES20.glUniformMatrix4fv(vertexColorMvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform3f(vertexColorLightDirHandle, 0.35f, 0.85f, 0.25f)

        vertices.position(0)
        normals.position(0)
        colors.position(0)
        indices.buffer.position(0)

        GLES20.glEnableVertexAttribArray(vertexColorPositionHandle)
        GLES20.glEnableVertexAttribArray(vertexColorAttrHandle)
        GLES20.glEnableVertexAttribArray(vertexColorNormalHandle)
        GLES20.glVertexAttribPointer(vertexColorPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glVertexAttribPointer(vertexColorAttrHandle, 4, GLES20.GL_FLOAT, false, 0, colors)
        GLES20.glVertexAttribPointer(vertexColorNormalHandle, 3, GLES20.GL_FLOAT, false, 0, normals)

        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.count, indices.glType, indices.buffer)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        GLES20.glDisableVertexAttribArray(vertexColorPositionHandle)
        GLES20.glDisableVertexAttribArray(vertexColorAttrHandle)
        GLES20.glDisableVertexAttribArray(vertexColorNormalHandle)
    }

    private fun drawColored(
        program: Int,
        mvpMatrix: FloatArray,
        mvpMatrixHandle: Int,
        positionHandle: Int,
        normalHandle: Int,
        colorHandle: Int,
        color: FloatArray,
        vertices: FloatBuffer,
        normals: FloatBuffer,
        indices: DrawIndices
    ) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform4fv(colorHandle, 1, color, 0)

        vertices.position(0)
        normals.position(0)
        indices.buffer.position(0)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normals)

        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.count, indices.glType, indices.buffer)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun isLoaded(): Boolean = modelLoaded

    private companion object {
        private const val INDEX_TYPE_UNSIGNED_INT = 5125
        private const val GL_UNSIGNED_INT_ENUM = 0x1405
    }
}
