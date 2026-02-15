package org.mediarise.herostep.graphics

import android.util.Log
import android.util.Base64
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        val hasAnimation: Boolean = false,
        val texCoords: FloatArray? = null,
        val colors: FloatArray? = null,
        val materialBaseColorFactor: FloatArray? = null,
        val textureImageData: ByteArray? = null,
        val textureMimeType: String? = null
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
            val accessors = json.optJSONArray("accessors") ?: return null
            val bufferViews = json.optJSONArray("bufferViews") ?: return null
            val meshes = json.optJSONArray("meshes")
            if (meshes == null || meshes.length() == 0) {
                Log.e("GLBParser", "No meshes found in GLB")
                return null
            }
            
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

            val verticesAll = mutableListOf<Float>()
            val normalsAll = mutableListOf<Float>()
            val texCoordsAll = mutableListOf<Float>()
            val colorsAll = mutableListOf<Float>()
            val indicesAll = mutableListOf<Int>()
            var hasAnyTexCoords = false
            var hasAnyColors = false
            var processedPrimitiveCount = 0

            var textureImageData: ByteArray? = null
            var textureMimeType: String? = null
            var materialBaseColorFactor: FloatArray? = null

            for (meshIndex in 0 until meshes.length()) {
                val mesh = meshes.getJSONObject(meshIndex)
                val primitives = mesh.optJSONArray("primitives") ?: continue

                for (primitiveIndex in 0 until primitives.length()) {
                    val primitive = primitives.getJSONObject(primitiveIndex)
                    val attributes = primitive.optJSONObject("attributes") ?: continue
                    val positionAccessorIndex = attributes.optInt("POSITION", -1)
                    if (positionAccessorIndex < 0) continue

                    val positions = readFloatAccessor(
                        accessorIndex = positionAccessorIndex,
                        expectedType = "VEC3",
                        accessors = accessors,
                        bufferViews = bufferViews,
                        binBuffer = binBuffer
                    ) ?: continue
                    val vertexCount = positions.size / 3
                    if (vertexCount == 0) continue

                    val normals = attributes.optInt("NORMAL", -1).let { normalAccessorIndex ->
                        if (normalAccessorIndex >= 0) {
                            readFloatAccessor(
                                accessorIndex = normalAccessorIndex,
                                expectedType = "VEC3",
                                accessors = accessors,
                                bufferViews = bufferViews,
                                binBuffer = binBuffer
                            )?.takeIf { it.size == vertexCount * 3 } ?: generateNormals(positions)
                        } else {
                            generateNormals(positions)
                        }
                    }

                    val texCoords = attributes.optInt("TEXCOORD_0", -1).let { texCoordAccessorIndex ->
                        if (texCoordAccessorIndex >= 0) {
                            readFloatAccessor(
                                accessorIndex = texCoordAccessorIndex,
                                expectedType = "VEC2",
                                accessors = accessors,
                                bufferViews = bufferViews,
                                binBuffer = binBuffer
                            )?.takeIf { it.size == vertexCount * 2 }
                        } else {
                            null
                        }
                    }

                    val colors = attributes.optInt("COLOR_0", -1).let { colorAccessorIndex ->
                        if (colorAccessorIndex >= 0) {
                            readColorAccessor(
                                accessorIndex = colorAccessorIndex,
                                accessors = accessors,
                                bufferViews = bufferViews,
                                binBuffer = binBuffer
                            )?.takeIf { it.size == vertexCount * 4 }
                        } else {
                            null
                        }
                    }

                    val indices = if (primitive.has("indices")) {
                        readIndicesAccessor(
                            accessorIndex = primitive.getInt("indices"),
                            accessors = accessors,
                            bufferViews = bufferViews,
                            binBuffer = binBuffer
                        ) ?: IntArray(vertexCount) { it }
                    } else {
                        IntArray(vertexCount) { it }
                    }

                    val baseVertex = verticesAll.size / 3

                    // Если впервые встретили UV/Color не в первом примитиве, заполняем предыдущие вершины нейтральными значениями.
                    if (texCoords != null && !hasAnyTexCoords) {
                        repeat(baseVertex * 2) { texCoordsAll.add(0f) }
                        hasAnyTexCoords = true
                    }
                    if (colors != null && !hasAnyColors) {
                        repeat(baseVertex) {
                            colorsAll.add(1f)
                            colorsAll.add(1f)
                            colorsAll.add(1f)
                            colorsAll.add(1f)
                        }
                        hasAnyColors = true
                    }

                    verticesAll.addAll(positions.asList())
                    normalsAll.addAll(normals.asList())

                    if (hasAnyTexCoords) {
                        if (texCoords != null) {
                            texCoordsAll.addAll(texCoords.asList())
                        } else {
                            repeat(vertexCount * 2) { texCoordsAll.add(0f) }
                        }
                    }

                    if (hasAnyColors) {
                        if (colors != null) {
                            colorsAll.addAll(colors.asList())
                        } else {
                            repeat(vertexCount) {
                                colorsAll.add(1f)
                                colorsAll.add(1f)
                                colorsAll.add(1f)
                                colorsAll.add(1f)
                            }
                        }
                    }

                    indices.forEach { indicesAll.add(it + baseVertex) }
                    processedPrimitiveCount++

                    if (textureImageData == null) {
                        val textureExtraction = extractTextureImageData(json, primitive, binData)
                        textureImageData = textureExtraction?.first
                        textureMimeType = textureExtraction?.second
                    }
                    if (materialBaseColorFactor == null) {
                        materialBaseColorFactor = extractMaterialBaseColorFactor(json, primitive)
                    }
                }
            }

            if (processedPrimitiveCount == 0 || verticesAll.isEmpty() || indicesAll.isEmpty()) {
                Log.e("GLBParser", "Model has no valid primitives")
                return null
            }
            
            // Проверяем наличие анимаций
            val hasAnimation = json.has("animations") && json.getJSONArray("animations").length() > 0
            
            if (hasAnimation) {
                val animations = json.getJSONArray("animations")
                Log.d("GLBParser", "Found ${animations.length()} animation(s) in GLB file:")
                for (i in 0 until animations.length()) {
                    val anim = animations.getJSONObject(i)
                    val animName = anim.optString("name", "Unnamed_$i")
                    Log.d("GLBParser", "  Animation #$i: $animName")
                }
            } else {
                Log.d("GLBParser", "No animations found in GLB file")
            }
            val vertices = verticesAll.toFloatArray()
            val normals = normalsAll.toFloatArray()
            val indices = indicesAll.toIntArray()
            val texCoords = if (hasAnyTexCoords) texCoordsAll.toFloatArray() else null
            val colors = if (hasAnyColors) colorsAll.toFloatArray() else null
            val maxIndex = indices.maxOrNull() ?: 0
            val indicesType = if (maxIndex > 65535) 5125 else 5123
            
            Log.d(
                "GLBParser",
                "Successfully loaded model: ${vertices.size / 3} vertices, ${indices.size} indices, " +
                    "primitives=$processedPrimitiveCount, indicesType=$indicesType, hasAnimation=$hasAnimation"
            )
            
            if (vertices.isEmpty() || indices.isEmpty()) {
                Log.e("GLBParser", "Model has no geometry data")
                return null
            }
            
            return ParsedModel(
                vertices = vertices,
                normals = normals,
                indices = indices,
                indicesType = indicesType,
                hasAnimation = hasAnimation,
                texCoords = texCoords,
                colors = colors,
                materialBaseColorFactor = materialBaseColorFactor,
                textureImageData = textureImageData,
                textureMimeType = textureMimeType
            )
            
        } catch (e: Exception) {
            Log.e("GLBParser", "Error parsing GLB: ${e.message}", e)
            return null
        }
    }

    private fun readFloatAccessor(
        accessorIndex: Int,
        expectedType: String,
        accessors: org.json.JSONArray,
        bufferViews: org.json.JSONArray,
        binBuffer: ByteBuffer
    ): FloatArray? {
        if (accessorIndex !in 0 until accessors.length()) return null
        val accessor = accessors.getJSONObject(accessorIndex)
        val type = accessor.optString("type")
        if (type != expectedType) return null
        if (accessor.optInt("componentType") != 5126) return null

        val components = when (type) {
            "SCALAR" -> 1
            "VEC2" -> 2
            "VEC3" -> 3
            "VEC4" -> 4
            else -> return null
        }

        val bufferViewIndex = accessor.optInt("bufferView", -1)
        if (bufferViewIndex !in 0 until bufferViews.length()) return null
        val bufferView = bufferViews.getJSONObject(bufferViewIndex)

        val count = accessor.optInt("count", 0)
        if (count <= 0) return null

        val viewOffset = bufferView.optInt("byteOffset", 0)
        val accessorOffset = accessor.optInt("byteOffset", 0)
        val baseOffset = viewOffset + accessorOffset
        val stride = bufferView.optInt("byteStride", components * 4).let { if (it <= 0) components * 4 else it }

        val out = FloatArray(count * components)
        for (i in 0 until count) {
            val elementOffset = baseOffset + i * stride
            for (c in 0 until components) {
                out[i * components + c] = binBuffer.getFloat(elementOffset + c * 4)
            }
        }
        return out
    }

    private fun readColorAccessor(
        accessorIndex: Int,
        accessors: org.json.JSONArray,
        bufferViews: org.json.JSONArray,
        binBuffer: ByteBuffer
    ): FloatArray? {
        if (accessorIndex !in 0 until accessors.length()) return null
        val accessor = accessors.getJSONObject(accessorIndex)
        val type = accessor.optString("type")
        val components = when (type) {
            "VEC3" -> 3
            "VEC4" -> 4
            else -> return null
        }
        val componentType = accessor.optInt("componentType", -1)
        val normalized = accessor.optBoolean("normalized", false)

        val bufferViewIndex = accessor.optInt("bufferView", -1)
        if (bufferViewIndex !in 0 until bufferViews.length()) return null
        val bufferView = bufferViews.getJSONObject(bufferViewIndex)

        val count = accessor.optInt("count", 0)
        if (count <= 0) return null

        val viewOffset = bufferView.optInt("byteOffset", 0)
        val accessorOffset = accessor.optInt("byteOffset", 0)
        val componentSize = when (componentType) {
            5121 -> 1
            5123 -> 2
            5126 -> 4
            else -> return null
        }
        val stride = bufferView.optInt("byteStride", components * componentSize)
            .let { if (it <= 0) components * componentSize else it }
        val baseOffset = viewOffset + accessorOffset

        val out = FloatArray(count * 4)
        for (i in 0 until count) {
            val elementOffset = baseOffset + i * stride
            for (c in 0 until components) {
                val value = when (componentType) {
                    5121 -> {
                        val raw = binBuffer.get(elementOffset + c).toInt() and 0xFF
                        if (normalized) raw / 255f else raw.toFloat()
                    }
                    5123 -> {
                        val raw = binBuffer.getShort(elementOffset + c * 2).toInt() and 0xFFFF
                        if (normalized) raw / 65535f else raw.toFloat()
                    }
                    5126 -> binBuffer.getFloat(elementOffset + c * 4)
                    else -> 1f
                }
                out[i * 4 + c] = value
            }
            out[i * 4 + 3] = if (components == 4) out[i * 4 + 3] else 1f
        }
        return out
    }

    private fun readIndicesAccessor(
        accessorIndex: Int,
        accessors: org.json.JSONArray,
        bufferViews: org.json.JSONArray,
        binBuffer: ByteBuffer
    ): IntArray? {
        if (accessorIndex !in 0 until accessors.length()) return null
        val accessor = accessors.getJSONObject(accessorIndex)
        if (accessor.optString("type", "SCALAR") != "SCALAR") return null

        val componentType = accessor.optInt("componentType", -1)
        val componentSize = when (componentType) {
            5121 -> 1
            5123 -> 2
            5125 -> 4
            else -> return null
        }

        val bufferViewIndex = accessor.optInt("bufferView", -1)
        if (bufferViewIndex !in 0 until bufferViews.length()) return null
        val bufferView = bufferViews.getJSONObject(bufferViewIndex)

        val count = accessor.optInt("count", 0)
        if (count <= 0) return null

        val viewOffset = bufferView.optInt("byteOffset", 0)
        val accessorOffset = accessor.optInt("byteOffset", 0)
        val baseOffset = viewOffset + accessorOffset
        val stride = bufferView.optInt("byteStride", componentSize)
            .let { if (it <= 0) componentSize else it }

        val out = IntArray(count)
        for (i in 0 until count) {
            val elementOffset = baseOffset + i * stride
            out[i] = when (componentType) {
                5121 -> binBuffer.get(elementOffset).toInt() and 0xFF
                5123 -> binBuffer.getShort(elementOffset).toInt() and 0xFFFF
                5125 -> binBuffer.getInt(elementOffset)
                else -> 0
            }
        }
        return out
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

    private fun extractTextureImageData(
        json: JSONObject,
        primitive: JSONObject,
        binData: ByteArray
    ): Pair<ByteArray, String>? {
        val images = json.optJSONArray("images") ?: return null
        if (images.length() == 0) return null

        val textures = json.optJSONArray("textures")
        val materials = json.optJSONArray("materials")

        var sourceImageIndex = -1

        if (primitive.has("material") && materials != null) {
            val materialIndex = primitive.getInt("material")
            if (materialIndex in 0 until materials.length()) {
                val material = materials.getJSONObject(materialIndex)
                val pbr = material.optJSONObject("pbrMetallicRoughness")
                val baseColorTexture = pbr?.optJSONObject("baseColorTexture")
                val textureIndex = baseColorTexture?.optInt("index", -1) ?: -1
                if (textureIndex >= 0 && textures != null && textureIndex < textures.length()) {
                    sourceImageIndex = textures.getJSONObject(textureIndex).optInt("source", -1)
                }
            }
        }

        if (sourceImageIndex < 0 && textures != null && textures.length() > 0) {
            sourceImageIndex = textures.getJSONObject(0).optInt("source", -1)
        }
        if (sourceImageIndex < 0 && images.length() > 0) {
            sourceImageIndex = 0
        }
        if (sourceImageIndex !in 0 until images.length()) return null

        val image = images.getJSONObject(sourceImageIndex)
        val mimeType = image.optString("mimeType", "image/png")

        if (image.has("bufferView")) {
            val bufferViews = json.optJSONArray("bufferViews") ?: return null
            val bufferViewIndex = image.getInt("bufferView")
            if (bufferViewIndex !in 0 until bufferViews.length()) return null

            val bufferView = bufferViews.getJSONObject(bufferViewIndex)
            val byteOffset = bufferView.optInt("byteOffset", 0)
            val byteLength = bufferView.optInt("byteLength", 0)
            if (byteLength <= 0 || byteOffset < 0 || byteOffset + byteLength > binData.size) return null

            val bytes = binData.copyOfRange(byteOffset, byteOffset + byteLength)
            return Pair(bytes, mimeType)
        }

        if (image.has("uri")) {
            val uri = image.getString("uri")
            if (uri.startsWith("data:")) {
                val commaIndex = uri.indexOf(',')
                if (commaIndex <= 0) return null

                val metadata = uri.substring(5, commaIndex)
                val payload = uri.substring(commaIndex + 1)
                val uriMime = metadata.substringBefore(';').ifBlank { mimeType }
                val bytes = if (metadata.contains(";base64")) {
                    Base64.decode(payload, Base64.DEFAULT)
                } else {
                    payload.toByteArray(Charsets.UTF_8)
                }
                return Pair(bytes, uriMime)
            }
        }

        return null
    }

    private fun extractMaterialBaseColorFactor(
        json: JSONObject,
        primitive: JSONObject
    ): FloatArray? {
        val materials = json.optJSONArray("materials") ?: return null
        if (!primitive.has("material")) return null

        val materialIndex = primitive.optInt("material", -1)
        if (materialIndex !in 0 until materials.length()) return null

        val material = materials.optJSONObject(materialIndex) ?: return null
        val pbr = material.optJSONObject("pbrMetallicRoughness") ?: return null
        val baseColorFactor = pbr.optJSONArray("baseColorFactor") ?: return null
        if (baseColorFactor.length() < 3) return null

        val r = baseColorFactor.optDouble(0, 1.0).toFloat().coerceIn(0f, 1f)
        val g = baseColorFactor.optDouble(1, 1.0).toFloat().coerceIn(0f, 1f)
        val b = baseColorFactor.optDouble(2, 1.0).toFloat().coerceIn(0f, 1f)
        val a = baseColorFactor.optDouble(3, 1.0).toFloat().coerceIn(0f, 1f)
        return floatArrayOf(r, g, b, a)
    }
}

