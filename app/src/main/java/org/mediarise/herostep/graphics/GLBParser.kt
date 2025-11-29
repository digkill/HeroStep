package org.mediarise.herostep.graphics

import android.util.Log
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Простой парсер GLB файлов для загрузки геометрии
 */
object GLBParser {
    private const val GLB_MAGIC = 0x46546C67 // "glTF"
    private const val CHUNK_TYPE_JSON = 0x4E4F534A // "JSON"
    private const val CHUNK_TYPE_BIN = 0x004E4942 // "BIN\0"
    
    data class ParsedModel(
        val vertices: FloatArray,
        val normals: FloatArray,
        val indices: IntArray, // Изменено на IntArray для поддержки больших моделей
        val indicesType: Int, // Тип индексов: 5123 (UNSIGNED_SHORT) или 5125 (UNSIGNED_INT)
        val hasAnimation: Boolean = false
    )
    
    /**
     * Парсит GLB файл и извлекает геометрию
     */
    fun parseGLB(stream: InputStream): ParsedModel? {
        try {
            val bytes = stream.readBytes()
            Log.d("GLBParser", "GLB file size: ${bytes.size} bytes")
            
            if (bytes.size < 12) {
                Log.e("GLBParser", "GLB file too small")
                return null
            }
            
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            
            // Читаем заголовок (12 байт)
            val magic = buffer.int
            if (magic != GLB_MAGIC) {
                Log.e("GLBParser", "Invalid GLB magic: $magic (expected: $GLB_MAGIC)")
                return null
            }
            
            val version = buffer.int
            val length = buffer.int
            
            Log.d("GLBParser", "GLB version: $version, length: $length, file size: ${bytes.size}")
            
            // Читаем JSON chunk
            val jsonChunkLength = buffer.int
            val jsonChunkType = buffer.int
            
            if (jsonChunkType != CHUNK_TYPE_JSON) {
                Log.e("GLBParser", "Expected JSON chunk, got: $jsonChunkType")
                return null
            }
            
            val jsonBytes = ByteArray(jsonChunkLength)
            buffer.get(jsonBytes)
            val jsonString = String(jsonBytes)
            
            // Парсим JSON
            val json = JSONObject(jsonString)
            // Получаем nodes из корня JSON
            val nodes = json.getJSONArray("nodes")
            
            // Находим первый mesh
            var meshIndex = -1
            for (i in 0 until nodes.length()) {
                val node = nodes.getJSONObject(i)
                if (node.has("mesh")) {
                    meshIndex = node.getInt("mesh")
                    break
                }
            }
            
            if (meshIndex == -1) {
                Log.e("GLBParser", "No mesh found in GLB")
                return null
            }
            
            val meshes = json.getJSONArray("meshes")
            val mesh = meshes.getJSONObject(meshIndex)
            val primitives = mesh.getJSONArray("primitives")
            val primitive = primitives.getJSONObject(0)
            
            // Получаем доступоры
            val attributes = primitive.getJSONObject("attributes")
            val positionAccessorIndex = attributes.getInt("POSITION")
            val normalAccessorIndex = if (attributes.has("NORMAL")) attributes.getInt("NORMAL") else -1
            val indicesAccessorIndex = if (primitive.has("indices")) primitive.getInt("indices") else -1
            
            val accessors = json.getJSONArray("accessors")
            val bufferViews = json.getJSONArray("bufferViews")
            val buffers = json.getJSONArray("buffers")
            
            // Читаем BIN chunk
            val binChunkLength = buffer.int
            val binChunkType = buffer.int
            
            if (binChunkType != CHUNK_TYPE_BIN) {
                Log.e("GLBParser", "Expected BIN chunk, got: $binChunkType")
                return null
            }
            
            val binData = ByteArray(binChunkLength)
            buffer.get(binData)
            val binBuffer = ByteBuffer.wrap(binData).order(ByteOrder.LITTLE_ENDIAN)
            
            // Извлекаем позиции
            val positionAccessor = accessors.getJSONObject(positionAccessorIndex)
            val positionBufferViewIndex = positionAccessor.getInt("bufferView")
            val positionBufferView = bufferViews.getJSONObject(positionBufferViewIndex)
            val positionByteOffset = if (positionBufferView.has("byteOffset")) positionBufferView.getInt("byteOffset") else 0
            val positionCount = positionAccessor.getInt("count")
            // type в accessor - это строка, а не массив
            val positionType = positionAccessor.getString("type")
            
            binBuffer.position(positionByteOffset)
            val vertices = FloatArray(positionCount * 3)
            for (i in vertices.indices) {
                vertices[i] = binBuffer.float
            }
            
            // Извлекаем нормали
            val normals = if (normalAccessorIndex >= 0) {
                val normalAccessor = accessors.getJSONObject(normalAccessorIndex)
                val normalBufferViewIndex = normalAccessor.getInt("bufferView")
                val normalBufferView = bufferViews.getJSONObject(normalBufferViewIndex)
                val normalByteOffset = if (normalBufferView.has("byteOffset")) normalBufferView.getInt("byteOffset") else 0
                val normalCount = normalAccessor.getInt("count")
                
                Log.d("GLBParser", "Normal accessor: count=$normalCount, byteOffset=$normalByteOffset")
                
                binBuffer.position(normalByteOffset)
                val normalsArray = FloatArray(normalCount * 3)
                for (i in normalsArray.indices) {
                    normalsArray[i] = binBuffer.float
                }
                Log.d("GLBParser", "Loaded $normalCount normals")
                normalsArray
            } else {
                // Генерируем нормали, если их нет
                Log.d("GLBParser", "No normals found, generating")
                generateNormals(vertices)
            }
            
            // Извлекаем индексы
            val (indices, indicesType) = if (indicesAccessorIndex >= 0) {
                val indicesAccessor = accessors.getJSONObject(indicesAccessorIndex)
                val indicesBufferViewIndex = indicesAccessor.getInt("bufferView")
                val indicesBufferView = bufferViews.getJSONObject(indicesBufferViewIndex)
                val indicesByteOffset = if (indicesBufferView.has("byteOffset")) indicesBufferView.getInt("byteOffset") else 0
                val indicesCount = indicesAccessor.getInt("count")
                val indicesComponentType = indicesAccessor.getInt("componentType")
                
                binBuffer.position(indicesByteOffset)
                val indicesArray = IntArray(indicesCount)
                
                // Читаем индексы в зависимости от типа компонента
                // 5123 = UNSIGNED_SHORT, 5125 = UNSIGNED_INT
                when (indicesComponentType) {
                    5123 -> { // UNSIGNED_SHORT -> конвертируем в INT
                        for (i in indicesArray.indices) {
                            val shortValue = binBuffer.short.toInt() and 0xFFFF // Преобразуем unsigned short в int
                            indicesArray[i] = shortValue
                        }
                    }
                    5125 -> { // UNSIGNED_INT
                        for (i in indicesArray.indices) {
                            indicesArray[i] = binBuffer.int
                        }
                    }
                    else -> {
                        Log.w("GLBParser", "Unsupported indices component type: $indicesComponentType, using UNSIGNED_SHORT")
                        binBuffer.position(indicesByteOffset)
                        for (i in indicesArray.indices) {
                            val shortValue = binBuffer.short.toInt() and 0xFFFF
                            indicesArray[i] = shortValue
                        }
                    }
                }
                
                Log.d("GLBParser", "Loaded $indicesCount indices (componentType: $indicesComponentType)")
                Pair(indicesArray, indicesComponentType)
            } else {
                // Генерируем индексы, если их нет
                Log.d("GLBParser", "No indices found, generating sequential indices")
                Pair(IntArray(vertices.size / 3) { it }, 5123) // По умолчанию UNSIGNED_SHORT
            }
            
            // Проверяем наличие анимаций
            val hasAnimation = json.has("animations") && json.getJSONArray("animations").length() > 0
            
            Log.d("GLBParser", "Successfully loaded model: ${vertices.size / 3} vertices, ${normals.size / 3} normals, ${indices.size} indices, indicesType: $indicesType, hasAnimation: $hasAnimation")
            
            if (vertices.isEmpty() || indices.isEmpty()) {
                Log.e("GLBParser", "Model has no geometry data")
                return null
            }
            
            return ParsedModel(vertices, normals, indices, indicesType, hasAnimation)
            
        } catch (e: Exception) {
            Log.e("GLBParser", "Error parsing GLB: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Генерирует нормали для вершин
     */
    private fun generateNormals(vertices: FloatArray): FloatArray {
        val normals = FloatArray(vertices.size)
        
        // Простая генерация нормалей - для каждой вершины используем нормаль (0, 1, 0)
        for (i in vertices.indices step 3) {
            normals[i] = 0f
            normals[i + 1] = 1f
            normals[i + 2] = 0f
        }
        
        return normals
    }
}

