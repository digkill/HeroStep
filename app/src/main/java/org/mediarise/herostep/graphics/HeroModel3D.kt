package org.mediarise.herostep.graphics

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import org.mediarise.herostep.data.model.Hero
import org.mediarise.herostep.data.model.Profession
import org.mediarise.herostep.data.model.Race
import org.mediarise.herostep.models.HeroModelLoader
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Класс для загрузки и отображения 3D модели героя
 */
class HeroModel3D(
    private val context: Context,
    private val hero: Hero
) {
    private var modelLoaded = false
    private var verticesBuffer: FloatBuffer? = null
    private var normalsBuffer: FloatBuffer? = null
    private var indicesBuffer: java.nio.IntBuffer? = null // Изменено на IntBuffer
    private var indicesType: Int = 5123 // 5123 = GL_UNSIGNED_SHORT, 5125 = GL_UNSIGNED_INT
    private var vertexCount = 0
    private var animationTime = 0f
    
    init {
        loadModel()
    }
    
    /**
     * Загружает модель героя из assets
     */
    private fun loadModel() {
        try {
            val loader = HeroModelLoader(context)
            val config = loader.getModelConfigForHero(hero)
            
            Log.d("HeroModel3D", "Attempting to load model for ${hero.race}/${hero.profession}")
            Log.d("HeroModel3D", "Model path: ${config.modelPath}")
            
            // Проверяем наличие модели
            if (!loader.modelExists(config)) {
                Log.d("HeroModel3D", "Model not found for ${hero.race}/${hero.profession}, using cube")
                modelLoaded = false
                return
            }
            
            Log.d("HeroModel3D", "Model exists, loading...")
            
            // Пытаемся загрузить модель
            val modelStream = loader.loadModel(config)
            if (modelStream != null) {
                // Получаем реальный путь к модели
                val actualPath = loader.findModelPath(config) ?: config.modelPath
                Log.d("HeroModel3D", "Actual model path: $actualPath")
                
                // Для GLB файлов нужен специальный парсер
                parseModel(modelStream, actualPath)
                modelStream.close()
            } else {
                Log.w("HeroModel3D", "Model stream is null")
                modelLoaded = false
            }
        } catch (e: Exception) {
            Log.e("HeroModel3D", "Error loading model: ${e.message}", e)
            e.printStackTrace()
            modelLoaded = false
        }
    }
    
    /**
     * Парсит модель (GLB/OBJ)
     */
    private fun parseModel(stream: InputStream, path: String) {
        try {
            Log.d("HeroModel3D", "Parsing model from path: $path")
            
            // Проверяем формат файла
            if (path.endsWith(".glb") || path.endsWith(".gltf")) {
                // Используем парсер GLB
                Log.d("HeroModel3D", "GLB model detected, parsing...")
                
                // Создаем копию потока, так как парсер может его изменить
                val bytes = stream.readBytes()
                val streamCopy = bytes.inputStream()
                val parsedModel = GLBParser.parseGLB(streamCopy)
                
                if (parsedModel != null && parsedModel.vertices.isNotEmpty() && parsedModel.indices.isNotEmpty()) {
                    // Сохраняем геометрию из GLB
                    Log.d("HeroModel3D", "Creating buffers: ${parsedModel.vertices.size / 3} vertices, ${parsedModel.indices.size} indices, indicesType: ${parsedModel.indicesType}")
                    
                    verticesBuffer = ByteBuffer.allocateDirect(parsedModel.vertices.size * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                        .put(parsedModel.vertices)
                    verticesBuffer?.position(0)
                    
                    normalsBuffer = ByteBuffer.allocateDirect(parsedModel.normals.size * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                        .put(parsedModel.normals)
                    normalsBuffer?.position(0)
                    
                    // Используем IntBuffer для индексов
                    indicesBuffer = ByteBuffer.allocateDirect(parsedModel.indices.size * 4)
                        .order(ByteOrder.nativeOrder())
                        .asIntBuffer()
                        .put(parsedModel.indices)
                    indicesBuffer?.position(0)
                    
                    indicesType = parsedModel.indicesType
                    vertexCount = parsedModel.indices.size
                    modelLoaded = true
                    
                    Log.d("HeroModel3D", "GLB model loaded successfully: $vertexCount indices, indicesType: $indicesType, buffers created")
                } else {
                    Log.w("HeroModel3D", "Failed to parse GLB or empty model (vertices: ${parsedModel?.vertices?.size ?: 0}, indices: ${parsedModel?.indices?.size ?: 0}), using placeholder")
                    createPlaceholderGeometry()
                }
            } else if (path.endsWith(".obj")) {
                // Для OBJ можно использовать простой парсер
                parseOBJ(stream)
            } else {
                Log.w("HeroModel3D", "Unsupported model format: $path")
                modelLoaded = false
            }
        } catch (e: Exception) {
            Log.e("HeroModel3D", "Error parsing model: ${e.message}", e)
            e.printStackTrace()
            modelLoaded = false
        }
    }
    
    /**
     * Создает простую геометрию-заглушку для GLB моделей
     * TODO: Заменить на полноценный парсер GLB
     */
    private fun createPlaceholderGeometry() {
        // Создаем простую фигуру вместо кубика (например, цилиндр или более сложную форму)
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        
        // Создаем простой цилиндр (более похож на героя, чем куб)
        // Увеличиваем размер, чтобы модель была более заметной
        val segments = 32 // Увеличено для более гладкой формы
        val radius = 0.5f // Увеличено
        val height = 1.5f // Увеличено
        
        // Верхняя крышка
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
        
        // Боковые грани
        for (i in 0..segments) {
            val angle = 2.0 * Math.PI * i / segments
            val x = (radius * Math.cos(angle)).toFloat()
            val z = (radius * Math.sin(angle)).toFloat()
            
            // Верхняя точка
            vertices.add(x)
            vertices.add(height / 2)
            vertices.add(z)
            val nx = x / radius
            val nz = z / radius
            normals.add(nx)
            normals.add(0f)
            normals.add(nz)
            
            // Нижняя точка
            vertices.add(x)
            vertices.add(-height / 2)
            vertices.add(z)
            normals.add(nx)
            normals.add(0f)
            normals.add(nz)
        }
        
        // Нижняя крышка
        vertices.add(0f)
        vertices.add(-height / 2)
        vertices.add(0f)
        normals.add(0f)
        normals.add(-1f)
        normals.add(0f)
        
        val bottomCenterIndex = (vertices.size / 3 - 1).toShort()
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
        
        // Создаем индексы для треугольников
        // Верхняя крышка
        for (i in 1..segments) {
            indices.add(0)
            indices.add(i.toShort())
            indices.add((i + 1).toShort())
        }
        
        // Боковые грани
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
        
        // Сохраняем в буферы
        val verticesArray = vertices.toFloatArray()
        val normalsArray = normals.toFloatArray()
        val indicesArray = indices.toShortArray()
        
        // Конвертируем ShortArray в IntArray для совместимости
        val indicesIntArray = IntArray(indicesArray.size) { indicesArray[it].toInt() and 0xFFFF }
        
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
        
        indicesBuffer =        ByteBuffer.allocateDirect(indicesIntArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .put(indicesIntArray)
        indicesBuffer?.position(0)
        
        indicesType = 5123 // UNSIGNED_SHORT для заглушки
        vertexCount = indicesIntArray.size
        modelLoaded = true
    }
    
    /**
     * Парсит OBJ файл (упрощенная версия)
     */
    private fun parseOBJ(stream: InputStream) {
        // TODO: Реализовать полноценный парсер OBJ
        // Пока используем заглушку
        createPlaceholderGeometry()
    }
    
    /**
     * Обновляет анимацию (idle)
     */
    fun updateAnimation(deltaTime: Float) {
        if (modelLoaded) {
            animationTime += deltaTime
            // Здесь можно добавить логику анимации idle
            // Например, легкое покачивание или дыхание
        }
    }
    
    /**
     * Рисует модель героя
     */
    fun draw(
        program: Int,
        mvpMatrix: FloatArray,
        mvpMatrixHandle: Int,
        positionHandle: Int,
        normalHandle: Int,
        colorHandle: Int,
        color: FloatArray
    ) {
        if (!modelLoaded) {
            Log.w("HeroModel3D", "Model not loaded for ${hero.race}/${hero.profession}")
            return
        }
        
        if (verticesBuffer == null || normalsBuffer == null || indicesBuffer == null) {
            Log.w("HeroModel3D", "Model buffers are null")
            return
        }
        
        if (vertexCount == 0) {
            Log.w("HeroModel3D", "Model has 0 vertices")
            return
        }
        
        try {
            GLES20.glUseProgram(program)
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniform4fv(colorHandle, 1, color, 0)
            
            verticesBuffer?.position(0)
            normalsBuffer?.position(0)
            indicesBuffer?.position(0)
            
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glEnableVertexAttribArray(normalHandle)
            
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, verticesBuffer)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalsBuffer)
            
            // Временно отключаем cull face для моделей, чтобы видеть все грани
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            
            // OpenGL ES 2.0 не поддерживает GL_UNSIGNED_INT в glDrawElements
            // Нужно конвертировать IntBuffer в ShortBuffer для ES 2.0
            if (indicesType == 5125 || vertexCount > 65535) {
                // UNSIGNED_INT или слишком много индексов - конвертируем в ShortArray
                // Но это может привести к потере данных, если индексы > 65535
                indicesBuffer?.position(0)
                val shortIndices = ShortArray(vertexCount) { 
                    val intValue = indicesBuffer?.get() ?: 0
                    if (intValue > 65535) {
                        Log.w("HeroModel3D", "Index $intValue exceeds Short.MAX_VALUE, clamping")
                        65535.toShort()
                    } else {
                        intValue.toShort()
                    }
                }
                indicesBuffer?.position(0)
                
                val shortBuffer = ByteBuffer.allocateDirect(shortIndices.size * 2)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
                    .put(shortIndices)
                shortBuffer.position(0)
                
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, vertexCount, GLES20.GL_UNSIGNED_SHORT, shortBuffer)
            } else {
                // UNSIGNED_SHORT - конвертируем IntBuffer в ShortBuffer
                indicesBuffer?.position(0)
                val shortIndices = ShortArray(vertexCount) { 
                    (indicesBuffer?.get() ?: 0).toShort()
                }
                indicesBuffer?.position(0)
                
                val shortBuffer = ByteBuffer.allocateDirect(shortIndices.size * 2)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
                    .put(shortIndices)
                shortBuffer.position(0)
                
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, vertexCount, GLES20.GL_UNSIGNED_SHORT, shortBuffer)
            }
            
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(normalHandle)
        } catch (e: Exception) {
            Log.e("HeroModel3D", "Error drawing model: ${e.message}", e)
        }
    }
    
    fun isLoaded(): Boolean = modelLoaded
}

